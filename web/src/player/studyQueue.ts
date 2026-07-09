import { AudioPlayer } from "../audio/audioPlayer";
import { directAudioUrl, synthAudioUrl } from "../audio/audioManifest";
import type { CloudAudioConfig } from "../cloud/models";
import { Store } from "../lib/store";
import { clearNowVoicing } from "./nowVoicing";
import { settingsStore } from "./settings";
import { runSpeechRatingCycle, speechRatingArmed } from "./speechRating";

const CANCELLED = Symbol("cancelled");

export interface QueueControls {
  rewind: boolean;
  next: boolean;
  restart: boolean;
}

export interface QueueState {
  playingIndex: number;
  isPaused: boolean;
  hasQueue: boolean;
  controls: QueueControls;
}

export interface StartOptions {
  total: number;
  startIndex?: number;
  rewindable?: boolean;
  nextable?: boolean;
  restartable?: boolean;
  /** Republish item i's NowVoicing without audio (used when parking after next/rewind while paused). */
  publishParked?: (index: number) => void;
  /** Warm item i's audio in the background while the previous item plays. */
  prefetchItem?: (index: number) => Promise<void>;
  /** Voice one item using say()/reveal(); publishes its own NowVoicing. */
  playItem: (index: number) => Promise<void>;
}

const IDLE: QueueState = {
  playingIndex: -1,
  isPaused: false,
  hasQueue: false,
  controls: { rewind: true, next: true, restart: true },
};

/**
 * Single shared driver for the cue → reveal → answer study loop, ported from the Android
 * StudyQueuePlayer. Owns the index, the cancellable run, the pause gate, prefetch-ahead, and
 * teardown. Pause is true pause-in-place; next/rewind park when paused; loop wraps the queue.
 */
export class StudyQueue {
  readonly state = new Store<QueueState>(IDLE);

  private player = new AudioPlayer();
  private audioCfg: CloudAudioConfig | null = null;

  private gen = 0; // bumped to cancel the active run
  private index = 0;
  private itemTotal = 0;
  private running = false;
  private paused = false;
  private pausedResolvers: Array<() => void> = [];

  private playItem: ((index: number) => Promise<void>) | null = null;
  private prefetchItem: ((index: number) => Promise<void>) | null = null;
  private publishParked: ((index: number) => void) | null = null;

  setAudioConfig(cfg: CloudAudioConfig | null): void {
    this.audioCfg = cfg;
  }

  get hasQueue(): boolean {
    return this.state.get().hasQueue;
  }

  start(opts: StartOptions): void {
    if (opts.total <= 0) return;
    this.itemTotal = opts.total;
    this.index = Math.min(Math.max(opts.startIndex ?? 0, 0), opts.total - 1);
    this.playItem = opts.playItem;
    this.prefetchItem = opts.prefetchItem ?? null;
    this.publishParked = opts.publishParked ?? null;
    this.state.set({
      playingIndex: this.index,
      isPaused: false,
      hasQueue: true,
      controls: {
        rewind: opts.rewindable ?? true,
        next: opts.nextable ?? true,
        restart: opts.restartable ?? true,
      },
    });
    this.launch();
  }

  // --- primitives used by playItem closures ---

  async say(text: string, locale: string, voice: string): Promise<boolean> {
    const myGen = this.gen;
    await this.gate(myGen);
    if (!text.trim() || !this.audioCfg) return true;
    const req = { text, locale, voice };
    const direct = await directAudioUrl(this.audioCfg, req);
    this.assertLive(myGen);
    let ok = await this.player.play(direct);
    this.assertLive(myGen);
    if (!ok) {
      const synth = await synthAudioUrl(req);
      this.assertLive(myGen);
      if (synth) {
        ok = await this.player.play(synth);
        this.assertLive(myGen);
      }
    }
    return ok;
  }

  async reveal(ms: number): Promise<void> {
    const myGen = this.gen;
    let remaining = ms;
    while (remaining > 0) {
      await this.gate(myGen);
      const step = Math.min(100, remaining);
      await delay(step);
      this.assertLive(myGen);
      remaining -= step;
    }
  }

  // --- transport ---

  pauseResume(): void {
    if (this.paused) this.resume();
    else this.pause();
  }

  pause(): void {
    if (!this.hasQueue || this.paused) return;
    this.paused = true;
    this.state.update((s) => ({ ...s, isPaused: true }));
    this.player.pause();
  }

  resume(): void {
    if (!this.paused) return;
    this.paused = false;
    this.state.update((s) => ({ ...s, isPaused: false }));
    this.flushPaused();
    if (this.running) this.player.resume();
    else this.launch();
  }

  next(): void {
    if (this.itemTotal <= 0) return;
    const wasPaused = this.paused;
    this.cancelForRetain();
    let n = this.index + 1;
    if (n >= this.itemTotal) {
      if (settingsStore.get().loop) n = 0;
      else {
        this.stop();
        return;
      }
    }
    this.index = n;
    if (wasPaused) this.parkAt(n);
    else this.launch();
  }

  rewind(): void {
    if (this.itemTotal <= 0) return;
    const wasPaused = this.paused;
    this.cancelForRetain();
    this.index = Math.max(0, this.index - 1);
    if (wasPaused) this.parkAt(this.index);
    else this.launch();
  }

  restart(): void {
    if (this.itemTotal <= 0) return;
    this.cancelForRetain();
    this.index = 0;
    this.launch();
  }

  stop(): void {
    this.gen += 1;
    this.running = false;
    this.flushPaused();
    this.player.stop();
    this.teardown();
  }

  // --- internals ---

  private launch(): void {
    this.gen += 1;
    this.paused = false;
    this.running = true;
    this.state.update((s) => ({ ...s, isPaused: false, hasQueue: true, playingIndex: this.index }));
    void this.runLoop(this.gen);
  }

  private async runLoop(myGen: number): Promise<void> {
    try {
      while (myGen === this.gen) {
        const i = this.index;
        this.state.update((s) => ({ ...s, playingIndex: i }));
        const ahead = this.peekNext(i);
        if (ahead != null && this.prefetchItem) void this.prefetchItem(ahead).catch(() => {});
        await this.playItem?.(i);
        if (myGen !== this.gen) return;
        // Speech rating: when the mic latch is on, listen → score → hold before advancing.
        // The gate afterwards honours a Pause/Stop issued during scoring (throws CANCELLED
        // if a transport action superseded this run). No-op when disarmed.
        if (speechRatingArmed.get()) {
          await runSpeechRatingCycle();
          await this.gate(myGen);
        }
        const n = this.peekNext(i);
        if (n == null) break;
        this.index = n;
      }
      if (myGen === this.gen) {
        this.running = false;
        this.stop();
      }
    } catch (err) {
      if (err !== CANCELLED) {
        this.running = false;
        this.stop();
      }
      // CANCELLED: a transport action took over; leave its state in place.
    }
  }

  private peekNext(i: number): number | null {
    const n = i + 1;
    if (n < this.itemTotal) return n;
    return settingsStore.get().loop ? 0 : null;
  }

  private cancelForRetain(): void {
    this.gen += 1;
    this.running = false;
    this.flushPaused();
    this.player.stop();
  }

  private parkAt(i: number): void {
    this.paused = true;
    this.running = false;
    this.state.update((s) => ({ ...s, playingIndex: i, isPaused: true, hasQueue: true }));
    this.publishParked?.(i);
  }

  private teardown(): void {
    this.paused = false;
    this.running = false;
    this.state.set(IDLE);
    clearNowVoicing();
  }

  private async gate(myGen: number): Promise<void> {
    if (myGen !== this.gen) throw CANCELLED;
    if (this.paused) {
      await new Promise<void>((resolve) => this.pausedResolvers.push(resolve));
      if (myGen !== this.gen) throw CANCELLED;
    }
  }

  private assertLive(myGen: number): void {
    if (myGen !== this.gen) throw CANCELLED;
  }

  private flushPaused(): void {
    const resolvers = this.pausedResolvers;
    this.pausedResolvers = [];
    resolvers.forEach((r) => r());
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** One shared queue — only one study queue is active at a time (like Android PlaybackController). */
export const studyQueue = new StudyQueue();
