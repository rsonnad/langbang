# LangBangML ProGuard / R8 keep rules
# Keep these until full validation for release minify (see AND-7b).
# Start conservative to avoid breaking Azure Speech SDK, Ktor (none here), kotlinx-serialization, Compose, WorkManager, credentials.

# Kotlinx serialization
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used with serialization
-keep @kotlinx.serialization.Serializable class ** { *; }

# Azure Cognitive Services Speech SDK (native bits + reflection)
-keep class com.microsoft.cognitiveservices.speech.** { *; }
-keep class com.microsoft.cognitiveservices.speech.internal.** { *; }
-dontwarn com.microsoft.cognitiveservices.speech.**

# Media / audio
-keep class android.media.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Credentials / Google ID
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Misc app classes referenced by name or reflection
-keep class com.sponic.langbang.** { *; }

# Keep BuildConfig fields we rely on at runtime
-keep class com.sponic.langbang.BuildConfig { *; }

# JSON parsing / org.json used in receivers
-keep class org.json.** { *; }

# Prevent stripping of entry points
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends androidx.work.ListenableWorker

# Compose / UI (usually kept by AGP defaults + compose rules)
# Add specific if shrink breaks a screen.

# Logging / debug only paths are often kept via -dontnote or rules above.
