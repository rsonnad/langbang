package com.sponic.langbang

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.PronounFilterStore
import com.sponic.langbang.data.RandomConfigStore
import com.sponic.langbang.domain.AudioCache
import com.sponic.langbang.domain.AudioPlayer
import com.sponic.langbang.domain.BackupService
import com.sponic.langbang.domain.NetworkMonitor
import com.sponic.langbang.domain.PrefetchService
import com.sponic.langbang.domain.PrefetchWorker
import com.sponic.langbang.domain.R2AudioDownloader
import com.sponic.langbang.domain.UsageTracker
import com.sponic.langbang.integrations.AzurePronunciationClient
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.integrations.GeminiClient

class LangbangApplication : Application() {

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
    lateinit var r2Audio: R2AudioDownloader
        private set

    override fun onCreate() {
        super.onCreate()
        lessonRepo = LessonRepository(this)
        pronounFilter = PronounFilterStore(this)
        randomConfig = RandomConfigStore(this)
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
    }
}
