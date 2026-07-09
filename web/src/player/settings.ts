import { Store } from "../lib/store";

/** Playback preferences — the web equivalent of the Android PracticePrefs/AudioPrefs. */
export interface PlaybackSettings {
  /** Play the source-language cue before the target answer. */
  sourceCue: boolean;
  /** Also play a slowed re-articulation of the target answer. */
  slowTarget: boolean;
  /** Loop the queue until stopped. */
  loop: boolean;
  /** Reveal/inter-segment gap in milliseconds. */
  revealMs: number;
}

const KEY = "langbang.settings.v1";

const DEFAULTS: PlaybackSettings = {
  sourceCue: true,
  slowTarget: true,
  loop: false,
  revealMs: 1100,
};

function load(): PlaybackSettings {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return DEFAULTS;
    return { ...DEFAULTS, ...(JSON.parse(raw) as Partial<PlaybackSettings>) };
  } catch {
    return DEFAULTS;
  }
}

export const settingsStore = new Store<PlaybackSettings>(load());

settingsStore.subscribe(() => {
  try {
    localStorage.setItem(KEY, JSON.stringify(settingsStore.get()));
  } catch {
    // ignore persistence failures
  }
});

export function updateSettings(patch: Partial<PlaybackSettings>): void {
  settingsStore.update((s) => ({ ...s, ...patch }));
}
