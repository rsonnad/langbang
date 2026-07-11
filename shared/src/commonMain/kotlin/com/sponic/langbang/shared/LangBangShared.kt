package com.sponic.langbang.shared

import com.sponic.langbang.shared.cloud.LangBangApi
import com.sponic.langbang.shared.cloud.createLangBangApi
import com.sponic.langbang.shared.practice.AudioPlayer
import com.sponic.langbang.shared.practice.PracticeModel
import com.sponic.langbang.shared.practice.createAudioPlayer as createPlatformAudio
import com.sponic.langbang.shared.voicing.NowVoicingModel
import com.sponic.langbang.shared.voicing.NowVoicingPhrase
import com.sponic.langbang.shared.voicing.NowVoicingSamples

/**
 * Shared module factory with a name that does not collide with the framework.
 */
object LangBangFactory {
    fun createApi(baseUrl: String = "https://langbangml-api.langbangml.workers.dev"): LangBangApi {
        return createLangBangApi(baseUrl)
    }

    fun createPracticeModel(): PracticeModel = PracticeModel(createPlatformAudio())

    fun createNowVoicingModel(): NowVoicingModel = NowVoicingModel()

    fun nowVoicingGreeting(): NowVoicingPhrase = NowVoicingSamples.greeting()

    fun nowVoicingAppreciate(): NowVoicingPhrase = NowVoicingSamples.appreciate()

    fun nowVoicingDemoQueue(): List<NowVoicingPhrase> = listOf(
        NowVoicingSamples.greeting(),
        NowVoicingSamples.appreciate(),
    )
}
