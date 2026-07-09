import { cachedAudioBlobUrl } from "./audioCache";

/**
 * One pooled HTMLAudioElement, the web analogue of the Android pooled AudioPlayer.
 * play() resolves true on natural end, false on error/stop/superseded. pause()/resume()
 * hold and continue the live clip in place (true pause, not replay-from-start).
 *
 * play() first checks the offline cache: a stored clip plays from a blob: URL (works with
 * no network); a miss streams from the original URL. A generation counter makes the latest
 * play() win if an async cache lookup resolves after a newer call.
 */
export class AudioPlayer {
  private el: HTMLAudioElement;
  private pending: ((ok: boolean) => void) | null = null;
  private gen = 0;
  private objectUrl: string | null = null;

  constructor() {
    this.el = typeof Audio !== "undefined" ? new Audio() : ({} as HTMLAudioElement);
    if (this.el.addEventListener) {
      this.el.preload = "auto";
      this.el.addEventListener("ended", () => this.settle(true));
      this.el.addEventListener("error", () => this.settle(false));
    }
  }

  private settle(ok: boolean): void {
    const resolve = this.pending;
    this.pending = null;
    if (resolve) resolve(ok);
  }

  play(url: string): Promise<boolean> {
    this.settle(false); // resolve any previous play before starting the next
    const gen = ++this.gen;
    return new Promise<boolean>((resolve) => {
      this.pending = resolve;
      void this.begin(url, gen);
    });
  }

  // Resolve the playable src (offline blob URL if cached, else the network URL), then start
  // playback — unless a newer play()/stop() superseded this one during the cache lookup.
  private async begin(url: string, gen: number): Promise<void> {
    const blob = await cachedAudioBlobUrl(url);
    if (gen !== this.gen) {
      if (blob) URL.revokeObjectURL(blob); // superseded; drop the unused object URL
      return;
    }
    this.setObjectUrl(blob);
    try {
      this.el.src = blob ?? url;
      this.el.currentTime = 0;
      const p = this.el.play();
      if (p && typeof p.catch === "function") p.catch(() => this.settle(false));
    } catch {
      this.settle(false);
    }
  }

  private setObjectUrl(next: string | null): void {
    if (this.objectUrl && this.objectUrl !== next) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = next;
  }

  pause(): void {
    try {
      this.el.pause();
    } catch {
      // ignore
    }
  }

  resume(): void {
    try {
      const p = this.el.play();
      if (p && typeof p.catch === "function") p.catch(() => {});
    } catch {
      // ignore
    }
  }

  stop(): void {
    this.gen++; // cancel any in-flight begin()
    try {
      this.el.pause();
      this.el.currentTime = 0;
    } catch {
      // ignore
    }
    this.setObjectUrl(null);
    this.settle(false);
  }

  get isPaused(): boolean {
    return !!this.el.paused;
  }
}
