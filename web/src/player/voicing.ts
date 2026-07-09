import { AudioPlayer } from "../audio/audioPlayer";
import { directAudioUrl, synthAudioUrl } from "../audio/audioManifest";
import type { CloudAudioConfig, SentenceExample, TokenPair, VoicingProfile } from "../cloud/models";
import { nowVoicing } from "./nowVoicing";
import { settingsStore } from "./settings";
import { studyQueue, type StudyQueue } from "./studyQueue";

export interface VoiceItem {
  /** Source cue text (the `en` content field, in either direction). */
  en: string;
  /** Target answer text (the `pl` content field, in either direction). */
  pl: string;
  literal?: string | null;
  words?: TokenPair[] | null;
  position?: string | null;
}

export function itemFromSentence(s: SentenceExample, position?: string): VoiceItem {
  return {
    en: s.en,
    pl: s.pl,
    literal: s.literal ?? null,
    words: s.words ?? null,
    position: position ?? null,
  };
}

/** Voice one item: source cue → reveal → (slow target) → target. Publishes NowVoicing per segment. */
export async function voiceItem(
  q: StudyQueue,
  item: VoiceItem,
  vp: VoicingProfile,
  opts: { speakSource?: boolean } = {},
): Promise<void> {
  const s = settingsStore.get();
  // Word forms only have target audio in R2 (matching AudioManifest.kt); callers pass
  // speakSource:false so the cue still shows in the panel but isn't synthesized/spoken.
  const speakSource = opts.speakSource ?? s.sourceCue;
  const base = {
    en: item.en,
    pl: item.pl,
    literal: item.literal,
    words: item.words,
    position: item.position,
  };

  nowVoicing.set({ ...base, lang: "en" });
  if (speakSource && item.en) await q.say(item.en, vp.sourceLocale, vp.sourceVoice);
  await q.reveal(s.revealMs);

  if (s.slowTarget && vp.slowTargetVoice && item.pl) {
    nowVoicing.set({ ...base, lang: "pl-slow" });
    await q.say(item.pl, vp.targetLocale, vp.slowTargetVoice);
    await q.reveal(Math.round(s.revealMs * 0.35));
  }

  nowVoicing.set({ ...base, lang: "pl" });
  if (item.pl) await q.say(item.pl, vp.targetLocale, vp.targetVoice);
  await q.reveal(Math.round(s.revealMs * 0.6));
}

// One-off tap player: a separate element so it never overlaps the queue. If a queue is
// actively playing, it parks (pauses) it, plays the single clip, then resumes — satisfying
// "a one-off tap must not destroy an active queue."
const oneOff = new AudioPlayer();

export async function playOneOff(
  cfg: CloudAudioConfig,
  vp: VoicingProfile,
  item: VoiceItem,
  which: "target" | "source" = "target",
): Promise<void> {
  const wasPlaying = studyQueue.hasQueue && !studyQueue.state.get().isPaused;
  if (wasPlaying) studyQueue.pause();
  const text = which === "source" ? item.en : item.pl;
  const voice = which === "source" ? vp.sourceVoice : vp.targetVoice;
  const locale = which === "source" ? vp.sourceLocale : vp.targetLocale;
  nowVoicing.set({
    en: item.en,
    pl: item.pl,
    literal: item.literal,
    words: item.words,
    position: item.position,
    lang: which === "source" ? "en" : "pl",
  });
  if (text) {
    const url = await directAudioUrl(cfg, { text, locale, voice });
    let ok = await oneOff.play(url);
    if (!ok) {
      const synth = await synthAudioUrl({ text, locale, voice });
      if (synth) ok = await oneOff.play(synth);
    }
  }
  if (wasPlaying) studyQueue.resume();
}
