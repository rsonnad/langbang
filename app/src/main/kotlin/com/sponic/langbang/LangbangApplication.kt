package com.sponic.langbang

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.cloud.CloudBackendClient
import com.sponic.langbang.cloud.CloudConfigStore
import com.sponic.langbang.data.AudioPrefsStore
import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.data.PronounFilterStore
import com.sponic.langbang.data.RandomConfigStore
import com.sponic.langbang.data.StarredPhrasesStore
import com.sponic.langbang.domain.AudioCache
import com.sponic.langbang.domain.AudioPlayer
import com.sponic.langbang.domain.BackupService
import com.sponic.langbang.domain.NetworkMonitor
import com.sponic.langbang.domain.PrefetchService
import com.sponic.langbang.domain.PrefetchWorker
import com.sponic.langbang.domain.R2AudioDownloader
import com.sponic.langbang.domain.UpdateChecker
import com.sponic.langbang.domain.SentenceRegenService
import com.sponic.langbang.domain.UsageTracker
import com.sponic.langbang.integrations.AzurePronunciationClient
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.integrations.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LangbangApplication : Application() {

    lateinit var cloudConfig: CloudConfigStore
        private set
    lateinit var cloudBackend: CloudBackendClient
        private set
    lateinit var lessonRepo: LessonRepository
        private set
    lateinit var audioCache: AudioCache
        private set
    lateinit var audioPlayer: AudioPlayer
        private set
    lateinit var usage: UsageTracker
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var tts: AzureTtsClient
        private set
    lateinit var pron: AzurePronunciationClient
        private set
    lateinit var gemini: GeminiClient
        private set
    lateinit var backup: BackupService
        private set
    lateinit var prefetch: PrefetchService
        private set
    lateinit var pronounFilter: PronounFilterStore
        private set
    lateinit var randomConfig: RandomConfigStore
        private set
    lateinit var practicePrefs: PracticePrefsStore
        private set
    lateinit var starredPhrases: StarredPhrasesStore
        private set
    lateinit var audioPrefs: AudioPrefsStore
        private set
    lateinit var r2Audio: R2AudioDownloader
        private set
    lateinit var sentenceRegen: SentenceRegenService
        private set
    lateinit var updateChecker: UpdateChecker
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AdbWifiKeeper.enableIfGranted(this, "app start")
        cloudConfig = CloudConfigStore(this, BuildConfig.LANGBANGML_INSTANCE_ID)
        cloudBackend = CloudBackendClient(apiBase = BuildConfig.LANGBANGML_API_BASE)
        lessonRepo = LessonRepository(this, cloudConfig)
        migrateSentenceCachesIfNeeded()
        sentenceRegen = SentenceRegenService(lessonRepo)
        pronounFilter = PronounFilterStore(this)
        randomConfig = RandomConfigStore(this)
        practicePrefs = PracticePrefsStore(this)
        starredPhrases = StarredPhrasesStore(this)
        audioPrefs = AudioPrefsStore(this)
        audioCache = AudioCache(this)
        audioPlayer = AudioPlayer()
        usage = UsageTracker(this)
        network = NetworkMonitor(this)
        tts = AzureTtsClient(usage, network)
        pron = AzurePronunciationClient(this, usage, network)
        gemini = GeminiClient(usage)
        backup = BackupService(this)
        prefetch = PrefetchService(tts, audioCache, lessonRepo)
        r2Audio = R2AudioDownloader(audioCache, lessonRepo, network)
        updateChecker = UpdateChecker(this, network)

        WorkManager.getInstance(this).enqueueUniqueWork(
            PrefetchWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PrefetchWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        // Kick the R2 sentence-bundle downloader the moment the app is alive.
        // No-ops when every bundle is already cached locally; surfaces progress
        // through the always-visible banner in LangbangApp when work is needed.
        sentenceRegen.startIfNeeded()
        syncCloudConfig()
    }

    fun syncCloudConfig() {
        cloudConfig.markSyncing()
        appScope.launch {
            cloudBackend.fetchInstances()
                .onSuccess { cloudConfig.saveInstances(it) }
            cloudBackend.fetchBootstrap(cloudConfig.state.value.selectedInstanceId).fold(
                onSuccess = { bootstrap ->
                    cloudConfig.saveBootstrap(bootstrap)
                    lessonRepo.clearCloudBackedBaseCache()
                },
                onFailure = { t ->
                    cloudConfig.saveError(t.message ?: t.javaClass.simpleName)
                }
            )
        }
    }

    fun selectCloudInstance(instanceId: String) {
        cloudConfig.setSelectedInstance(instanceId)
        lessonRepo.clearCloudBackedBaseCache()
        syncCloudConfig()
    }

    /**
     * Wipes only the per-type sentence caches whose prompts have bumped — verbs,
     * adjectives, and adverbs each have their own [GeminiClient] wipe version so
     * a tweak to (say) the adjective prompt doesn't blow away the verb cache the
     * user has already paid Gemini time for. See the wipe-version constants in
     * [GeminiClient.Companion] for the rationale and the 2026-05-28 incident.
     *
     * Legacy shim: v0.1.7.70 used a single `sentence-prompt-version` int. If
     * that key is present and ≥ the current per-type versions, seed the new
     * per-type counters in lockstep so we don't re-wipe a cache the legacy
     * migration already cleared.
     */
    private fun migrateSentenceCachesIfNeeded() {
        val prefs = getSharedPreferences("app-migrations", Context.MODE_PRIVATE)
        val legacy = prefs.getInt(KEY_LEGACY_PROMPT_VERSION, 0)
        val seedFromLegacy = legacy >= GeminiClient.SENTENCE_PROMPT_VERSION
        val storedVerb = if (seedFromLegacy) GeminiClient.VERB_WIPE_VERSION
                         else prefs.getInt(KEY_VERB_WIPE, 0)
        val storedAdj = if (seedFromLegacy) GeminiClient.ADJECTIVE_WIPE_VERSION
                        else prefs.getInt(KEY_ADJ_WIPE, 0)
        val storedAdv = if (seedFromLegacy) GeminiClient.ADVERB_WIPE_VERSION
                        else prefs.getInt(KEY_ADV_WIPE, 0)
        // Nouns are new (no legacy single-version key ever covered them), so the
        // legacy seed never applies — read the stored counter straight.
        val storedNoun = prefs.getInt(KEY_NOUN_WIPE, 0)

        if (storedVerb < GeminiClient.VERB_WIPE_VERSION) {
            lessonRepo.clearVerbSentences()
        }
        if (storedAdj < GeminiClient.ADJECTIVE_WIPE_VERSION) {
            lessonRepo.clearAdjectiveSentencesCache()
        }
        if (storedAdv < GeminiClient.ADVERB_WIPE_VERSION) {
            lessonRepo.clearAdverbSentencesCache()
        }
        if (storedNoun < GeminiClient.NOUN_WIPE_VERSION) {
            lessonRepo.clearNounSentencesCache()
        }

        prefs.edit()
            .putInt(KEY_VERB_WIPE, GeminiClient.VERB_WIPE_VERSION)
            .putInt(KEY_ADJ_WIPE, GeminiClient.ADJECTIVE_WIPE_VERSION)
            .putInt(KEY_ADV_WIPE, GeminiClient.ADVERB_WIPE_VERSION)
            .putInt(KEY_NOUN_WIPE, GeminiClient.NOUN_WIPE_VERSION)
            .apply()
    }

    companion object {
        // Legacy single-version key from v0.1.7.70 — kept for one-time seeding.
        private const val KEY_LEGACY_PROMPT_VERSION = "sentence-prompt-version"
        private const val KEY_VERB_WIPE = "verb-wipe-version"
        private const val KEY_ADJ_WIPE = "adjective-wipe-version"
        private const val KEY_ADV_WIPE = "adverb-wipe-version"
        private const val KEY_NOUN_WIPE = "noun-wipe-version"
    }
}
