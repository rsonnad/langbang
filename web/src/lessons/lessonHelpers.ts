import type { VoicingProfile } from "../cloud/models";
import { nowVoicing } from "../player/nowVoicing";
import { studyQueue } from "../player/studyQueue";
import { voiceItem, type VoiceItem } from "../player/voicing";

export const PERSON_KEYS = ["1sg", "2sg", "3sg", "1pl", "2pl", "3pl"] as const;

const SUBJECT_PL: Record<string, string> = {
  "1sg": "ja",
  "2sg": "ty",
  "3sg": "on",
  "1pl": "my",
  "2pl": "wy",
  "3pl": "oni",
};
const SUBJECT_EN: Record<string, string> = {
  "1sg": "I",
  "2sg": "you",
  "3sg": "he",
  "1pl": "we",
  "2pl": "y'all",
  "3pl": "they",
};

export function isPolish(locale: string): boolean {
  return locale.toLowerCase().startsWith("pl");
}

/** Subject pronoun in the target language for a person key (verbs render "ja jestem" / "I am"). */
export function targetSubject(vp: VoicingProfile, key: string): string {
  return isPolish(vp.targetLocale) ? SUBJECT_PL[key] ?? "" : SUBJECT_EN[key] ?? "";
}

function withPosition(item: VoiceItem, index: number, total: number): VoiceItem {
  return { ...item, position: total > 1 ? `${index + 1} / ${total}` : null };
}

/**
 * Start the shared study queue over a list of items. `speakSource` controls whether the
 * cue is spoken (sentences: yes; word forms: no — only target audio exists in R2).
 */
export function startQueue(
  items: VoiceItem[],
  vp: VoicingProfile,
  opts: { speakSource?: boolean; startIndex?: number } = {},
): void {
  if (items.length === 0) return;
  studyQueue.start({
    total: items.length,
    startIndex: opts.startIndex ?? 0,
    publishParked: (i) => {
      const it = withPosition(items[i], i, items.length);
      nowVoicing.set({
        en: it.en,
        pl: it.pl,
        literal: it.literal,
        words: it.words,
        position: it.position,
        lang: "pl",
      });
    },
    playItem: async (i) => {
      await voiceItem(studyQueue, withPosition(items[i], i, items.length), vp, {
        speakSource: opts.speakSource,
      });
    },
  });
}
