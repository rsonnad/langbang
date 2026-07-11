package com.sponic.langbang.domain

import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.integrations.AzureTtsClient

data class StudyAudioVoice(
    val locale: String,
    val voice: String
)

fun LessonRepository.sourceAudioVoice(): StudyAudioVoice {
    val pair = cloudLanguagePair()
    return StudyAudioVoice(
        locale = pair?.sourceLocale ?: AzureTtsClient.LOCALE_EN,
        voice = pair?.sourceVoice ?: AzureTtsClient.EN_US_F
    )
}

fun LessonRepository.targetAudioVoice(): StudyAudioVoice {
    val pair = cloudLanguagePair()
    return StudyAudioVoice(
        locale = pair?.targetLocale ?: AzureTtsClient.LOCALE_PL,
        voice = pair?.targetVoice ?: AzureTtsClient.PL_PL_F
    )
}

fun LessonRepository.targetSlowVoices(): List<String> =
    cloudLanguagePair()?.targetSlowVoices.orEmpty()

fun LessonRepository.sourceLanguageLabel(): String =
    languageLabel(cloudLanguagePair()?.sourceLanguage, "English")

fun LessonRepository.targetLanguageLabel(): String =
    languageLabel(cloudLanguagePair()?.targetLanguage, "Polish")

fun LessonRepository.targetSubjectFor(personKey: String): String =
    when (targetAudioVoice().locale) {
        AzureTtsClient.LOCALE_EN -> englishSubjectFor(personKey)
        AzureTtsClient.LOCALE_JA -> japaneseSubjectFor(personKey)
        else -> audioPronoun(personKey)
    }

fun LangbangApplication.sourceAudioVoice(): StudyAudioVoice =
    lessonRepo.sourceAudioVoice()

fun LangbangApplication.targetAudioVoice(): StudyAudioVoice =
    lessonRepo.targetAudioVoice()

fun LangbangApplication.targetSlowVoice(): String {
    val target = targetAudioVoice()
    return audioPrefs.slowTargetVoice(
        configuredVoices = lessonRepo.targetSlowVoices(),
        fallbackVoice = "${target.voice}${AzureTtsClient.SLOW_SUFFIX_V2}"
    )
}

fun LangbangApplication.sourceLanguageLabel(): String =
    lessonRepo.sourceLanguageLabel()

fun LangbangApplication.targetLanguageLabel(): String =
    lessonRepo.targetLanguageLabel()

fun LangbangApplication.targetSubjectFor(personKey: String): String =
    lessonRepo.targetSubjectFor(personKey)

private fun languageLabel(code: String?, fallback: String): String =
    when (code?.lowercase()) {
        "en", "english" -> "English"
        "pl", "polish" -> "Polish"
        "ja", "japanese" -> "Japanese"
        "es", "spanish" -> "Spanish"
        else -> fallback
    }

private fun japaneseSubjectFor(personKey: String): String = when (personKey) {
    "1sg" -> "私は"
    "2sg" -> "あなたは"
    "3sg" -> "彼は"
    "1pl" -> "私たちは"
    "2pl" -> "あなたたちは"
    "3pl" -> "彼らは"
    else -> ""
}
