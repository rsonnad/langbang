import { directAudioUrl } from "../audio/audioManifest";
import {
  findLesson,
  type AdjectivesPayload,
  type AdverbsPayload,
  type AudioPhraseReq,
  type CloudBootstrap,
  type NounsPayload,
  type PhrasesPayload,
  type PronunciationPayload,
  type VerbsPayload,
  type VoicingProfile,
} from "../cloud/models";
import { PERSON_KEYS, targetSubject } from "./lessonHelpers";

// Mirror of each *Page.tsx queue-item builder so "Download for offline" caches exactly
// the clips the lessons can voice (e.g. verbs voice "ja jestem", not "jestem").
// Keep in sync with the lesson pages.
const NOUN_CASES = ["nom", "acc", "gen"] as const;
const NOUN_NUMS = ["sg", "pl"] as const;
const ADJ_CASES = ["nom", "acc"] as const;
const ADJ_GENDERS = ["m", "f", "n", "mp", "other"] as const;

type Spoken = { text: string; role: "source" | "target" };

function collectSpoken(b: CloudBootstrap, vp: VoicingProfile): Spoken[] {
  const out: Spoken[] = [];
  const target = (text: string) => {
    if (text) out.push({ text, role: "target" });
  };
  const source = (text: string) => {
    if (text) out.push({ text, role: "source" });
  };

  // Phrases — both the source cue and the target answer are voiced.
  const phrases = findLesson<PhrasesPayload>(b, "phrases");
  for (const g of phrases?.groups ?? []) {
    for (const s of g.sentences) {
      source(s.en);
      target(s.pl);
    }
  }

  // Verbs — present + past forms, target only (subject pronoun + form).
  const verbs = findLesson<VerbsPayload>(b, "verbs");
  const addForms = (forms: Record<string, string> | null | undefined) => {
    if (!forms) return;
    for (const k of PERSON_KEYS) {
      if (forms[k]) target(`${targetSubject(vp, k)} ${forms[k]}`.trim());
    }
  };
  for (const v of verbs?.verbs ?? []) {
    addForms(v.forms);
    addForms(v.past_forms);
  }

  // Nouns — case × number, target only.
  const nouns = findLesson<NounsPayload>(b, "nouns");
  for (const n of nouns?.nouns ?? []) {
    for (const c of NOUN_CASES) for (const num of NOUN_NUMS) target(n[c]?.[num] ?? "");
  }

  // Adjectives — case × gender, target only.
  const adjectives = findLesson<AdjectivesPayload>(b, "adjectives");
  for (const a of adjectives?.adjectives ?? []) {
    for (const c of ADJ_CASES) for (const g of ADJ_GENDERS) target(a[c]?.[g] ?? "");
  }

  // Adverbs — lemma, target only.
  const adverbs = findLesson<AdverbsPayload>(b, "adverbs");
  for (const a of adverbs?.adverbs ?? []) target(a.lemma);

  // Pronunciation — example words, target only.
  const pron = findLesson<PronunciationPayload>(b, "pronunciation");
  for (const ph of pron?.phonemes ?? []) for (const e of ph.examples) target(e.pl);

  return out;
}

/** Deduped audio requests for every clip the lessons can voice (normal speed). */
export function collectAudioRequests(b: CloudBootstrap, vp: VoicingProfile): AudioPhraseReq[] {
  const seen = new Set<string>();
  const reqs: AudioPhraseReq[] = [];
  for (const s of collectSpoken(b, vp)) {
    const locale = s.role === "source" ? vp.sourceLocale : vp.targetLocale;
    const voice = s.role === "source" ? vp.sourceVoice : vp.targetVoice;
    const key = `${locale}|${voice}|${s.text}`;
    if (!seen.has(key)) {
      seen.add(key);
      reqs.push({ text: s.text, locale, voice });
    }
  }
  return reqs;
}

/** Resolve direct R2 URLs for every offline clip (pre-synthesized; missing ones 404 at fetch). */
export function resolveOfflineUrls(b: CloudBootstrap, vp: VoicingProfile): Promise<string[]> {
  return Promise.all(collectAudioRequests(b, vp).map((r) => directAudioUrl(b.audio, r)));
}
