package com.sponic.langbang.data

import kotlinx.serialization.Serializable

/**
 * Shape of `langbang/sentences/v{N}/manifest.json` on R2. Each entry's `key` is
 * relative to the prompt-version prefix (e.g. `verbs/być-present.json`,
 * `adjectives/dobry.json`, `adverbs/szybko.json`). The client uses [url] for the
 * raw bundle download and [sha256] to decide whether a locally-cached copy is
 * already current.
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
