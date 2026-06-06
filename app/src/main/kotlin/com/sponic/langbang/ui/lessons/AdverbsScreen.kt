package com.sponic.langbang.ui.lessons

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.WordListPlaybackHeader
import com.sponic.langbang.ui.common.WordPlayLimitControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Per-adverb state hoisted to screen root so the play/regenerate controls live in the
 * top bar (mirrors VerbsTabState / AdjectivesScreenState).
 */
internal class AdverbsScreenState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    var selected: AdverbEntry? by mutableStateOf(null)
        private set
    var sentences: List<SentenceExample> by mutableStateOf(emptyList())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var checkedLemmas: Set<String> by mutableStateOf(
        app.practicePrefs.checkedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS)
    )
        private set
    var playLimitText: String by mutableStateOf(
        app.practicePrefs.wordPlayLimit(PracticePrefsStore.CATEGORY_ADVERBS).toString()
    )
        private set
    var randomOrder: Boolean by mutableStateOf(
        app.practicePrefs.wordPlayRandom(PracticePrefsStore.CATEGORY_ADVERBS)
    )
        private set
    private var checkedDefaultsLoaded = false

    private val player = StudyQueuePlayer(app, scope)
    val playingIndex: Int get() = player.playingIndex
    val playing: Boolean get() = player.hasQueue

    fun select(adv: AdverbEntry?) {
        if (selected?.lemma == adv?.lemma) return
        stop()
        selected = adv
        sentences = adv?.let { app.lessonRepo.adverbSentencesFor(it.lemma) } ?: emptyList()
        error = null
    }

    fun generate() {
        if (busy) return
        val adv = selected ?: return
        busy = true; error = null
        scope.launch {
            app.gemini.generateAdverbSentences(adv)
                .onSuccess { list ->
                    app.lessonRepo.saveAdverbSentences(adv.lemma, list)
                    if (selected?.lemma == adv.lemma) sentences = list
                    app.prefetch.prefetchSentences(list)
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun stop() {
        player.stop()
    }

    fun ensureCheckedDefaults(allLemmas: List<String>) {
        if (checkedDefaultsLoaded) return
        checkedDefaultsLoaded = true
        if (!app.practicePrefs.hasCheckedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS)) {
            checkedLemmas = allLemmas.toSet()
            app.practicePrefs.setCheckedWordLemmas(
                PracticePrefsStore.CATEGORY_ADVERBS,
                checkedLemmas
            )
        } else {
            checkedLemmas = checkedLemmas.intersect(allLemmas.toSet())
        }
    }

    fun toggleChecked(lemma: String, checked: Boolean) {
        checkedLemmas = if (checked) checkedLemmas + lemma else checkedLemmas - lemma
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS, checkedLemmas)
    }

    fun setAllChecked(lemmas: List<String>, checked: Boolean) {
        checkedLemmas = if (checked) lemmas.toSet() else emptySet()
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS, checkedLemmas)
    }

    fun updatePlayLimitText(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(2)
        playLimitText = cleaned
        cleaned.toIntOrNull()?.let {
            app.practicePrefs.setWordPlayLimit(PracticePrefsStore.CATEGORY_ADVERBS, it)
        }
    }

    fun updateRandomOrder(enabled: Boolean) {
        randomOrder = enabled
        app.practicePrefs.setWordPlayRandom(PracticePrefsStore.CATEGORY_ADVERBS, enabled)
    }

    private fun playLimit(): Int =
        playLimitText.toIntOrNull()
            ?.coerceIn(PracticePrefsStore.MIN_WORD_PLAY_LIMIT, PracticePrefsStore.MAX_WORD_PLAY_LIMIT)
            ?: 0

    fun playCount(adverbs: List<AdverbEntry>): Int {
        val limit = playLimit()
        if (limit <= 0) return 0
        return adverbs
            .filter { it.lemma in checkedLemmas }
            .sumOf { sentencePoolFor(it).size.coerceAtMost(limit) }
    }

    fun playAll(adverbs: List<AdverbEntry>, quiz: Boolean) {
        if (player.hasQueue) { stop(); return }
        val items = buildCheckedSentenceQueue(adverbs, quiz)
        if (items.isEmpty()) return
        startQueue(items, quiz)
    }

    private fun buildCheckedSentenceQueue(
        adverbs: List<AdverbEntry>,
        quiz: Boolean
    ): List<SentenceExample> {
        val limit = playLimit()
        if (limit <= 0) return emptyList()
        val items = adverbs
            .filter { it.lemma in checkedLemmas }
            .flatMap { adv ->
                val pool = sentencePoolFor(adv)
                if (randomOrder) pool.shuffled().take(limit) else pool.take(limit)
            }
        return if (randomOrder || quiz) items.shuffled() else items
    }

    private fun sentencePoolFor(adverb: AdverbEntry): List<SentenceExample> =
        (app.lessonRepo.adverbSentencesFor(adverb.lemma) + fallbackAdverbSentences(adverb))
            .distinctBy { it.pl.lowercase() }

    private fun fallbackAdverbSentences(adverb: AdverbEntry): List<SentenceExample> {
        val gloss = adverb.en.substringBefore("/").trim().ifBlank { adverb.en }
        fun ex(pl: String, en: String, vararg words: Pair<String, String>) =
            SentenceExample(
                pl = pl,
                en = en,
                literal = words.joinToString(" ") { it.second },
                words = words.map { TokenPair(it.first, it.second) }
            )

        return when (adverb.lemma.lowercase()) {
            "bardzo" -> listOf(
                ex("Bardzo lubię kawę", "I really like coffee", "Bardzo" to "really", "lubię" to "I like", "kawę" to "coffee"),
                ex("Bardzo dziękuję", "Thank you very much", "Bardzo" to "very", "dziękuję" to "I thank"),
                ex("Bardzo chcę iść", "I really want to go", "Bardzo" to "really", "chcę" to "I want", "iść" to "to go"),
                ex("Bardzo lubię ten dom", "I really like this house", "Bardzo" to "really", "lubię" to "I like", "ten" to "this", "dom" to "house"),
                ex("Bardzo dobrze rozumiem", "I understand very well", "Bardzo" to "very", "dobrze" to "well", "rozumiem" to "I understand"),
                ex("Bardzo proszę", "Here you go", "Bardzo" to "very", "proszę" to "please"),
                ex("Bardzo mi miło", "It is very nice to meet you", "Bardzo" to "very", "mi" to "to me", "miło" to "nice"),
                ex("Bardzo szybko idę", "I am walking very quickly", "Bardzo" to "very", "szybko" to "quickly", "idę" to "I go"),
                ex("Bardzo dobrze mówię", "I speak very well", "Bardzo" to "very", "dobrze" to "well", "mówię" to "I speak"),
                ex("Bardzo chcę pomóc", "I really want to help", "Bardzo" to "really", "chcę" to "I want", "pomóc" to "to help")
            )
            "dobrze" -> listOf(
                ex("Dobrze mówię po polsku", "I speak Polish well", "Dobrze" to "well", "mówię" to "I speak", "po" to "in", "polsku" to "Polish"),
                ex("Dobrze się czuję", "I feel good", "Dobrze" to "good", "się" to "myself", "czuję" to "I feel"),
                ex("Dobrze rozumiem", "I understand well", "Dobrze" to "well", "rozumiem" to "I understand"),
                ex("Dobrze pracuję", "I work well", "Dobrze" to "well", "pracuję" to "I work"),
                ex("Dobrze śpię", "I sleep well", "Dobrze" to "well", "śpię" to "I sleep"),
                ex("Dobrze pamiętam", "I remember well", "Dobrze" to "well", "pamiętam" to "I remember"),
                ex("Dobrze czytam", "I read well", "Dobrze" to "well", "czytam" to "I read"),
                ex("Dobrze słyszę", "I hear well", "Dobrze" to "well", "słyszę" to "I hear"),
                ex("Dobrze znam to miejsce", "I know this place well", "Dobrze" to "well", "znam" to "I know", "to" to "this", "miejsce" to "place"),
                ex("Dobrze gotuję", "I cook well", "Dobrze" to "well", "gotuję" to "I cook")
            )
            "szybko" -> listOf(
                ex("Szybko idę", "I am walking quickly", "Szybko" to "quickly", "idę" to "I go"),
                ex("Szybko wracam", "I am coming back quickly", "Szybko" to "quickly", "wracam" to "I return"),
                ex("Szybko czytam", "I read quickly", "Szybko" to "quickly", "czytam" to "I read"),
                ex("Szybko piszę", "I write quickly", "Szybko" to "quickly", "piszę" to "I write"),
                ex("Szybko mówię", "I speak quickly", "Szybko" to "quickly", "mówię" to "I speak"),
                ex("Szybko pracuję", "I work quickly", "Szybko" to "quickly", "pracuję" to "I work"),
                ex("Szybko jem", "I eat quickly", "Szybko" to "quickly", "jem" to "I eat"),
                ex("Szybko piję wodę", "I drink water quickly", "Szybko" to "quickly", "piję" to "I drink", "wodę" to "water"),
                ex("Szybko kupuję chleb", "I buy bread quickly", "Szybko" to "quickly", "kupuję" to "I buy", "chleb" to "bread"),
                ex("Szybko uczę się", "I learn quickly", "Szybko" to "quickly", "uczę" to "I learn", "się" to "myself")
            )
            "trochę" -> listOf(
                ex("Trochę rozumiem", "I understand a little", "Trochę" to "a little", "rozumiem" to "I understand"),
                ex("Trochę mówię po polsku", "I speak a little Polish", "Trochę" to "a little", "mówię" to "I speak", "po" to "in", "polsku" to "Polish"),
                ex("Trochę chcę spać", "I want to sleep a little", "Trochę" to "a little", "chcę" to "I want", "spać" to "to sleep"),
                ex("Trochę pracuję", "I am working a little", "Trochę" to "a little", "pracuję" to "I work"),
                ex("Trochę czytam", "I am reading a little", "Trochę" to "a little", "czytam" to "I read"),
                ex("Trochę piję wodę", "I am drinking a little water", "Trochę" to "a little", "piję" to "I drink", "wodę" to "water"),
                ex("Trochę lubię kawę", "I like coffee a little", "Trochę" to "a little", "lubię" to "I like", "kawę" to "coffee"),
                ex("Trochę pamiętam", "I remember a little", "Trochę" to "a little", "pamiętam" to "I remember"),
                ex("Trochę się boję", "I am a little afraid", "Trochę" to "a little", "się" to "myself", "boję" to "I fear"),
                ex("Trochę jestem zmęczony", "I am a little tired", "Trochę" to "a little", "jestem" to "I am", "zmęczony" to "tired")
            )
            "tutaj" -> listOf(
                ex("Tutaj mieszkam", "I live here", "Tutaj" to "here", "mieszkam" to "I live"),
                ex("Tutaj pracuję", "I work here", "Tutaj" to "here", "pracuję" to "I work"),
                ex("Tutaj jestem", "I am here", "Tutaj" to "here", "jestem" to "I am"),
                ex("Tutaj czekam", "I am waiting here", "Tutaj" to "here", "czekam" to "I wait"),
                ex("Tutaj jem", "I eat here", "Tutaj" to "here", "jem" to "I eat"),
                ex("Tutaj piję kawę", "I drink coffee here", "Tutaj" to "here", "piję" to "I drink", "kawę" to "coffee"),
                ex("Tutaj czytam książkę", "I read a book here", "Tutaj" to "here", "czytam" to "I read", "książkę" to "book"),
                ex("Tutaj kupuję chleb", "I buy bread here", "Tutaj" to "here", "kupuję" to "I buy", "chleb" to "bread"),
                ex("Tutaj śpię", "I sleep here", "Tutaj" to "here", "śpię" to "I sleep"),
                ex("Tutaj mówię po polsku", "I speak Polish here", "Tutaj" to "here", "mówię" to "I speak", "po" to "in", "polsku" to "Polish")
            )
            "razem" -> listOf(
                ex("Razem idziemy", "We are going together", "Razem" to "together", "idziemy" to "we go"),
                ex("Razem pracujemy", "We work together", "Razem" to "together", "pracujemy" to "we work"),
                ex("Razem czytamy", "We read together", "Razem" to "together", "czytamy" to "we read"),
                ex("Razem jemy", "We eat together", "Razem" to "together", "jemy" to "we eat"),
                ex("Razem pijemy kawę", "We drink coffee together", "Razem" to "together", "pijemy" to "we drink", "kawę" to "coffee"),
                ex("Razem wracamy", "We return together", "Razem" to "together", "wracamy" to "we return"),
                ex("Razem uczymy się", "We learn together", "Razem" to "together", "uczymy" to "we learn", "się" to "ourselves"),
                ex("Razem gotujemy", "We cook together", "Razem" to "together", "gotujemy" to "we cook"),
                ex("Razem kupujemy chleb", "We buy bread together", "Razem" to "together", "kupujemy" to "we buy", "chleb" to "bread"),
                ex("Razem oglądamy film", "We watch a movie together", "Razem" to "together", "oglądamy" to "we watch", "film" to "movie")
            )
            "dzisiaj" -> listOf(
                ex("Dzisiaj pracuję", "I am working today", "Dzisiaj" to "today", "pracuję" to "I work"),
                ex("Dzisiaj idę do szkoły", "I am going to school today", "Dzisiaj" to "today", "idę" to "I go", "do" to "to", "szkoły" to "school"),
                ex("Dzisiaj czytam książkę", "I am reading a book today", "Dzisiaj" to "today", "czytam" to "I read", "książkę" to "book"),
                ex("Dzisiaj piję kawę", "I am drinking coffee today", "Dzisiaj" to "today", "piję" to "I drink", "kawę" to "coffee"),
                ex("Dzisiaj kupuję chleb", "I am buying bread today", "Dzisiaj" to "today", "kupuję" to "I buy", "chleb" to "bread"),
                ex("Dzisiaj wracam do domu", "I am returning home today", "Dzisiaj" to "today", "wracam" to "I return", "do" to "to", "domu" to "home"),
                ex("Dzisiaj jem obiad", "I am eating lunch today", "Dzisiaj" to "today", "jem" to "I eat", "obiad" to "lunch"),
                ex("Dzisiaj śpię w domu", "I sleep at home today", "Dzisiaj" to "today", "śpię" to "I sleep", "w" to "in", "domu" to "home"),
                ex("Dzisiaj mówię po polsku", "I speak Polish today", "Dzisiaj" to "today", "mówię" to "I speak", "po" to "in", "polsku" to "Polish"),
                ex("Dzisiaj uczę się", "I am studying today", "Dzisiaj" to "today", "uczę" to "I learn", "się" to "myself")
            )
            "jutro" -> listOf(
                ex("Jutro idę do szkoły", "I am going to school tomorrow", "Jutro" to "tomorrow", "idę" to "I go", "do" to "to", "szkoły" to "school"),
                ex("Jutro pracuję", "I am working tomorrow", "Jutro" to "tomorrow", "pracuję" to "I work"),
                ex("Jutro wracam do domu", "I am returning home tomorrow", "Jutro" to "tomorrow", "wracam" to "I return", "do" to "to", "domu" to "home"),
                ex("Jutro kupuję kawę", "I am buying coffee tomorrow", "Jutro" to "tomorrow", "kupuję" to "I buy", "kawę" to "coffee"),
                ex("Jutro czytam książkę", "I am reading a book tomorrow", "Jutro" to "tomorrow", "czytam" to "I read", "książkę" to "book"),
                ex("Jutro piję herbatę", "I am drinking tea tomorrow", "Jutro" to "tomorrow", "piję" to "I drink", "herbatę" to "tea"),
                ex("Jutro jem chleb", "I eat bread tomorrow", "Jutro" to "tomorrow", "jem" to "I eat", "chleb" to "bread"),
                ex("Jutro dzwonię", "I am calling tomorrow", "Jutro" to "tomorrow", "dzwonię" to "I call"),
                ex("Jutro pomagam", "I am helping tomorrow", "Jutro" to "tomorrow", "pomagam" to "I help"),
                ex("Jutro uczę się", "I am studying tomorrow", "Jutro" to "tomorrow", "uczę" to "I learn", "się" to "myself")
            )
            "teraz" -> listOf(
                ex("Teraz czytam książkę", "I am reading a book now", "Teraz" to "now", "czytam" to "I read", "książkę" to "book"),
                ex("Teraz piję kawę", "I am drinking coffee now", "Teraz" to "now", "piję" to "I drink", "kawę" to "coffee"),
                ex("Teraz pracuję", "I am working now", "Teraz" to "now", "pracuję" to "I work"),
                ex("Teraz idę do domu", "I am going home now", "Teraz" to "now", "idę" to "I go", "do" to "to", "domu" to "home"),
                ex("Teraz jem chleb", "I am eating bread now", "Teraz" to "now", "jem" to "I eat", "chleb" to "bread"),
                ex("Teraz mówię po polsku", "I am speaking Polish now", "Teraz" to "now", "mówię" to "I speak", "po" to "in", "polsku" to "Polish"),
                ex("Teraz uczę się", "I am studying now", "Teraz" to "now", "uczę" to "I learn", "się" to "myself"),
                ex("Teraz wracam", "I am returning now", "Teraz" to "now", "wracam" to "I return"),
                ex("Teraz słucham", "I am listening now", "Teraz" to "now", "słucham" to "I listen"),
                ex("Teraz czekam", "I am waiting now", "Teraz" to "now", "czekam" to "I wait")
            )
            "chętnie" -> listOf(
                ex("Chętnie pomogę", "I will gladly help", "Chętnie" to "gladly", "pomogę" to "I will help"),
                ex("Chętnie pójdę", "I will gladly go", "Chętnie" to "gladly", "pójdę" to "I will go"),
                ex("Chętnie wypiję kawę", "I will gladly drink coffee", "Chętnie" to "gladly", "wypiję" to "I will drink", "kawę" to "coffee"),
                ex("Chętnie przeczytam książkę", "I will gladly read a book", "Chętnie" to "gladly", "przeczytam" to "I will read", "książkę" to "book"),
                ex("Chętnie zjem chleb", "I will gladly eat bread", "Chętnie" to "gladly", "zjem" to "I will eat", "chleb" to "bread"),
                ex("Chętnie kupię herbatę", "I will gladly buy tea", "Chętnie" to "gladly", "kupię" to "I will buy", "herbatę" to "tea"),
                ex("Chętnie porozmawiam", "I will gladly talk", "Chętnie" to "gladly", "porozmawiam" to "I will talk"),
                ex("Chętnie posłucham", "I will gladly listen", "Chętnie" to "gladly", "posłucham" to "I will listen"),
                ex("Chętnie zaczekam", "I will gladly wait", "Chętnie" to "gladly", "zaczekam" to "I will wait"),
                ex("Chętnie się nauczę", "I will gladly learn", "Chętnie" to "gladly", "się" to "myself", "nauczę" to "I will learn")
            )
            else -> listOf(
                ex(
                    "Używam słowa ${adverb.lemma}",
                    "I use the word $gloss",
                    "Używam" to "I use",
                    "słowa" to "word",
                    adverb.lemma to gloss
                )
            )
        }
    }

    private fun startQueue(items: List<SentenceExample>, quiz: Boolean) {
        val slowFirst = app.practicePrefs.slowFirst()
        val slowPlVoice = app.targetSlowVoice()
        player.start(
            total = items.size,
            publishParked = { i ->
                val s = items[i]
                NowVoicingBus.publish(
                    NowVoicing(
                        en = s.en, pl = s.pl, literal = s.literal, lang = "pause",
                        position = "${i + 1}/${items.size}", words = s.words,
                        plHidden = quiz, quizMode = quiz
                    )
                )
            },
            prefetchItem = { i ->
                val s = items[i]
                app.ensureCachedAudio(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                if (slowFirst && !quiz) app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, slowPlVoice)
            },
        ) { i ->
            val s = items[i]
            val pos = "${i + 1}/${items.size}"
            fun pub(lang: String, plHidden: Boolean = false) {
                NowVoicingBus.publish(
                    NowVoicing(
                        en = s.en, pl = s.pl, literal = s.literal,
                        lang = lang, position = pos, words = s.words,
                        plHidden = plHidden, quizMode = quiz
                    )
                )
            }
            if (quiz) {
                pub("en", plHidden = true)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                pub("pause", plHidden = true)
                reveal(2000L)
                pub("pause", plHidden = false)
                reveal(2000L)
                pub("pl", plHidden = false)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else if (slowFirst) {
                pub("en")
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                pub("pl-slow")
                say(s.pl, app.targetAudioVoice().locale, slowPlVoice)
                pub("pl")
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else {
                pub("en")
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                pub("pl")
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            }
            if (!quiz && i < items.size - 1) reveal(500L)
        }
    }
}

@Composable
private fun rememberAdverbsScreenState(app: LangbangApplication): AdverbsScreenState {
    val scope = rememberCoroutineScope()
    return remember { AdverbsScreenState(app, scope) }
}

@Composable
fun AdverbsScreen(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    val lesson = remember { app.lessonRepo.lesson4() }
    val scope = rememberCoroutineScope()
    val state = rememberAdverbsScreenState(app)
    val activeNowVoicing by NowVoicingBus.state.collectAsState()
    var generateAllBusy by remember { mutableStateOf(false) }
    var generateAllProgress by remember { mutableStateOf<String?>(null) }
    var generateAllError by remember { mutableStateOf<String?>(null) }

    if (state.selected == null ||
        lesson.adverbs.none { it.lemma == state.selected?.lemma }
    ) {
        state.select(lesson.adverbs.firstOrNull())
    }

    LaunchedEffect(activeNowVoicing?.pl, activeNowVoicing?.words, lesson.adverbs) {
        nowVoicingAdverb(activeNowVoicing, lesson.adverbs)?.let { state.select(it) }
    }

    val generateAll: () -> Unit = {
        if (!generateAllBusy) {
            generateAllBusy = true
            generateAllError = null
            scope.launch {
                val errors = mutableListOf<String>()
                try {
                    lesson.adverbs.forEachIndexed { i, a ->
                        generateAllProgress =
                            "Gemini ${i + 1}/${lesson.adverbs.size} · ${a.lemma}"
                        val existing = app.lessonRepo.adverbSentencesFor(a.lemma)
                        if (existing.isNotEmpty()) return@forEachIndexed
                        app.gemini.generateAdverbSentences(a)
                            .onSuccess { app.lessonRepo.saveAdverbSentences(a.lemma, it) }
                            .onFailure { errors += "${a.lemma}: ${it.message}" }
                    }
                    generateAllProgress = "Kicking audio prefetch…"
                    kickPrefetchWorker(app)
                } finally {
                    if (errors.isNotEmpty()) {
                        generateAllError =
                            "${errors.size} adverb(s) failed: ${errors.first()}"
                    }
                    generateAllProgress = null
                    generateAllBusy = false
                }
            }
        }
    }
    state.ensureCheckedDefaults(lesson.adverbs.map { it.lemma })

    // Left list flush to the top; right column = Now Voicing band, controls, examples.
    Row(modifier = Modifier.fillMaxSize()) {
        AdverbList(
            adverbs = lesson.adverbs,
            selected = state.selected,
            onSelect = { state.select(it) },
            checkedLemmas = state.checkedLemmas,
            onToggleAdverb = { lemma, checked -> state.toggleChecked(lemma, checked) },
            onToggleAll = { checked ->
                state.setAllChecked(lesson.adverbs.map { it.lemma }, checked)
            },
            randomOrder = state.randomOrder,
            onRandomOrderChange = { state.updateRandomOrder(it) },
            enabled = !state.playing,
            modifier = Modifier
                .width(269.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            AdvControlsBar(
                onGenerateAll = generateAll,
                generateAllBusy = generateAllBusy,
                generateAllProgress = generateAllProgress,
                state = state,
                adverbs = lesson.adverbs
            )
            generateAllError?.let {
                Text(
                    "Generate-all error: $it",
                    fontSize = 11.sp, color = Color.Red,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.selected?.let {
                    AdverbSentences(
                        app = app,
                        adv = it,
                        state = state,
                        adverbs = lesson.adverbs
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvControlsBar(
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean,
    generateAllProgress: String?,
    state: AdverbsScreenState,
    adverbs: List<AdverbEntry>
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.selected != null) {
                    AdvExamplesControls(state = state, adverbs = adverbs)
                }
            }
            generateAllProgress?.let {
                Text(
                    "Generating · $it",
                    fontSize = 10.sp,
                    color = LbColors.Label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)
                )
            }
        }
    }
}


// Emits its controls directly into the caller's FlowRow (no own layout wrapper) so chips
// and buttons wrap together as one band.
@Composable
private fun AdvExamplesControls(
    state: AdverbsScreenState,
    adverbs: List<AdverbEntry>
) {
    val playCount = state.playCount(adverbs)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.playing) {
                LbButton.Stop("Stop", onClick = { state.stop() }, icon = Icons.Default.Stop)
            } else {
                LbButton.Audio(
                    "Play",
                    onClick = { state.playAll(adverbs, quiz = false) },
                    count = playCount,
                    enabled = playCount > 0
                )
                WordPlayLimitControl(
                    limitText = state.playLimitText,
                    onLimitTextChange = { state.updatePlayLimitText(it) },
                    leadingLabel = "groups of",
                    trailingLabel = null
                )
            }
            if (state.sentences.isNotEmpty() && !state.playing) {
                LbButton.Ghost("Sent. quiz", onClick = { state.playAll(adverbs, quiz = true) }, icon = Icons.Default.PlayArrow)
            }
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                )
            } else if (state.sentences.isEmpty()) {
                LbButton.Ghost(
                    label = "Generate examples",
                    icon = Icons.Default.Add,
                    onClick = { state.generate() }
                )
            }
        }
    }
}

// AdvCacheBadge moved to AppHeader (single source) — removed duplicate.

@Composable
private fun AdverbList(
    adverbs: List<AdverbEntry>,
    selected: AdverbEntry?,
    onSelect: (AdverbEntry) -> Unit,
    checkedLemmas: Set<String>,
    onToggleAdverb: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    randomOrder: Boolean,
    onRandomOrderChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val allLemmas = adverbs.map { it.lemma }
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        item(key = "all-adverbs-master") {
            WordListPlaybackHeader(
                allChecked = allLemmas.isNotEmpty() && checkedLemmas.containsAll(allLemmas),
                onAllCheckedChange = onToggleAll,
                random = randomOrder,
                onRandomChange = onRandomOrderChange,
                enabled = enabled
            )
        }
        itemsIndexed(adverbs, key = { _, a -> "adv-${a.lemma}" }) { index, a ->
            val isSel = a == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SubtleCheckbox(
                    checked = a.lemma in checkedLemmas,
                    onCheckedChange = { onToggleAdverb(a.lemma, it) },
                    enabled = enabled,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                CompactLessonListCard(
                    selected = isSel,
                    onClick = { onSelect(a) },
                    modifier = Modifier.weight(1f),
                    alternate = index % 2 == 1
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            a.lemma,
                            color = if (isSel) Color.White else LbColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        DelayedEnglishTranslation(
                            text = a.en,
                            color = if (isSel) Color.White.copy(alpha = 0.85f)
                            else LbColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdverbSentences(
    app: LangbangApplication,
    adv: AdverbEntry,
    state: AdverbsScreenState,
    adverbs: List<AdverbEntry>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(adv.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(12.dp))
            DelayedEnglishTranslation(text = adv.en, fontSize = 14.sp, color = LbColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            SelectionNavButtons(
                items = adverbs,
                selected = adv,
                onSelect = { state.select(it) },
                previousContentDescription = "Previous adverb",
                nextContentDescription = "Next adverb"
            )
        }
        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate above to make 20 short sentences using " +
                    "this adverb with common verbs and beginner vocabulary.",
                fontSize = 12.sp,
                color = LbColors.TextMuted
            )
        } else {
            state.sentences.forEachIndexed { i, s ->
                AdvSentenceRow(
                    sentence = s,
                    highlighted = i == state.playingIndex,
                    onPlay = { playSentenceAdv(app, s) }
                )
            }
        }
        state.error?.let {
            Text("Error: $it", color = Color.Red, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AdvSentenceRow(
    sentence: SentenceExample,
    highlighted: Boolean = false,
    onPlay: () -> Unit
) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) LbColors.PrimarySoft else LbColors.SurfaceRaised
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                DelayedEnglishTranslation(
                    text = sentence.en,
                    fontSize = 12.sp,
                    color = LbColors.TextMuted
                )
                com.sponic.langbang.ui.common.WordAlignedPolish(
                    sentence = sentence,
                    plFontSize = 16.sp,
                    plFontWeight = FontWeight.Bold,
                    glossFontSize = 10.sp
                )
            }
        }
    }
}

// ── Shared helpers (renamed to avoid clash with AdjectivesScreen private fns) ────

private fun playFormAdv(app: LangbangApplication, form: String) {
    if (form.isEmpty()) return
    val f = app.audioCache.fileFor(
        app.targetAudioVoice().locale, app.targetAudioVoice().voice, form
    )
    app.audioPlayer.play(f)
}

private fun playSentenceAdv(app: LangbangApplication, sentence: SentenceExample) {
    playFormAdv(app, sentence.pl)
}
