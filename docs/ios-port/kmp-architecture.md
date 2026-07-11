# LangBang KMP Architecture & Migration Plan (Pass 1)

## Goals for this pass
- Preserve 100% of the existing Kotlin **domain + data + cloud logic** for reuse.
- Put SwiftUI on top for iOS.
- Produce a **compiling, runnable skeleton** with one real vertical slice (network + stubbed auth).
- Document the exact `expect/actual` surface and the big strategic choice.

---

## Strategic Recommendation (the pivotal decision)

**Recommendation: Create / keep a separate KMP repository (this LangBang-iOS checkout) rather than converting LangBangML in-place into a KMP project.**

### Tradeoffs

**Separate KMP repo (current path)**
- Pros:
  - Zero risk to the live Android app and its 61 dirty files / tablet workflows.
  - Android team can continue shipping without KMP Gradle or source-layout churn.
  - iOS port can evolve the shared module at its own pace; only copy or symlink (or git-submodule / git-subtree) the desired sources later.
  - Clean boundary: shared module starts minimal (only what iOS needs) and grows deliberately.
  - Easier to validate "does the iOS build still work?" independently.
- Cons:
  - Logic duplication risk until the two trees are reconciled.
  - When you decide to unify, you will eventually need to move sources or introduce a monorepo/submodule step.
  - Two places to apply a bugfix in the short term.

**Convert LangBangML in-place to KMP (single source tree)**
- Pros:
  - Single source of truth from day one; no drift.
  - Android build continues to consume `:shared` (or moves code into commonMain).
  - One CI, one version.properties story.
- Cons:
  - **Invasive** on the live Android product. Requires restructuring `app/src/main/kotlin` → `shared/src/commonMain` + androidMain, changing package layout, build scripts, ProGuard rules, WorkManager, MediaPlayer, CredentialManager, etc.
  - Risk of breaking tablet debug flows, publish scripts, and the many uncommitted changes.
  - Gradle version / plugin coordination between AGP 8.9 + KMP 2.0.20 must be validated carefully.
  - Harder to "try iOS" without touching the Android branch that is currently in flight.

**Verdict for Pass 1:** Separate repo is the correct choice. The human can later decide to fold the proven shared sources back into LangBangML (or adopt a monorepo) once the iOS app has real usage and the shared contracts are stable.

---

## Layering (what lives where)

### shared (commonMain) — maximum sharing
- All **pure Kotlin** domain models and business logic:
  - `data/model/*` (VerbEntry, SentenceExample, TokenPair, PhraseGroup, …)
  - `data/LessonRepository` (minus Android Context bits) + stores that are just JSON on disk
  - `cloud/CloudModels.kt`, most of `CloudBackendClient` (replace its HttpURLConnection with Ktor)
  - `domain/*` that is not platform audio: `NowVoicing`, `PlaybackController`, `EnglishConjugator`, `SentenceRegenService` (the orchestration), `UsageTracker` (thin), `NetworkMonitor` (expect actual connectivity)
  - Quiz generators (`ui/quizzes/PracticeGenerators`, `QuizGenerators`)
  - Gemini prompt constants + sentence shaping logic
  - `LbJson` (kotlinx.serialization config)
- **Ktor** HTTP client (common) — preferred over platform HTTP so auth, serialization, and error handling are identical.
- `kotlinx-serialization`, `kotlinx-coroutines-core`, `kotlinx-datetime` as needed.

### shared (androidMain)
- Android-specific implementations only when unavoidable (e.g. thin wrappers around existing Android stores if you keep dual trees for a while).
- For the separate-repo approach in Pass 1 this can be minimal or even empty.

### shared (iosMain)
- `expect/actual` implementations for iOS:
  - Audio playback (AVAudioPlayer or similar)
  - Azure Speech (iOS SDK)
  - Persistence (UserDefaults + FileManager or a small DataStore-like abstraction; or kotlinx-datastore if it gains iOS support)
  - Google Sign-In (GIDSignIn)
  - Platform context / logging / reachability

### iosApp (SwiftUI, pure Swift)
- All UI: tabs, NowVoicing panel, quizzes, lessons, settings, random player UI.
- Calls into the Kotlin framework (`LangBangShared`) via generated Objective-C/Swift headers.
- Owns:
  - `GIDSignIn` wiring (the button + delegate that eventually hands an idToken + nonce to shared)
  - AVAudio plumbing (actual)
  - SwiftUI navigation + state (ObservableObject or similar that observes shared flows via SKIE or direct callbacks if you add a thin ObjC bridge)
- **No Kotlin/Compose Multiplatform UI** on iOS for this port.

---

## expect / actual inventory (initial)

| Capability | common declaration (`expect`) | androidMain (`actual`) | iosMain (`actual`) | Notes |
|------------|-------------------------------|------------------------|--------------------|-------|
| HTTP client | Ktor engine + client in common (preferred) | OkHttp engine (or CIO) | Darwin engine | Put the CloudBackendClient logic in common using Ktor. |
| Audio playback (mp3 file) | `expect fun playAudio(filePath: String, onDone: () -> Unit)` + transport | Current MediaPlayer reuse in AudioPlayer | AVAudioPlayer + AVQueuePlayer | Keep PlaybackController + NowVoicingBus in common. |
| TTS (on-device fallback) | `expect suspend fun synthesize(...)` | AzureTtsClient (REST or SDK) | Azure Speech iOS SDK | Prefer R2 path (identical across platforms). |
| Pronunciation assessment (mic) | `expect interface PronunciationAssessor` | AzurePronunciationClient (SDK) | Azure Speech iOS SDK | Complex; can be stubbed in Pass 1. |
| Persistence (simple prefs + JSON blobs) | `expect object AppStorage` or repository interfaces | SharedPreferences + files | UserDefaults + FileManager (or a KMP DataStore port) | Starred, random config, pronoun filter, user phrases, sentence caches. |
| Google Sign-In | `expect suspend fun requestGoogleIdToken(clientId: String): GoogleIdTokenResult` | GoogleSignInHelper (CredentialManager) | GIDSignIn (with nonce) | See auth wiring section below. |
| Secure random nonce | common (kotlin std + crypto) | — | — | Already in GoogleSignInHelper. |
| Network reachability | `expect class NetworkMonitor` | Android ConnectivityManager | NWPathMonitor | Simple online/offline flow. |
| Logging | `expect fun log(...)` | Log | NSLog / os_log | Or just use a common logging facade. |
| Background prefetch | WorkManager (Android only) | actual | BackgroundTasks or none (or use shared coroutine + notifications) | iOS can start a similar download on foreground or use BGProcessing. |
| Update / self-distribution | Android APK sideload | — | App Store + TestFlight or Enterprise later | Different mechanism entirely. |

---

## Android → iOS equivalent mapping (key deps)

| Android | Purpose | iOS equivalent (KMP or Swift) |
|---------|---------|-------------------------------|
| MediaPlayer | mp3 playback | AVFoundation (AVAudioPlayer) |
| Azure Speech SDK (client-sdk:1.42) | TTS + STT + pron | Azure Speech iOS SDK (MicrosoftCognitiveServicesSpeech) |
| CredentialManager + googleid | Google Sign-In | GoogleSignIn-iOS (GIDSignIn) via Swift |
| WorkManager | prefetch + sentence regen | BGTaskScheduler + URLSession or manual on active |
| DataStore / SharedPreferences | prefs | UserDefaults + Codable or KMP abstraction |
| kotlinx.serialization | JSON | Same (common) |
| Ktor (future) | HTTP | Same (common) |
| Compose / Material3 | UI | SwiftUI (native) |
| js-ch (SFTP backup) | dev only | Omit or replace with something else for iOS |
| FileProvider + package installer | sideload updates | Omit for phone (App Store) |

---

## iOS Auth Wiring (human / infra task — NOT implemented by this agent)

1. In GCP Console (project `langbang-498411`):
   - Create a new **iOS OAuth 2.0 client** for the LangBang bundle ID you will use (e.g. `com.sponic.langbangml` or a new one).
   - Note the **iOS client ID** (looks like `123-abc.apps.googleusercontent.com`).
   - Add the **reversed client ID** as a URL scheme in the Xcode target's Info.plist (e.g. `com.googleusercontent.apps.123-abc`).

2. Append the new iOS client ID to the worker's `GOOGLE_WEB_CLIENT_ID` secret (comma-separated list). Redeploy the worker. No code change required in `index.js`.

3. In the iOS app:
   - Configure `GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: "THE_IOS_CLIENT_ID")`
   - On button tap: `GIDSignIn.sharedInstance.signIn(withPresenting: vc) { result, error in ... }`
   - Extract `result.user.idToken?.tokenString` + generate a matching nonce on the client (same as Android).
   - Call the **exact same** `POST /v1/auth/google` contract with `{idToken, nonce, instanceId}`.
   - Store the returned `session.token`.

Until the iOS client ID is registered and the allowlist updated, any real Google sign-in on iOS will get "audience mismatch". The scaffold therefore uses a **stub** that prints the exact payload it would send and lets the developer paste a token for testing (marked `TODO`).

---

## Persistence Strategy (shared)

Keep the existing `JsonListStore` / `*Store` classes in common as much as possible. They only need:
- A way to read/write a file by name under app container.
- A way to read/write small primitives (lastSync, toggles).

Create a tiny `expect/actual` storage interface:

```kotlin
expect class PlatformStorage() {
    fun readText(name: String): String?
    fun writeText(name: String, text: String)
    fun remove(name: String)
}
```

iosMain uses `FileManager.default.urls(for: .documentDirectory...)` + `String.write(to:)`.

---

## Audio Architecture (shared + platform)

- Keep `PlaybackController`, `NowVoicingBus`, `AudioActivityBus` in common.
- Move the **queue logic** (RandomPlayerState, StudyQueuePlayer, quiz players) into common; they should call an `expect` player.
- The actual player implementation only needs to be able to:
  - Given a local file path (already downloaded), play it, report done, support pause/resume/stop.
  - Report "is playing" for the transport icon.

For R2 audio: the downloader + manifest logic is already almost pure Kotlin + Ktor; it will move to common with only file I/O as actual.

---

## One Vertical Slice (implemented in this pass)

1. Ktor client in `shared` configured with Darwin engine on iOS.
2. `shared` exposes a `suspend fun fetchHealth(): String` (or fetch bootstrap summary) that hits the real backend.
3. SwiftUI screen shows the result of the call + a "Refresh" button.
4. A prominent "Sign in with Google (stub)" button that:
   - Generates a nonce
   - Prints the exact JSON body it would POST to `/v1/auth/google`
   - Has a text field for pasting a real `idToken` for manual testing (until real GIDSignIn + client ID exist)
   - On "Send" calls a thin shared wrapper that performs the POST and shows success/failure + returned session token prefix.

This proves:
- Build produces a usable framework
- Ktor + serialization works on iOS talking to real backend
- The auth contract shape is understood and ready for real wiring

---

## Build / Integration Notes

- Shared produces a dynamic framework (`LangBangShared.framework`).
- Xcode project adds a "Run Script" build phase that invokes `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (or the equivalent task for the configuration) and links the output.
- Or: build the framework once, copy into the Xcode tree for the first iteration (documented as temporary).
- Use consistent Kotlin 2.0.20 across the Gradle setup.
- For full Swift interop niceness later, consider SKIE (especially for `Flow` → `AsyncSequence` and sealed classes).

---

## Open Decisions (human)

1. **In-place conversion vs separate repo** — this doc recommends separate for safety.
2. Exact bundle ID and marketing version strategy for the iOS app.
3. Whether to adopt SKIE or a thin ObjC bridge for Flow/state observation.
4. Scope of first user-facing iOS release (drop G2, drop some quizzes, keep core phrases + random + auth?).
5. How/when to reconcile sentence caches and user phrase stores between platforms for the same user.
6. Update distribution model on iOS (TestFlight + App Store only; no equivalent of the sideload APK flow).

---

## What this Pass 1 deliberately does NOT do

- Port full UI.
- Implement every quiz generator on iOS.
- Wire real GIDSignIn + real iOS client ID (infra prerequisite).
- Implement Azure Speech on iOS (heavy; can be stubbed).
- Move all Android code into the shared tree yet (we document the target state).

The scaffold + the three docs give the team a concrete foundation and a checklist.
