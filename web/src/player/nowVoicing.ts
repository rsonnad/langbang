import type { TokenPair } from "../cloud/models";
import { Store } from "../lib/store";

/**
 * What is currently being voiced — surfaced in a persistent panel so the learner can
 * always see the source cue / target answer / word-for-word gloss of the live audio.
 * Web port of the Android NowVoicingBus.
 *
 * `lang`: "en" (source cue), "pl" (target answer), "pl-slow" (slow target), or null when idle.
 */
export interface NowVoicing {
  en: string;
  pl: string;
  literal?: string | null;
  lang: "en" | "pl" | "pl-slow" | "pause" | null;
  position?: string | null;
  words?: TokenPair[] | null;
  /** Hide the target text (recall drill) until reveal. */
  plHidden?: boolean;
}

export const nowVoicing = new Store<NowVoicing | null>(null);

export function clearNowVoicing(): void {
  nowVoicing.set(null);
}
