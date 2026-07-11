plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("co.touchlab.skie") version "0.9.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}

tasks.register<Exec>("checkMobileParity") {
    group = "verification"
    description = "Checks the Android/shared/iOS mobile parity contract without using the web app."
    commandLine("bash", "scripts/check-mobile-parity.sh", "--static")
}
