package com.sponic.langbang.domain

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.integrations.AzureTtsClient

/** One unit of audio the app needs cached: a (text, locale, voice) triple. */
data class AudioUnit(val text: String, val locale: String, val voice: String)

/**
 * The single source of truth for every audio unit the app needs cached — phrases,
 * verb / pronoun / adjective / noun forms, phoneme examples, and every generated example
 * sentence (both languages).
 *
 * Consumed by both [PrefetchService] (on-device synth) and [R2AudioDownloader] (bulk R2
 * pull). Keeping ONE walk means the R2 manifest and the synth fallback can never drift
 * apart — a divergence that previously left phrase-bank sentences silent for a long time
 * (see the history note in [R2AudioDownloader.buildManifestPhrases]).
 *
 * Polish text expands to three voices — normal + both slow styles (V2 stretch, ART
 * re-articulated) — so flipping Settings → Slow audio style always hits a cached file.
 * The legacy -50% slow voice is intentionally NOT emitted: nothing plays it and R2 never
 * serves it. English cues are a single voice. The final de-dup collapses repeats, so the
 * emission order below is cosmetic (it only affects prefetch progress display).
 */
fun audioManifest(repo: LessonRepository): List<AudioUnit> {
    val lesson = repo.lesson2()
    val adjLesson = repo.lesson3()
    val advLesson = repo.lesson4()
    val nounLesson = repo.lesson6()
    val pron = repo.pronunciation()
    return buildList {
        fun addEn(text: String) {
            if (text.isEmpty()) return
            add(AudioUnit(text, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F))
        }
        fun addPl(text: String) {
            if (text.isEmpty()) return
            add(AudioUnit(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F))
            add(AudioUnit(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_V2))
            add(AudioUnit(text, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_ART))
        }

        // --- Core forms ---
        lesson.phrases.forEach { p -> addEn(p.en); addPl(p.pl) }
        lesson.verbs.forEach { v ->
            v.forms.forEach { (k, f) -> addPl("${audioPronoun(k)} $f".trim()) }
            v.past_forms?.forEach { (k, f) -> addPl("${audioPronoun(k)} $f".trim()) }
        }
        lesson.pronouns.forEach { p -> p.case_forms.values.forEach { addPl(it) } }
        adjLesson.adjectives.forEach { a ->
            a.nom.values.forEach { addPl(it) }
            a.acc.values.forEach { addPl(it) }
        }
        advLesson.adverbs.forEach { adv -> addPl(adv.lemma) }
        nounLesson.nouns.forEach { n ->
            n.nom.values.forEach { addPl(it) }
            n.acc.values.forEach { addPl(it) }
            n.gen.values.forEach { addPl(it) }
        }
        pron.phonemes.forEach { ph -> ph.examples.forEach { addPl(it.pl) } }

        // --- Generated example sentences (EN cue + PL target + slow PL) ---
        lesson.verbs.forEach { v ->
            repo.sentencesFor(v.lemma, VerbSentenceStore.TENSE_PRESENT).forEach { s -> addEn(s.en); addPl(s.pl) }
            repo.sentencesFor(v.lemma, VerbSentenceStore.TENSE_PAST).forEach { s -> addEn(s.en); addPl(s.pl) }
        }
        adjLesson.adjectives.forEach { a ->
            repo.adjectiveSentencesFor(a.lemma).forEach { s -> addEn(s.en); addPl(s.pl) }
        }
        advLesson.adverbs.forEach { adv ->
            repo.adverbSentencesFor(adv.lemma).forEach { s -> addEn(s.en); addPl(s.pl) }
        }
        nounLesson.nouns.forEach { n ->
            repo.nounSentencesFor(n.lemma).forEach { s -> addEn(s.en); addPl(s.pl) }
        }
        // Phrase-bank (lesson 5) sentences feed the "Play Phrases" queue exactly like the
        // verb/adj/adv sentences above, so they MUST be enumerated too — otherwise they
        // miss the bulk cache and fall through to slow per-phrase on-demand synth.
        repo.lesson5().groups.forEach { g ->
            g.sentences.forEach { s -> addEn(s.en); addPl(s.pl) }
        }
    }.distinctBy { it.text + "|" + it.locale + "|" + it.voice }
}
