package com.sponic.langbang.data

import kotlinx.serialization.json.Json

/**
 * Two shared [Json] configurations, replacing the ~15 ad-hoc `Json { }` literals that
 * were scattered across the data, domain, and integrations layers.
 *
 * - [pretty]  — pretty-printed + tolerant of unknown keys. For on-device files we read
 *               AND write (user entries, sentence caches).
 * - [lenient] — tolerant of unknown keys + lenient parsing. For JSON we only read, from
 *               the network or bundled assets (Edge Function responses, lesson assets,
 *               Gemini output, update manifests).
 */
object LbJson {
    val pretty = Json { prettyPrint = true; ignoreUnknownKeys = true }
    val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
}
