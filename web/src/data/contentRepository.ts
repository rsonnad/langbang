import { cloudClient } from "../cloud/cloudClient";
import type { CloudBootstrap, CloudInstanceSummary } from "../cloud/models";
import { loadBootstrap as loadCachedBootstrap, saveBootstrap } from "./localStore";

export type ContentSource = "network" | "cache" | "bundled";

export interface ContentResult {
  bootstrap: CloudBootstrap;
  source: ContentSource;
}

const FALLBACK_BASE = `${import.meta.env.BASE_URL}fallback`;

export async function loadInstances(): Promise<CloudInstanceSummary[]> {
  try {
    const list = await cloudClient.fetchInstances();
    if (list.length) return list;
  } catch {
    // fall through to bundled
  }
  try {
    const res = await fetch(`${FALLBACK_BASE}/instances.json`);
    if (res.ok) {
      const data = (await res.json()) as { instances?: CloudInstanceSummary[] };
      return data.instances ?? [];
    }
  } catch {
    // ignore
  }
  return [];
}

/**
 * Resolve content for an instance, mirroring the Android repository:
 *  1. live bootstrap from the Worker (and persist it for offline),
 *  2. last-good bootstrap from IndexedDB,
 *  3. bundled JSON snapshot shipped with the app.
 */
export async function loadContent(instanceId: string): Promise<ContentResult> {
  try {
    const bootstrap = await cloudClient.fetchBootstrap(instanceId);
    void saveBootstrap(instanceId, bootstrap);
    return { bootstrap, source: "network" };
  } catch {
    // continue to cache / bundled
  }

  const cached = await loadCachedBootstrap(instanceId);
  if (cached) return { bootstrap: cached, source: "cache" };

  const res = await fetch(`${FALLBACK_BASE}/bootstrap-${instanceId}.json`);
  if (res.ok) {
    const bootstrap = (await res.json()) as CloudBootstrap;
    void saveBootstrap(instanceId, bootstrap);
    return { bootstrap, source: "bundled" };
  }

  throw new Error("No content available — offline with no cached or bundled content.");
}
