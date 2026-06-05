package com.sponic.langbang.data

import kotlinx.serialization.Serializable

/**
 * Shape of `langbang/sentences/v{N}/manifest.json` on R2. Each entry's `key` is
 * relative to the prompt-version prefix (e.g. `verbs/być-present.json`,
 * `adjectives/dobry.json`, `adverbs/szybko.json`). Older manifests may contain
 * stale public bucket URLs after Cloudflare account migration, so the client
 * derives bundle URLs from the current manifest base unless [url] already points
 * at that base.
 */
@Serializable
data class R2SentenceManifest(
    val promptVersion: Int,
    val generatedAt: String,
    val entries: Map<String, R2SentenceManifestEntry>
)

@Serializable
data class R2SentenceManifestEntry(
    val sha256: String,
    val count: Int,
    val bytes: Int,
    val url: String
)
