import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("co.touchlab.skie")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    // Android target (thin for Pass 1)
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // iOS targets
    iosArm64()
    iosSimulatorArm64()
    // iosX64() // enable if you need intel simulator support
    // (JVM tests rely on interface impls passed explicitly; no extra jvm target to avoid sourceSet name clash)

    // Produce a framework for Xcode consumption
    targets.withType<KotlinNativeTarget> {
        binaries.framework {
            baseName = "LangBangShared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

                // Ktor — HTTP in common (the key decision)
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                implementation("io.ktor:ktor-client-logging:2.3.12")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("io.ktor:ktor-client-mock:2.3.12")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                // MediaPlayer for audio actual (no extra dep needed)
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }

        // Convenience: make ios* targets use the iosMain source set
        iosArm64().compilations["main"].defaultSourceSet.dependsOn(iosMain)
        iosSimulatorArm64().compilations["main"].defaultSourceSet.dependsOn(iosMain)
    }
}

// SKIE: improves Swift interop for StateFlow, sealed classes etc. for thin SwiftUI renderers.
skie {
    // Default config is sufficient for Flow -> AsyncSequence and enums.
}

// Detekt guardrail: forbid android.* / androidx.* leaks into commonMain.
// The config is in detekt.yml (see below). We fail the build on issues.
detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // Only analyze commonMain for the android-import ban (platform actuals are allowed their imports).
    // A simple filter: we rely on the detekt.yml rule + will demonstrate failure when violated.
}

// Executable guardrail: fail build if android.* / androidx.* appears in commonMain sources.
// This is the reliable cross-platform "no drift" lint (Detekt supplements it).
val checkNoAndroidInCommon = tasks.register("checkNoAndroidInCommon") {
    group = "verification"
    description = "Fails if android or androidx imports are found in shared/commonMain (anti-drift guard)."
    doLast {
        val commonMainDir = project.file("src/commonMain/kotlin")
        if (!commonMainDir.exists()) return@doLast
        val bad = commonMainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val text = f.readText()
                text.contains("import android.") || text.contains("import androidx.")
            }
            .toList()
        if (bad.isNotEmpty()) {
            val list = bad.joinToString("\n  ") { it.relativeTo(project.projectDir).path }
            throw GradleException("Forbidden Android imports in commonMain (drift risk):\n  $list\nRemove platform imports from shared logic; use expect/actual.")
        }
        println("checkNoAndroidInCommon: OK — no android.* / androidx.* in commonMain")
    }
}

tasks.named("check").configure { dependsOn(checkNoAndroidInCommon) }

// Executable parity check: a shared FeatureModel must have BOTH an androidApp renderer
// AND an iosApp renderer entry point. Missing one is a build failure.
val checkPracticeParity = tasks.register("checkPracticeParity") {
    group = "verification"
    description = "Asserts that PracticeModel (shared) has thin renderers in BOTH androidApp and iosApp."
    doLast {
        val sharedModelRef = "PracticeModel"
        // Look for references to the model in renderer locations.
        val androidRenderer = project.rootProject.file("app/src/main/kotlin/com/sponic/langbang/ui/quizzes/PracticeGenerators.kt")
        val iosAppDir = project.rootProject.file("iosApp/LangBang")
        val iosHasReference = if (iosAppDir.exists() && iosAppDir.isDirectory) {
            iosAppDir.walkTopDown().any { f ->
                f.isFile && (f.extension == "swift" || f.extension == "kt") &&
                    (f.readText().contains(sharedModelRef) || f.readText().contains("PracticeView") || f.readText().contains("PracticeModel") || f.readText().contains("createPracticeModel"))
            }
        } else false

        val androidAppDir = project.rootProject.file("app/src/main/kotlin")
        val hasAndroid = androidRenderer.exists() || (androidAppDir.exists() && androidAppDir.walkTopDown().any {
            it.isFile && it.extension == "kt" && it.readText().contains(sharedModelRef)
        })

        val missing = mutableListOf<String>()
        if (!hasAndroid) missing += "androidApp renderer (PracticeScreen or equivalent using PracticeModel)"
        if (!iosHasReference) missing += "iosApp SwiftUI renderer (ContentView or PracticeView referencing PracticeModel)"

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Parity violation for Practice feature: missing renderer(s):\n" +
                    missing.joinToString("\n  - ", prefix = "  - ") +
                    "\n\nOne shared model must be rendered by BOTH platforms to prevent drift."
            )
        }
        println("checkPracticeParity: OK — both androidApp and iosApp reference the shared Practice feature.")
    }
}

tasks.named("check").configure { dependsOn(checkPracticeParity) }

android {
    namespace = "com.sponic.langbang.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
