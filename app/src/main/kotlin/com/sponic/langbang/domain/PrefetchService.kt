package com.sponic.langbang.domain

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
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
            val text = "${repo.targetSubjectFor(k)} $form".trim()
            if (text.isEmpty()) return@forEach
            ensureTarget(text)
        }
        verb.past_forms?.forEach { (k, form) ->
            val text = "${repo.targetSubjectFor(k)} $form".trim()
            if (text.isEmpty()) return@forEach
            ensureTarget(text)
        }
    }

    /** Synthesise TTS for every form of a newly-added adjective. */
    suspend fun prefetchAdjective(adj: AdjectiveEntry) {
        (adj.nom.values + adj.acc.values).forEach { form -> ensureTarget(form) }
    }

    /** Synthesise TTS for every form of a newly-added noun. */
    suspend fun prefetchNoun(noun: NounEntry) {
        (noun.nom.values + noun.acc.values + noun.gen.values).forEach { form -> ensureTarget(form) }
    }

    /** Synthesise both sides of each generated example sentence (source cue + target + slow target). */
    suspend fun prefetchSentences(sentences: List<SentenceExample>) {
        val source = repo.sourceAudioVoice()
        sentences.forEach { s ->
            ensureAudio(s.en, source.locale, source.voice)
            ensureTarget(s.pl)
        }
    }

    private suspend fun ensureTarget(text: String) {
        if (text.isEmpty()) return
        val target = repo.targetAudioVoice()
        val slowVoices = repo.targetSlowVoices().ifEmpty {
            listOf("${target.voice}|slow60v1", "${target.voice}|slowart1")
        }
        ensureAudio(text, target.locale, target.voice)
        slowVoices.forEach { ensureAudio(text, target.locale, it) }
    }

    private suspend fun ensureAudio(text: String, locale: String, voice: String) {
        if (text.isEmpty()) return
        val file = cache.fileFor(locale, voice, text)
        if (!cache.has(file)) tts.synthesize(text, voice, locale, file)
    }
}
