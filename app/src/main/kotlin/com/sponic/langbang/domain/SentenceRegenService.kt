package com.sponic.langbang.domain

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.R2SentenceManifest
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.integrations.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.sponic.langbang.data.LbJson
import kotlinx.serialization.builtins.ListSerializer
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives sentence cache population from R2. The server-side
 * `langbang-pregen-sentences` Edge Function has already generated bundles under
 * `langbang/sentences/v{N}/...`; this service reads the manifest, downloads any
 * missing or stale bundles in parallel, and writes them into the local
 * per-type sentence stores via [LessonRepository]'s save methods. Replaces the
 * minutes-long on-device Gemini regen for canonical content — user-added words
 * still go through [GeminiClient] directly because they aren't in the manifest.
 *
 * Progress is exposed as a [StateFlow] so the always-visible banner and the
 * Settings card render the same live state.
 */
class SentenceRegenService(
    private val repo: LessonRepository
) {
    sealed interface State {
        data object Idle : State
        data class Downloading(val done: Int, val total: Int, val currentKey: String) : State
        data class Done(val downloaded: Int, val total: Int, val failures: Int) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = LbJson.lenient

    /**
     * Serializes every write into the per-type SharedPreferences-style JSON stores
     * ([VerbSentenceStore], [AdjectiveSentenceStore], [AdverbSentenceStore]). Each
     * store's `put()` does load → mutate → write; without this lock, the 6 parallel
     * downloaders race and most writes get overwritten by the next one's load+save
     * cycle. Caught by inspecting the on-device cache after a v0.1.8.73 install:
     * only ~15 of 92 downloaded bundles survived. One mutex per service instance is
     * fine because both flows (auto-launch + manual "Re-sync") share this service.
     */
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var activeJob: Job? = null

    /**
     * Kick off a background download pass. No-op while one is running so flipping
     * screens doesn't spawn duplicates. Pass [force] = true to ignore a previous
     * Done state and re-walk every bundle (used by the Settings "Refresh" path
     * after a partial failure).
     */
    fun startIfNeeded(force: Boolean = false) {
        if (activeJob?.isActive == true) return
        if (!force && _state.value is State.Done) return
        activeJob = scope.launch { runOnce() }
    }

    private suspend fun runOnce() {
        _state.value = State.Downloading(done = 0, total = 0, currentKey = "manifest")
        val manifest = try {
            fetchManifest()
        } catch (t: Throwable) {
            _state.value = State.Failed("Couldn't reach R2 manifest: ${t.message}")
            return
        }
        val needed = manifest.entries.entries
            .filter { (key, _) -> !hasFreshLocal(key) }
            .toList()
        if (needed.isEmpty()) {
            _state.value = State.Done(downloaded = 0, total = manifest.entries.size, failures = 0)
            return
        }
        val total = needed.size
        val downloaded = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)
        val jobs = needed.map { (key, entry) ->
            scope.launch {
                gate.withPermit {
                    _state.value = State.Downloading(
                        done = downloaded.get() + failures.get(),
                        total = total,
                        currentKey = key
                    )
                    val ok = downloadAndStore(key, entry.url)
                    if (ok) downloaded.incrementAndGet() else failures.incrementAndGet()
                    _state.value = State.Downloading(
                        done = downloaded.get() + failures.get(),
                        total = total,
                        currentKey = key
                    )
                }
            }
        }
        jobs.forEach { it.join() }
        _state.value = State.Done(
            downloaded = downloaded.get(),
            total = total,
            failures = failures.get()
        )
    }

    private suspend fun fetchManifest(): R2SentenceManifest = withContext(Dispatchers.IO) {
        val url = "$MANIFEST_BASE/v${GeminiClient.SENTENCE_PROMPT_VERSION}/manifest.json"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("manifest HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(R2SentenceManifest.serializer(), text)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * "Fresh" today means "we have ≥1 sentence for this lemma+type". This is
     * good enough because the [LangbangApplication] migration wipes the local
     * stores on every `SENTENCE_PROMPT_VERSION` bump — so after a prompt change
     * the locals are empty and every bundle gets pulled fresh. Within a single
     * prompt version, partial cache hits are a feature: retries after a network
     * failure only re-download the missing keys instead of starting over.
     */
    private fun hasFreshLocal(key: String): Boolean {
        val parsed = parseKey(key) ?: return false
        return when (parsed.type) {
            // Cache-only checks — must NOT see the bundled `verb-past-sentences-pregen.json`
            // fallback, otherwise every past bundle download gets skipped on first install.
            BundleType.VERB_PRESENT ->
                repo.cachedVerbSentencesFor(parsed.lemma, VerbSentenceStore.TENSE_PRESENT).isNotEmpty()
            BundleType.VERB_PAST ->
                repo.cachedVerbSentencesFor(parsed.lemma, VerbSentenceStore.TENSE_PAST).isNotEmpty()
            BundleType.ADJECTIVE -> repo.adjectiveSentencesFor(parsed.lemma).isNotEmpty()
            BundleType.ADVERB -> repo.adverbSentencesFor(parsed.lemma).isNotEmpty()
            BundleType.NOUN -> repo.nounSentencesFor(parsed.lemma).isNotEmpty()
        }
    }

    private suspend fun downloadAndStore(key: String, url: String): Boolean =
        withContext(Dispatchers.IO) {
            val parsed = parseKey(key) ?: return@withContext false
            val text = try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 20000
                    requestMethod = "GET"
                }
                try {
                    if (conn.responseCode !in 200..299) return@withContext false
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Throwable) {
                return@withContext false
            }
            val sentences = try {
                json.decodeFromString(ListSerializer(SentenceExample.serializer()), text)
            } catch (_: Throwable) {
                return@withContext false
            }
            if (sentences.isEmpty()) return@withContext false
            // Per-store JSON files do load-then-write; multiple in-flight writes
            // would clobber each other. Serialize the save phase only — downloads
            // stay parallel, just the file mutation is mutex-guarded.
            saveMutex.withLock {
                when (parsed.type) {
                    BundleType.VERB_PRESENT ->
                        repo.saveSentences(parsed.lemma, sentences, VerbSentenceStore.TENSE_PRESENT)
                    BundleType.VERB_PAST ->
                        repo.saveSentences(parsed.lemma, sentences, VerbSentenceStore.TENSE_PAST)
                    BundleType.ADJECTIVE -> repo.saveAdjectiveSentences(parsed.lemma, sentences)
                    BundleType.ADVERB -> repo.saveAdverbSentences(parsed.lemma, sentences)
                    BundleType.NOUN -> repo.saveNounSentences(parsed.lemma, sentences)
                }
            }
            true
        }

    private data class ParsedKey(val type: BundleType, val lemma: String)
    private enum class BundleType { VERB_PRESENT, VERB_PAST, ADJECTIVE, ADVERB, NOUN }

    private fun parseKey(key: String): ParsedKey? {
        val parts = key.removeSuffix(".json").split("/")
        if (parts.size != 2) return null
        val (folder, lemmaWithTense) = parts
        return when (folder) {
            "verbs" -> when {
                lemmaWithTense.endsWith("-present") ->
                    ParsedKey(BundleType.VERB_PRESENT, lemmaWithTense.removeSuffix("-present"))
                lemmaWithTense.endsWith("-past") ->
                    ParsedKey(BundleType.VERB_PAST, lemmaWithTense.removeSuffix("-past"))
                else -> null
            }
            "adjectives" -> ParsedKey(BundleType.ADJECTIVE, lemmaWithTense)
            "adverbs" -> ParsedKey(BundleType.ADVERB, lemmaWithTense)
            "nouns" -> ParsedKey(BundleType.NOUN, lemmaWithTense)
            else -> null
        }
    }

    companion object {
        private const val MANIFEST_BASE =
            "https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/sentences"

        // Six concurrent downloads is plenty for ~120 sub-10KB JSON bundles —
        // wall time is dominated by latency, not throughput.
        private const val MAX_PARALLEL_DOWNLOADS = 6
    }
}
