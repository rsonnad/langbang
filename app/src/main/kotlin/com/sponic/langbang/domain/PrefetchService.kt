package com.sponic.langbang.domain

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdverbEntry
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
 * Stateless prefetch logic. Iterates every audio unit in Lesson 1 (phrases + verb forms +
 * pronoun forms across both languages), calls Azure TTS for misses, writes to [AudioCache].
 *
 * Designed to be called from a WorkManager [PrefetchWorker]. Progress is reported via a
 * callback so the worker can publish it through WorkManager's progress channel.
 */
class PrefetchService(
    private val tts: AzureTtsClient,
    private val cache: AudioCache,
    private val repo: LessonRepository
) {
    suspend fun prefetchLesson1(onProgress: suspend (PrefetchProgress) -> Unit) {
        val lesson = repo.lesson2()
        val adjLesson = repo.lesson3()
        val advLesson = repo.lesson4()
        val nounLesson = repo.lesson6()
        val pron = repo.pronunciation()
        val units = buildList {
            fun addPl(text: String) {
                if (text.isEmpty()) return
                add(Unit_(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F))
                // Pre-cache the two slow variants playback can actually select (Settings →
                // Slow audio style: "Stretch" = -60%, "Articulate" = per-word breaks). The
                // legacy -50% (PL_PL_F_SLOW) is NOT cached: nothing plays it (slowPlVoice()
                // only returns V2/ART) and R2 never serves it, so synthesising one per
                // phrase on-device was thousands of Azure calls that never completed —
                // which is exactly why the "audio N/total" counter sat stuck. See
                // AudioPrefsStore.slowPlVoice().
                add(Unit_(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_V2))
                add(Unit_(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_ART))
            }
            lesson.phrases.forEach { p ->
                add(Unit_(p.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                addPl(p.pl)
            }
            lesson.verbs.forEach { v ->
                v.forms.forEach { (k, f) ->
                    addPl("${audioPronoun(k)} $f".trim())
                }
                v.past_forms?.forEach { (k, f) ->
                    addPl("${audioPronoun(k)} $f".trim())
                }
            }
            lesson.pronouns.forEach { p ->
                p.case_forms.values.forEach { f -> addPl(f) }
            }
            adjLesson.adjectives.forEach { a ->
                a.nom.values.forEach { f -> addPl(f) }
                a.acc.values.forEach { f -> addPl(f) }
            }
            nounLesson.nouns.forEach { n ->
                n.nom.values.forEach { f -> addPl(f) }
                n.acc.values.forEach { f -> addPl(f) }
                n.gen.values.forEach { f -> addPl(f) }
            }
            pron.phonemes.forEach { ph ->
                ph.examples.forEach { ex -> addPl(ex.pl) }
            }
            // Saved Gemini sentences — so disconnected mode covers everything generated so far.
            lesson.verbs.forEach { v ->
                repo.sentencesFor(v.lemma, VerbSentenceStore.TENSE_PRESENT).forEach { s ->
                    add(Unit_(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                    addPl(s.pl)
                }
                repo.sentencesFor(v.lemma, VerbSentenceStore.TENSE_PAST).forEach { s ->
                    add(Unit_(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                    addPl(s.pl)
                }
            }
            adjLesson.adjectives.forEach { a ->
                repo.adjectiveSentencesFor(a.lemma).forEach { s ->
                    add(Unit_(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                    addPl(s.pl)
                }
            }
            advLesson.adverbs.forEach { adv ->
                addPl(adv.lemma)
                repo.adverbSentencesFor(adv.lemma).forEach { s ->
                    add(Unit_(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                    addPl(s.pl)
                }
            }
            nounLesson.nouns.forEach { n ->
                repo.nounSentencesFor(n.lemma).forEach { s ->
                    add(Unit_(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                    addPl(s.pl)
                }
            }
            // Lesson 5 — multi-sentence phrases. Every sentence needs EN + PL + both
            // slow variants pre-cached so "Play all" works offline.
            repo.lesson5().groups.forEach { g ->
                g.sentences.forEach { s ->
                    add(Unit_(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
                    addPl(s.pl)
                }
            }
        }.distinctBy { it.text + "|" + it.locale + "|" + it.voice }

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
        // Only the two slow variants playback can select — see addPl() for why the
        // legacy -50% is intentionally NOT pre-cached.
        ensureAudio(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_V2)
        ensureAudio(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_ART)
    }

    private suspend fun ensureAudio(text: String, locale: String, voice: String) {
        if (text.isEmpty()) return
        val file = cache.fileFor(locale, voice, text)
        if (!cache.has(file)) tts.synthesize(text, voice, locale, file)
    }

    private data class Unit_(val text: String, val locale: String, val voice: String)
}
