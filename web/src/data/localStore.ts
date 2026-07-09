import { openDB, type IDBPDatabase } from "idb";
import type { CloudBootstrap, PhraseGroup } from "../cloud/models";

// IndexedDB persistence: last-good bootstrap per instance (offline boot), user-owned
// custom phrase groups, and a small kv bag. Audio blobs live in CacheStorage (audioCache.ts).
const DB_NAME = "langbang";
const DB_VERSION = 1;
const STORE_BOOTSTRAP = "bootstrap";
const STORE_CUSTOM = "customPhrases";
const STORE_KV = "kv";

let dbPromise: Promise<IDBPDatabase> | null = null;

function db(): Promise<IDBPDatabase> {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(database) {
        if (!database.objectStoreNames.contains(STORE_BOOTSTRAP)) {
          database.createObjectStore(STORE_BOOTSTRAP);
        }
        if (!database.objectStoreNames.contains(STORE_CUSTOM)) {
          database.createObjectStore(STORE_CUSTOM, { keyPath: "id" });
        }
        if (!database.objectStoreNames.contains(STORE_KV)) {
          database.createObjectStore(STORE_KV);
        }
      },
    });
  }
  return dbPromise;
}

export async function saveBootstrap(instanceId: string, bootstrap: CloudBootstrap): Promise<void> {
  try {
    (await db()).put(STORE_BOOTSTRAP, bootstrap, instanceId);
  } catch {
    // Non-fatal — the app still runs from the in-memory bootstrap.
  }
}

export async function loadBootstrap(instanceId: string): Promise<CloudBootstrap | null> {
  try {
    const value = await (await db()).get(STORE_BOOTSTRAP, instanceId);
    return (value as CloudBootstrap) ?? null;
  } catch {
    return null;
  }
}

// --- Custom (local-only) phrase groups, keyed by instance ---

interface CustomRecord {
  id: string; // `${instanceId}:${groupId}`
  instanceId: string;
  group: PhraseGroup;
}

export async function listCustomGroups(instanceId: string): Promise<PhraseGroup[]> {
  try {
    const all = (await (await db()).getAll(STORE_CUSTOM)) as CustomRecord[];
    return all.filter((r) => r.instanceId === instanceId).map((r) => r.group);
  } catch {
    return [];
  }
}

export async function saveCustomGroup(instanceId: string, group: PhraseGroup): Promise<void> {
  const record: CustomRecord = { id: `${instanceId}:${group.id}`, instanceId, group };
  (await db()).put(STORE_CUSTOM, record);
}

export async function deleteCustomGroup(instanceId: string, groupId: string): Promise<void> {
  (await db()).delete(STORE_CUSTOM, `${instanceId}:${groupId}`);
}

export async function kvGet<T>(key: string): Promise<T | null> {
  try {
    return ((await (await db()).get(STORE_KV, key)) as T) ?? null;
  } catch {
    return null;
  }
}

export async function kvSet(key: string, value: unknown): Promise<void> {
  try {
    (await db()).put(STORE_KV, value, key);
  } catch {
    // ignore quota errors
  }
}
