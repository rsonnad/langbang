import { cloudClient } from "../cloud/cloudClient";
import type { AudioPhraseReq, CloudAudioConfig } from "../cloud/models";

// The Worker names every cached clip sha1(`${locale}|${voice}|${text}`).mp3 under the
// public R2 base, so the client can address pre-synthesized audio directly with no
// round-trip. Uncached text is synthesized on demand via POST /v1/audio/manifest.

async function sha1Hex(input: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(input));
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export async function directAudioUrl(cfg: CloudAudioConfig, req: AudioPhraseReq): Promise<string> {
  const sha1 = await sha1Hex(`${req.locale}|${req.voice}|${req.text}`);
  const base = cfg.publicR2Base.replace(/\/+$/, "");
  const prefix = cfg.audioPrefix.replace(/^\/+|\/+$/g, "");
  return `${base}/${prefix}/${sha1}.mp3`;
}

/** Force synthesis (and upload) of a clip, returning its public URL or null on failure. */
export async function synthAudioUrl(req: AudioPhraseReq): Promise<string | null> {
  try {
    const resp = await cloudClient.audioManifest([req]);
    const entry = resp.manifest[0];
    if (entry && !entry.error && entry.url) return entry.url;
  } catch {
    // fall through
  }
  return null;
}

/** Batch-resolve URLs for many clips at once (used for pre-caching / prefetch). */
export async function resolveManifest(reqs: AudioPhraseReq[]): Promise<Map<string, string>> {
  const map = new Map<string, string>();
  if (reqs.length === 0) return map;
  try {
    const resp = await cloudClient.audioManifest(reqs);
    for (const entry of resp.manifest) {
      if (!entry.error && entry.url) {
        map.set(`${entry.locale}|${entry.voice}|${entry.text}`, entry.url);
      }
    }
  } catch {
    // ignore — callers fall back to direct URLs
  }
  return map;
}
