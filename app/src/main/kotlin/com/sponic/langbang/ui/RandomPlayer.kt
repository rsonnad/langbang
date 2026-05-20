package com.sponic.langbang.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Drives the top-level "Play random" button. Collects every cached Gemini sentence
 * (verb examples filtered to the 1sg/2sg/3sg conjugations only — "I", "you", "he/she" —
 * and every adjective example), shuffles them, and plays each in EN → PL-slow → EN → PL
 * order so the user gets the same scaffolded drill as Play-all per verb.
 */
internal class RandomPlayerState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    var playing: Boolean by mutableStateOf(false)
        private set
    var queueSize: Int by mutableStateOf(0)
        private set

    fun toggle() {
        if (job?.isActive == true) stop() else start()
    }

    fun stop() {
        job?.cancel()
        job = null
        playing = false
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    private fun start() {
        val phrases = collectPhrases().shuffled()
        queueSize = phrases.size
        if (phrases.isEmpty()) return
        playing = true
        job = scope.launch {
            try {
                phrases.forEachIndexed { i, s ->
                    val pos = "${i + 1}/${phrases.size}"
                    pub(s, "en", pos)
                    playAndAwait(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                    pub(s, "pl-slow", pos)
                    playAndAwait(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW)
                    pub(s, "en", pos)
                    playAndAwait(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                    pub(s, "pl", pos)
                    playAndAwait(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                }
            } finally {
                playing = false
                job = null
                NowVoicingBus.clear()
            }
        }
    }

    private fun pub(s: SentenceExample, lang: String, pos: String) {
        NowVoicingBus.publish(NowVoicing(s.en, s.pl, s.literal, lang, pos))
    }

    private suspend fun playAndAwait(text: String, locale: String, voice: String) {
        if (text.isEmpty()) return
        val file = app.audioCache.fileFor(locale, voice, text)
        if (!app.audioCache.has(file)) {
            app.tts.synthesize(text, voice, locale, file)
        }
        if (!app.audioCache.has(file)) return
        suspendCancellableCoroutine<Unit> { cont ->
            app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { app.audioPlayer.stop() }
        }
    }

    /**
     * Verb sentences carry no person tag, so we filter by word match: a sentence is
     * eligible iff one of its tokens equals the verb's 1sg / 2sg / 3sg form. That way
     * cached sentences from when other pronouns were checked don't leak in.
     */
    private fun collectPhrases(): List<SentenceExample> {
        val allowedPersonKeys = setOf("1sg", "2sg", "3sg")
        val verbLesson = app.lessonRepo.lesson2()
        val verbSentences = verbLesson.verbs.flatMap { verb ->
            val allowedForms = verb.forms
                .filterKeys { it in allowedPersonKeys }
                .values
                .filter { it.isNotBlank() }
                .map { it.lowercase() }
                .toSet()
            if (allowedForms.isEmpty()) emptyList()
            else app.lessonRepo.sentencesFor(verb.lemma)
                .filter { tokensMatch(it.pl, allowedForms) }
        }
        val adjLesson = app.lessonRepo.lesson3()
        val adjSentences = adjLesson.adjectives.flatMap {
            app.lessonRepo.adjectiveSentencesFor(it.lemma)
        }
        return verbSentences + adjSentences
    }

    private fun tokensMatch(pl: String, allowed: Set<String>): Boolean {
        return pl.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .any { it.isNotEmpty() && it in allowed }
    }
}
