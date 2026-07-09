import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val versionFileContent = providers.fileContents(
    project.layout.projectDirectory.file("version.properties")
).asText.orElse("versionName=0.1.0\nversionCode=1\n").get()
val g2VersionName =
    Regex("versionName=(.+)").find(versionFileContent)?.groupValues?.get(1)?.trim()
        ?: "0.1.0"
val g2VersionCode =
    Regex("versionCode=(\\d+)").find(versionFileContent)?.groupValues?.get(1)?.toIntOrNull()
        ?: 1

fun prop(name: String, default: String = ""): String =
    localProps.getProperty("LANGBANGTRANS_$name")
        ?: default

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.sponic.langbangtrans"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sponic.langbangtrans"
        minSdk = 33
        targetSdk = 36
        versionCode = g2VersionCode
        versionName = g2VersionName

        buildConfigField("String", "GEMINI_API_KEY", quoted(prop("GEMINI_API_KEY")))
        buildConfigField("String", "GEMINI_PROXY_TOKEN", quoted(prop("GEMINI_PROXY_TOKEN")))
        buildConfigField(
            "String",
            "GEMINI_MODEL",
            quoted(prop("GEMINI_MODEL", "gemini-3.5-live-translate-preview"))
        )
        buildConfigField(
            "String",
            "GEMINI_WS_ENDPOINT",
            quoted(
                prop(
                    "GEMINI_WS_ENDPOINT",
                    "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
                )
            )
        )
        buildConfigField(
            "String",
            "GEMINI_TEXT_ENDPOINT",
            quoted(prop("GEMINI_TEXT_ENDPOINT", "https://langbangml-api.langbangml.workers.dev/v1/gemini/g2-translate"))
        )
        buildConfigField("String", "TARGET_LANGUAGE_CODE", quoted(prop("TARGET_LANGUAGE_CODE", "en")))
        buildConfigField("String", "RESPONSE_MODALITIES", quoted(prop("RESPONSE_MODALITIES", "AUDIO")))
        buildConfigField("Boolean", "PLAY_OUTPUT_AUDIO", prop("PLAY_OUTPUT_AUDIO", "true"))
        buildConfigField("int", "CAPTURE_SAMPLE_RATE", prop("CAPTURE_SAMPLE_RATE", "16000"))
        buildConfigField("int", "CAPTURE_FRAME_MS", prop("CAPTURE_FRAME_MS", "20"))
        buildConfigField("int", "GEMINI_AUDIO_CHUNK_MS", prop("GEMINI_AUDIO_CHUNK_MS", "100"))
        buildConfigField("int", "OUTPUT_SAMPLE_RATE", prop("OUTPUT_SAMPLE_RATE", "24000"))

        buildConfigField("Boolean", "G2_DRY_RUN", prop("G2_DRY_RUN", "true"))
        buildConfigField("String", "G2_NAME_REGEX", quoted(prop("G2_NAME_REGEX", "Even G2|G2")))
        buildConfigField("String", "G2_SERVICE_UUID", quoted(prop("G2_SERVICE_UUID")))
        buildConfigField("String", "G2_WRITE_CHAR_UUID", quoted(prop("G2_WRITE_CHAR_UUID")))
        buildConfigField("String", "G2_NOTIFY_CHAR_UUID", quoted(prop("G2_NOTIFY_CHAR_UUID")))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
