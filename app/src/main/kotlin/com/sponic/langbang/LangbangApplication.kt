package com.sponic.langbang

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.analytics.ProductAnalytics
import com.sponic.langbang.analytics.ProductAnalyticsClient
import com.sponic.langbang.analytics.ProductAnalyticsProfile
import com.sponic.langbang.cloud.AuthStore
import com.sponic.langbang.cloud.CloudBackendClient
import com.sponic.langbang.cloud.CloudConfigStore
import com.sponic.langbang.cloud.PhraseSyncService
import com.sponic.langbang.data.AudioPrefsStore
import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.LanguagePackStore
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class LangbangApplication : Application() {

    lateinit var cloudConfig: CloudConfigStore
        private set
    lateinit var languagePacks: LanguagePackStore
        private set
    lateinit var cloudBackend: CloudBackendClient
        private set
    lateinit var authStore: AuthStore
        private set
    lateinit var phraseSync: PhraseSyncService
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
    lateinit var analytics: ProductAnalytics
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var languagePackDownload: Job? = null
    private var languagePackDownloadInstanceId: String? = null
    private val languagePackDownloadGeneration = AtomicLong(0)
    private val cloudSyncGeneration = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) AdbWifiKeeper.enableIfGranted(this, "app start")
        cloudConfig = CloudConfigStore(this, BuildConfig.LANGBANGML_INSTANCE_ID)
        languagePacks = LanguagePackStore(this)
        if (languagePacks.needsLegacyMigration()) {
            cloudConfig.state.value.bootstrap
                ?.takeIf { cloudConfig.hasLegacyBootstrapForLanguagePackMigration() }
                ?.let { bootstrap ->
                languagePacks.adoptLegacySelection(bootstrap.instance.id, bootstrap.content.versionId)
            }
            languagePacks.completeLegacyMigration()
        }
        languagePacks.state.value.selectedInstanceId?.let { selectedInstanceId ->
            if (cloudConfig.state.value.selectedInstanceId != selectedInstanceId) {
                cloudConfig.setSelectedInstance(selectedInstanceId)
            }
        }
        cloudBackend = CloudBackendClient(apiBase = BuildConfig.LANGBANGML_API_BASE)
        authStore = AuthStore(this)
        analytics = ProductAnalytics(
            context = this,
            cloudConfig = cloudConfig,
            client = ProductAnalyticsClient(apiBase = BuildConfig.LANGBANGML_API_BASE),
            scope = appScope
        )
        lessonRepo = LessonRepository(this, cloudConfig, languagePacks)
        migrateSentenceCachesIfNeeded()
        network = NetworkMonitor(this)
        sentenceRegen = SentenceRegenService(lessonRepo, network)
        pronounFilter = PronounFilterStore(this)
        randomConfig = RandomConfigStore(this)
        practicePrefs = PracticePrefsStore(this)
        starredPhrases = StarredPhrasesStore(this)
        phraseSync = PhraseSyncService(cloudBackend, authStore, lessonRepo, starredPhrases, cloudConfig)
        audioPrefs = AudioPrefsStore(this)
        audioCache = AudioCache(this)
        audioPlayer = AudioPlayer()
        usage = UsageTracker(this)
        tts = AzureTtsClient(usage, network)
        pron = AzurePronunciationClient(this, usage, network)
        gemini = GeminiClient(usage)
        gemini.setSessionToken(authStore.state.value.sessionToken)
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
        bindAuthProfileToAnalytics()
        analytics.trackSessionStart()
        syncCloudConfig(installSelectedPack = languagePacks.state.value.selectionMade &&
            languagePacks.state.value.status != com.sponic.langbang.data.LanguagePackStatus.READY)
        if (authStore.state.value.signedIn) {
            syncUserPhrases()
        }
    }

    private fun bindAuthProfileToAnalytics() {
        appScope.launch {
            authStore.state.collect { state ->
                val user = state.user
                analytics.setProfile(
                    if (state.signedIn && user != null) {
                        ProductAnalyticsProfile(
                            profileId = user.id,
                            provider = "langbang",
                            providerSubject = user.id,
                            email = user.email,
                            displayName = user.displayName,
                            locale = Locale.getDefault().toLanguageTag(),
                            signupState = "signed_in",
                            properties = mapOf("emailVerified" to user.emailVerified.toString())
                        )
                    } else {
                        null
                    }
                )
                // Propagate session token to GeminiClient so /v1/gemini/generate calls
                // can be attributed to the signed-in user for per-user quotas/rate limits.
                gemini.setSessionToken(state.sessionToken)
            }
        }
    }

    fun syncCloudConfig(installSelectedPack: Boolean = false) {
        analytics.track(name = "cloud_sync_requested", feature = "cloud", action = "sync")
        val requestedInstanceId = cloudConfig.state.value.selectedInstanceId
        val syncGeneration = cloudSyncGeneration.incrementAndGet()
        cloudConfig.markSyncing()
        appScope.launch {
            cloudBackend.fetchInstances()
                .onSuccess { instances ->
                    if (syncGeneration == cloudSyncGeneration.get()) cloudConfig.saveInstances(instances)
                }
            cloudBackend.fetchBootstrap(requestedInstanceId).fold(
                onSuccess = { bootstrap ->
                    val selectedPack = languagePacks.state.value
                    val isCurrentSelection = !selectedPack.selectionMade ||
                        selectedPack.selectedInstanceId == bootstrap.instance.id
                    if (syncGeneration == cloudSyncGeneration.get() && isCurrentSelection) {
                        if (selectedPack.selectionMade) cloudConfig.saveBootstrap(bootstrap)
                        else cloudConfig.previewBootstrap(bootstrap)
                        lessonRepo.clearCloudBackedBaseCache()
                        val selectedPackNeedsInstall = selectedPack.selectionMade &&
                            selectedPack.selectedInstanceId == bootstrap.instance.id &&
                            !languagePacks.isReady(bootstrap.instance.id, bootstrap.content.versionId)
                        if (installSelectedPack || selectedPackNeedsInstall) installLanguagePack(bootstrap)
                        analytics.track(
                            name = "cloud_sync_succeeded",
                            feature = "cloud",
                            action = "sync",
                            properties = mapOf(
                                "contentVersionId" to (bootstrap.content.versionId ?: ""),
                                "instanceId" to bootstrap.instance.id
                            )
                        )
                    }
                },
                onFailure = { t ->
                    if (syncGeneration != cloudSyncGeneration.get()) return@fold
                    cloudConfig.saveError(t.message ?: t.javaClass.simpleName)
                    val selectedPack = languagePacks.state.value
                    if (selectedPack.selectionMade &&
                        selectedPack.status != com.sponic.langbang.data.LanguagePackStatus.READY &&
                        !(languagePackDownload?.isActive == true &&
                            languagePackDownloadInstanceId == selectedPack.selectedInstanceId)
                    ) {
                        selectedPack.selectedInstanceId?.let { instanceId ->
                            languagePacks.markFailed(
                                instanceId,
                                t.message ?: "Language pack could not be loaded."
                            )
                        }
                    }
                    analytics.track(
                        name = "cloud_sync_failed",
                        feature = "cloud",
                        action = "sync",
                        properties = mapOf("error" to (t.message ?: t.javaClass.simpleName))
                    )
                }
            )
        }
    }

    fun selectCloudInstance(instanceId: String) {
        analytics.track(
            name = "instance_selected",
            feature = "cloud",
            action = "select",
            properties = mapOf("instanceId" to instanceId)
        )
        languagePackDownloadGeneration.incrementAndGet()
        languagePackDownload?.cancel()
        languagePackDownloadInstanceId = null
        languagePacks.select(instanceId)
        cloudConfig.setSelectedInstance(instanceId)
        lessonRepo.clearCloudBackedBaseCache()
        syncCloudConfig(installSelectedPack = true)
        if (authStore.state.value.signedIn) {
            syncUserPhrases()
        }
    }

    /** Retry a selected pack after a network or audio-manifest failure. */
    fun retrySelectedLanguagePack() {
        val selected = languagePacks.state.value.selectedInstanceId ?: return
        val bootstrap = cloudConfig.state.value.bootstrap
        if (bootstrap?.instance?.id == selected) {
            installLanguagePack(bootstrap)
        } else {
            cloudConfig.setSelectedInstance(selected)
            lessonRepo.clearCloudBackedBaseCache()
            syncCloudConfig(installSelectedPack = true)
        }
    }

    private fun installLanguagePack(bootstrap: com.sponic.langbang.cloud.CloudBootstrap) {
        val instanceId = bootstrap.instance.id
        if (languagePacks.state.value.selectedInstanceId != instanceId) return
        val generation = languagePackDownloadGeneration.incrementAndGet()
        val contentVersionId = bootstrap.content.versionId
        languagePackDownload?.cancel()
        if (languagePacks.isReady(instanceId, contentVersionId)) {
            languagePackDownloadInstanceId = null
            languagePacks.markReady(instanceId, contentVersionId)
            return
        }
        languagePacks.beginDownload(instanceId, contentVersionId)
        languagePackDownloadInstanceId = instanceId
        languagePackDownload = appScope.launch {
            r2Audio.downloadAll(
                onProgress = { done, total, current ->
                    if (languagePackDownloadGeneration.get() == generation) {
                        languagePacks.updateProgress(instanceId, done, total, current)
                    }
                },
                shouldContinue = { languagePackDownloadGeneration.get() == generation }
            ).fold(
                onSuccess = { summary ->
                    if (languagePackDownloadGeneration.get() != generation) return@fold
                    languagePackDownloadInstanceId = null
                    if (summary.failed == 0) {
                        languagePacks.markReady(instanceId, contentVersionId)
                    } else {
                        languagePacks.markFailed(
                            instanceId,
                            "${summary.failed} audio files could not be downloaded."
                        )
                    }
                },
                onFailure = { error ->
                    if (languagePackDownloadGeneration.get() != generation) return@fold
                    languagePackDownloadInstanceId = null
                    languagePacks.markFailed(
                        instanceId,
                        error.message ?: "Language pack download failed."
                    )
                }
            )
        }
    }

    fun syncUserPhrases() {
        analytics.track(name = "phrase_sync_requested", feature = "profile", action = "sync")
        appScope.launch {
            phraseSync.syncNow().fold(
                onSuccess = {
                    analytics.track(
                        name = "phrase_sync_succeeded",
                        feature = "profile",
                        action = "sync",
                        properties = mapOf(
                            "groupCount" to it.groups.size.toString(),
                            "starCount" to it.starredPhrases.size.toString()
                        )
                    )
                },
                onFailure = { t ->
                    analytics.track(
                        name = "phrase_sync_failed",
                        feature = "profile",
                        action = "sync",
                        properties = mapOf("error" to (t.message ?: t.javaClass.simpleName))
                    )
                }
            )
        }
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
