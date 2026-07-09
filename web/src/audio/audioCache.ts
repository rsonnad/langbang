// Opportunistic offline cache for audio clips, backed by the CacheStorage API.
// Not on the playback hot path (clips stream from R2 with immutable cache headers);
// this is the seam the offline phase ("Download for offline") builds on.
export const CACHE_NAME = "langbang-audio-v1";

function cacheAvailable(): boolean {
  return typeof caches !== "undefined";
}

/**
 * If `url` is stored offline, return a blob: object URL the <audio> element can play
 * without a network round-trip (so playback works offline). Returns null on a miss or
 * when CacheStorage is unavailable. Caller owns the object URL and must revoke it.
 */
export async function cachedAudioBlobUrl(url: string): Promise<string | null> {
  if (!cacheAvailable()) return null;
  try {
    const cache = await caches.open(CACHE_NAME);
    const hit = await cache.match(url);
    if (!hit) return null;
    return URL.createObjectURL(await hit.blob());
  } catch {
    return null;
  }
}

export async function primeAudio(
  urls: string[],
  onProgress?: (done: number, total: number) => void,
): Promise<{ cached: number; failed: number }> {
  if (!cacheAvailable()) return { cached: 0, failed: urls.length };
  const cache = await caches.open(CACHE_NAME);
  let cached = 0;
  let failed = 0;
  let done = 0;
  for (const url of urls) {
    try {
      const existing = await cache.match(url);
      if (!existing) {
        const res = await fetch(url);
        if (res.ok) await cache.put(url, res.clone());
        else throw new Error(`HTTP ${res.status}`);
      }
      cached += 1;
    } catch {
      failed += 1;
    }
    done += 1;
    onProgress?.(done, urls.length);
  }
  return { cached, failed };
}

export async function isAudioCached(url: string): Promise<boolean> {
  if (!cacheAvailable()) return false;
  const cache = await caches.open(CACHE_NAME);
  return !!(await cache.match(url));
}

/** How many audio clips are currently stored offline on this device. */
export async function countCachedAudio(): Promise<number> {
  if (!cacheAvailable()) return 0;
  try {
    const cache = await caches.open(CACHE_NAME);
    return (await cache.keys()).length;
  } catch {
    return 0;
  }
}

export async function clearAudioCache(): Promise<void> {
  if (!cacheAvailable()) return;
  await caches.delete(CACHE_NAME);
}
