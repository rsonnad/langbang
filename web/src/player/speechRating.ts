import { Store } from "../lib/store";
import { nowVoicing } from "./nowVoicing";

/**
 * Speech rating — the web port of the Android {@link SpeechRating} feature. The sticky mic
 * toggle under the Now Voicing transport buttons. While armed, the study queue listens for
 * the user to repeat each phrase after it's voiced, scores the attempt 0–100, surfaces it,
 * then auto-advances. The latch stays on until toggled off.
 *
 * GAP vs. Android (flagged for review): Android scores with Azure's PronunciationAssessment
 * (phoneme-level accuracy/fluency/completeness). The web app has no Azure pronunciation
 * endpoint, so this mirrors the *semantics* (a single 0–100 "how close were you" number)
 * using the browser's Web Speech API to transcribe the attempt and comparing that transcript
 * to the reference text (normalized edit-distance similarity). It's a coarser proxy than
 * Azure's phoneme scoring. If true phoneme parity is needed on web, add an Azure Speech
 * token endpoint (Worker) + the microsoft-cognitiveservices-speech-sdk browser SDK.
 */

export type SpeechRatingPhase = "idle" | "listening" | "scored";

export interface SpeechRatingResult {
  /** 0–100, "how close they were". */
  score: number;
  transcribed: string;
  reference: string;
  error?: string;
}

// Chrome/Edge expose webkitSpeechRecognition; Firefox/Safari generally don't.
const SpeechRecognitionImpl: typeof SpeechRecognition | undefined =
  typeof window !== "undefined"
    ? (window.SpeechRecognition ?? window.webkitSpeechRecognition)
    : undefined;

export const speechRatingSupported = !!SpeechRecognitionImpl;

export const speechRatingArmed = new Store<boolean>(false);
export const speechRatingPhase = new Store<SpeechRatingPhase>("idle");
export const speechRatingPartial = new Store<string>("");
export const speechRatingResult = new Store<SpeechRatingResult | null>(null);

/** How long to hold the score on-screen before the queue advances. */
const RESULT_HOLD_MS = 1800;
/** Safety net so a wedged recognizer can't hang the queue forever. */
const LISTEN_TIMEOUT_MS = 12_000;

let targetLocale = "pl-PL";

/** Set by the app shell from the active voicing profile's target locale (e.g. "pl-PL"). */
export function setSpeechRatingLocale(locale: string): void {
  if (locale) targetLocale = locale;
}

export function setSpeechRatingArmed(value: boolean): void {
  speechRatingArmed.set(value);
  if (!value) reset();
}

export function toggleSpeechRating(): void {
  setSpeechRatingArmed(!speechRatingArmed.get());
}

function reset(): void {
  speechRatingPhase.set("idle");
  speechRatingPartial.set("");
  speechRatingResult.set(null);
}

/**
 * If armed, run one listen → score → hold cycle against the phrase currently in the Now
 * Voicing panel, then resolve so the queue can auto-advance. No-op when disarmed or when
 * there's no scorable target text.
 */
export async function runSpeechRatingCycle(): Promise<void> {
  if (!speechRatingArmed.get()) return;
  const reference = nowVoicing.get()?.pl?.trim();
  if (!reference) return;

  if (!SpeechRecognitionImpl) {
    speechRatingResult.set({
      score: 0,
      transcribed: "",
      reference,
      error: "This browser has no speech recognition — try Chrome or Edge.",
    });
    speechRatingPhase.set("scored");
    await delay(RESULT_HOLD_MS);
    return;
  }

  speechRatingPhase.set("listening");
  speechRatingPartial.set("");
  try {
    const transcript = await recognizeOnce(targetLocale, (p) => speechRatingPartial.set(p));
    // Drop the result if the user disarmed while we were listening.
    if (!speechRatingArmed.get()) return;
    speechRatingResult.set({
      score: similarityScore(reference, transcript),
      transcribed: transcript,
      reference,
    });
  } catch (err) {
    if (!speechRatingArmed.get()) return;
    speechRatingResult.set({
      score: 0,
      transcribed: "",
      reference,
      error: err instanceof Error ? err.message : "Couldn't score that one — try again.",
    });
  }
  speechRatingPhase.set("scored");
  speechRatingPartial.set("");

  // Hold the score on-screen briefly, bailing early if the user disarms mid-hold.
  let remaining = RESULT_HOLD_MS;
  while (remaining > 0 && speechRatingArmed.get()) {
    const step = Math.min(120, remaining);
    await delay(step);
    remaining -= step;
  }
}

/** Capture one utterance from the mic and resolve with the best final transcript. */
function recognizeOnce(locale: string, onPartial: (text: string) => void): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const Impl = SpeechRecognitionImpl;
    if (!Impl) {
      reject(new Error("Speech recognition not supported"));
      return;
    }
    const rec = new Impl();
    rec.lang = locale;
    rec.interimResults = true;
    rec.maxAlternatives = 1;
    rec.continuous = false;

    let finalTranscript = "";
    let settled = false;

    const timer = setTimeout(() => {
      try {
        rec.stop();
      } catch {
        /* already stopped */
      }
    }, LISTEN_TIMEOUT_MS);

    const finish = (fn: () => void) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      fn();
    };

    rec.onresult = (e: SpeechRecognitionEvent) => {
      let interim = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) finalTranscript += r[0].transcript;
        else interim += r[0].transcript;
      }
      if (interim) onPartial(interim.trim());
    };
    rec.onerror = (e: SpeechRecognitionErrorEvent) => {
      finish(() => {
        if (e.error === "no-speech") resolve("");
        else reject(new Error(speechErrorMessage(e.error)));
      });
    };
    rec.onend = () => finish(() => resolve(finalTranscript.trim()));

    try {
      rec.start();
    } catch (err) {
      finish(() => reject(err instanceof Error ? err : new Error(String(err))));
    }
  });
}

function speechErrorMessage(code: string): string {
  switch (code) {
    case "not-allowed":
    case "service-not-allowed":
      return "Microphone blocked — allow mic access for this site, then try again.";
    case "audio-capture":
      return "No microphone found.";
    case "network":
      return "Speech recognition needs the network.";
    default:
      return `Speech recognition error (${code}).`;
  }
}

/** Normalized edit-distance similarity, 0–100. Diacritics kept (recognizer returns them). */
function similarityScore(reference: string, heard: string): number {
  const a = normalize(reference);
  const b = normalize(heard);
  if (!a || !b) return 0;
  const dist = levenshtein(a, b);
  const maxLen = Math.max(a.length, b.length);
  const ratio = maxLen === 0 ? 0 : 1 - dist / maxLen;
  return Math.max(0, Math.min(100, Math.round(ratio * 100)));
}

function normalize(s: string): string {
  return s
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function levenshtein(a: string, b: string): number {
  const m = a.length;
  const n = b.length;
  if (m === 0) return n;
  if (n === 0) return m;
  let prev = new Array<number>(n + 1);
  let curr = new Array<number>(n + 1);
  for (let j = 0; j <= n; j++) prev[j] = j;
  for (let i = 1; i <= m; i++) {
    curr[0] = i;
    for (let j = 1; j <= n; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
    }
    [prev, curr] = [curr, prev];
  }
  return prev[n];
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
