package com.sponic.langbang.domain

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.integrations.AzureTtsClient

data class PrefetchProgress(
    val total: Int = 0,
    val done: Int = 0,
    val current: String = "",
    val finished: Boolean = false
) {
    val ratio: Float get() = if (total == 0) 0f else done / total.toFloat()
}

/**
 * Stateless prefetch logic. Walks every audio unit the app needs (see [audioManifest]),
 * calls Azure TTS for cache misses, writes to [AudioCache].
 *
 * Designed to be called from a WorkManager [PrefetchWorker]. Progress is reported via a
 * callback so the worker can publish it through WorkManager's progress channel.
 */
class PrefetchService(
    private val tts: AzureTtsClient,
    private val cache: AudioCache,
    private val repo: LessonRepository
) {
    /** Synthesise (on cache miss) every audio unit the app needs. Despite the name this
     *  covers all lessons + generated sentences — see [audioManifest]. */
    suspend fun prefetchAll(onProgress: suspend (PrefetchProgress) -> Unit) {
        val units = audioManifest(repo)
        units.forEachIndexed { i, u ->
            onProgress(PrefetchProgress(total = units.size, done = i, current = u.text))
            val file = cache.fileFor(u.locale, u.voice, u.text)
            if (!cache.has(file)) {
                tts.synthesize(u.text, u.voice, u.locale, file)
            }
        }
        onProgress(
            PrefetchProgress(
                total = units.size, done = units.size,
                current = "", finished = true
            )
        )
    }

    /** Synthesise TTS for every form of a newly-added verb so playback works immediately. */
    suspend fun prefetchVerb(verb: VerbEntry) {
        verb.forms.forEach { (k, form) ->
            val text = "${audioPronoun(k)} $form".trim()
            if (text.isEmpty()) return@forEach
            ensurePl(text)
        }
        verb.past_forms?.forEach { (k, form) ->
            val text = "${audioPronoun(k)} $form".trim()
            if (text.isEmpty()) return@forEach
            ensurePl(text)
        }
    }

    /** Synthesise TTS for every form of a newly-added adjective. */
    suspend fun prefetchAdjective(adj: AdjectiveEntry) {
        (adj.nom.values + adj.acc.values).forEach { form -> ensurePl(form) }
    }

    /** Synthesise TTS for every form of a newly-added noun. */
    suspend fun prefetchNoun(noun: NounEntry) {
        (noun.nom.values + noun.acc.values + noun.gen.values).forEach { form -> ensurePl(form) }
    }

    /** Synthesise both sides of each generated example sentence (EN cue + PL target + slow PL). */
    suspend fun prefetchSentences(sentences: List<SentenceExample>) {
        sentences.forEach { s ->
            ensureAudio(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
            ensurePl(s.pl)
        }
    }

    private suspend fun ensurePl(text: String) {
        if (text.isEmpty()) return
        ensureAudio(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
        // Only the two slow variants playback can select — the legacy -50% is intentionally
        // NOT pre-cached (nothing plays it, R2 never serves it). See [audioManifest].
        ensureAudio(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_V2)
        ensureAudio(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_ART)
    }

    private suspend fun ensureAudio(text: String, locale: String, voice: String) {
        if (text.isEmpty()) return
        val file = cache.fileFor(locale, voice, text)
        if (!cache.has(file)) tts.synthesize(text, voice, locale, file)
    }
}
