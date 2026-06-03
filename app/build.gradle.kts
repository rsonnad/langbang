import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Single source of truth for the app version: root version.properties.
//   versionName  → Android manifest semver prefix ("0.1.8"), not the app-visible tag.
//   buildNumber  → auto-incremented on every assemble/bundle/install so versionCode
//                  is unique. App-visible labels use v{buildNumber} only.
// providers.fileContents() declares the file as a config-cache input — without it,
// re-runs of the same gradle command would replay the cached values.
val versionPropsFile = rootProject.file("version.properties")
val versionFileContent = providers.fileContents(
    rootProject.layout.projectDirectory.file("version.properties")
).asText.orElse("versionName=0.0.0\nbuildNumber=0\n").get()
val baseVersionName =
    Regex("versionName=(.+)").find(versionFileContent)?.groupValues?.get(1)?.trim()
        ?: "0.0.0"
val currentBuildNumber =
    Regex("buildNumber=(\\d+)").find(versionFileContent)?.groupValues?.get(1)?.toIntOrNull() ?: 0

val producesApk = !gradle.startParameter.isDryRun && gradle.startParameter.taskNames.any { name ->
    val n = name.lowercase()
    "assemble" in n || "bundle" in n || "install" in n
}
val effectiveBuildNumber = if (producesApk) currentBuildNumber + 1 else currentBuildNumber
if (producesApk) {
    versionPropsFile.writeText(
        "versionName=$baseVersionName\nbuildNumber=$effectiveBuildNumber\n"
    )
}
// Android manifest versionName keeps the semver prefix for OS/package metadata.
// In-app and publish labels use BuildConfig.BUILD_NUMBER as v{buildNumber}.
val composedVersionName = "$baseVersionName.$effectiveBuildNumber"

val buildTimestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    .apply { timeZone = TimeZone.getDefault() }
    .format(Date())

android {
    namespace = "com.sponic.langbang"
    compileSdk = 36

    // Pin the debug signing key so every machine produces an APK with the same
    // signature. Without this, AGP falls back to ~/.android/debug.keystore, which
    // is per-machine and gets regenerated — a signature change forces uninstall-
    // before-install on the tablet, wiping filesDir/audio/ and triggering a full
    // re-download of every cached mp3.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/langbang-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.sponic.langbangml"
        minSdk = 33
        targetSdk = 36
        versionCode = effectiveBuildNumber
        versionName = composedVersionName

        buildConfigField("int", "BUILD_NUMBER", "$effectiveBuildNumber")
        buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")

        buildConfigField(
            "String",
            "AZURE_SPEECH_KEY",
            "\"${localProps.getProperty("AZURE_SPEECH_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "AZURE_SPEECH_REGION",
            "\"${localProps.getProperty("AZURE_SPEECH_REGION", "eastus")}\""
        )
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProps.getProperty("GEMINI_API_KEY", "")}\""
        )

        buildConfigField(
            "String",
            "LANGBANGML_API_BASE",
            "\"${localProps.getProperty("LANGBANGML_API_BASE", "https://langbangml-api.langbangml.workers.dev")}\""
        )
        buildConfigField(
            "String",
            "LANGBANGML_INSTANCE_ID",
            "\"${localProps.getProperty("LANGBANGML_INSTANCE_ID", "langbangml-en-pl")}\""
        )
        buildConfigField(
            "String",
            "LANGBANGML_UPDATE_MANIFEST_URL",
            "\"${localProps.getProperty("LANGBANGML_UPDATE_MANIFEST_URL", "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json")}\""
        )
    }

    flavorDimensions += "direction"
    productFlavors {
        create("enPl") {
            dimension = "direction"
            applicationIdSuffix = ".enpl"
            resValue("string", "app_name", "LangBang EN-PL")
            buildConfigField("String", "LANGBANGML_INSTANCE_ID", "\"langbangml-en-pl\"")
            buildConfigField(
                "String",
                "LANGBANGML_UPDATE_MANIFEST_URL",
                "\"https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json\""
            )
        }
        create("plEn") {
            dimension = "direction"
            applicationIdSuffix = ".plen"
            resValue("string", "app_name", "LangBang PL-EN")
            buildConfigField("String", "LANGBANGML_INSTANCE_ID", "\"langbangml-pl-en\"")
            buildConfigField(
                "String",
                "LANGBANGML_UPDATE_MANIFEST_URL",
                "\"https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/latest.json\""
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Azure Speech SDK (TTS + STT + Pronunciation Assessment)
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.42.0")

    // SFTP client for backup uploads to ALPUCA. mwiede fork = actively
    // maintained drop-in for the abandoned com.jcraft:jsch.
    implementation("com.github.mwiede:jsch:0.2.17")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
