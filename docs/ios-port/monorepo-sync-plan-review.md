# LangBang KMP Monorepo + Anti-Drift Plan — Critical Review (Grok)

**Reviewer:** Grok (independent staff engineer review)  
**Date:** 2026 review  
**Inputs:** `monorepo-sync-plan.md`, `parity-manifest.md`, `kmp-architecture.md`, `backend-contract.md`; strict read-only inspection of `/Users/rahulio/Documents/CodingProjects/LangBangML` (codex/langbangml @ ~8b2e632 + 61 dirty files). No modifications to LangBangML. No backend work.

**Primary concern under review:** Android and iOS **must not drift**. Maximizing shared % is explicitly *not* the goal.

---

## 1. Verdict on the core anti-drift strategy

**The strategy is directionally correct but overstated in its current framing.**

The plan's thesis is right: the only reliable way to keep two *native* UIs (Compose + SwiftUI) from diverging in behavior is to put **all decision logic and state machines** in `shared/commonMain`, and make the platform UIs pure renderers that observe immutable `UiState` (via `StateFlow`) and forward events via a single `onEvent(Event)` sink. With one implementation of "what happens when X", drift in *logic* becomes structurally impossible.

**Strengths of the proposed shape (plain shared StateFlow + SKIE):**
- Matches the "native UIs, don't maximize shared" constraint.
- SKIE (StateFlow → AsyncSequence, sealed classes → Swift enums) materially reduces the pain of consuming Kotlin state from SwiftUI.
- Buses that already exist (`PlaybackController`, `NowVoicingBus`, `AudioActivityBus` in `domain/`) are already good exemplars of this pattern.

**Problems with the framing:**
- The plan repeatedly calls this "Move ViewModels / screen state". There are **no `androidx.lifecycle.ViewModel`s** in the app today. Search for `ViewModel|viewModel()` in source yields only `lifecycleScope` in `MainActivity.kt:19`.
- State lives in two places today:
  1. Hundreds of `remember { mutableStateOf(...) }`, `LaunchedEffect`, `DisposableEffect`, `rememberCoroutineScope` inside `@Composable` functions (448+ occurrences across ~30 UI files).
  2. Ad-hoc `*State` classes inside the `ui/` package (e.g. `RandomPlayerState`, `VerbsTabState`, `AdjectivesScreenState`, `NounsScreenState`, `AdverbsScreenState`, `PolishTokenSelectionState`).
- These state classes take `app: LangbangApplication` (the god object) and mix mutable UI state, derived computations, side-effect orchestration, and direct calls into audio/analytics/stores.

A "shared presentation layer" does not currently exist to "move". It must be **invented** while the old entangled code is still the only thing keeping Android shipping. This is a much larger lift than the plan's language suggests.

**Alternatives assessment (given native SwiftUI constraint):**

- **Compose Multiplatform:** Rejected by user (correctly for this goal). Using CMP would collapse the "two renderers" problem but would violate the native SwiftUI mandate and change the entire product character on iOS. Not relevant unless that locked decision is reopened.
- **Decompose:** Overkill here. Current navigation is a simple tab strip + sheets + one "settings replaces body" toggle (see `LangbangApp.kt:142`). Decompose's component tree + deep linking would add complexity without solving the core state-drift problem.
- **Molecule:** Interesting. It lets you write "presenters" as `@Composable` functions that emit `Model` snapshots (using `SnapshotState` under the hood). On Android you could keep a Compose-like mental model; on iOS you'd still render with SwiftUI against the emitted model. It reduces boilerplate for derived state and effects compared to raw `StateFlow` + manual `onEvent`. Downside: another dependency + mental model, and SKIE interop story is less mature than plain flows.
- **MVIKotlin:** Too heavy/opinionated for the payoff. The app's "MVI" today is ad-hoc.
- **Plain shared StateFlow VMs (plan's shape):** The pragmatic winner. Simple, no extra runtime, works with SKIE today. Call them "ScreenModel", "Presenter", or "FeatureStateHolder" — avoid "ViewModel" to prevent confusion.

**Concrete recommendation:** Proceed with plain `StateFlow<UiState>` + sealed `Event` + `onEvent` in `commonMain`. Add SKIE. If, after porting 2-3 complex screens (e.g. Random + one lesson tab + PracticeQuiz), the wiring feels repetitive, *then* evaluate Molecule as a local improvement. Do not adopt Decompose or MVIKotlin.

---

## 2. Reality-check: in-place conversion against actual LangBangML structure

**The lift is large. "Android stays green at every step" is optimistic given the current entanglement.**

### Current architecture signals (grounded in files)

**No separation of concerns in presentation:**
- `LangbangApp.kt` (the root shell) owns tab selection (`var section by remember`), quiz reset token, WorkManager flow collection, `LocalContext`, direct `app.cloudConfig`, `NowVoicingBus`, `PlaybackController`, and global coordination. ~150+ lines of state + effects before any screen body.
- Every major screen takes `app: LangbangApplication`:
  - `PracticeQuiz(app, ...)`
  - `SettingsScreen(app)`
  - `PhrasesScreen(...)`, `VerbsTab(...)`, etc.
- `PracticeQuiz.kt` contains a full state machine (`stageIndex`, `queue`, `index`, `recent`, `runId`, `expandedTargets`) + `LaunchedEffect(scope.auto)`, manual coroutine jobs, `Atomic*` counters, and direct calls to `PracticeGenerators`, `AzureTtsClient`, `ensureCachedAudio`, `awaitAudioPlayback`.
- `RandomPlayer.kt:53` defines `internal class RandomPlayerState(private val app: LangbangApplication, private val scope: CoroutineScope)` with `mutableStateOf` for `playing/paused/queueSize/position`, plus analytics and direct `app.audioPlayer.stop()`.
- `VerbsTab.kt:182` has `internal class VerbsTabState(private val app: ...)` that reads/writes `app.practicePrefs`, `app.pronounFilter`, creates `StudyQueuePlayer(app, scope)`, and owns sentence generation + playback state.
- Similar `*State` classes in `AdjectivesScreen.kt`, `NounsScreen.kt`, `AdverbsScreen.kt`.

**Domain is only partially pure:**
- Clean: `PlaybackController.kt`, `NowVoicing.kt` (buses + data), `EnglishConjugator.kt` (appears pure).
- Leaky: `AudioPlayer.kt` (full `MediaPlayer`, `Handler(Looper.getMainLooper())`, `AudioAttributes`), `NetworkMonitor.kt` (ConnectivityManager), `VoicingMediaSession.kt` (MediaSession), `AudioPlayback.kt` (Android Log + app extensions), `SentenceRegenService.kt` (Log + Android dispatchers), `SpeechRating.kt`.
- `StudyQueuePlayer.kt` (ui/common) is the sophisticated queue driver used by lessons/phrases — it takes `app`, owns jobs, registers transports, does prefetch-ahead, and calls `ensureCachedAudio` / `runSpeechRatingCycle`.

**Data & persistence are Context-tied:**
- `LessonRepository.kt:25` takes `Context`, loads from `assets` via `assetLesson`, constructs 8+ `JsonListStore` + `SentenceStore` instances using `context.filesDir`.
- `JsonListStore.kt:18`, `RandomConfigStore.kt:13`, `AuthStore.kt`, `AudioPrefsStore.kt`, `PronounFilterStore.kt`, `PracticePrefsStore.kt`, `StarredPhrasesStore.kt`, `CloudConfigStore.kt` all take `Context` and use `SharedPreferences` or direct files.
- No existing abstraction that would trivially become an `expect class PlatformStorage`.

**Networking:**
- `CloudBackendClient.kt` uses raw `java.net.HttpURLConnection` + `withContext(Dispatchers.IO)`. The Pass-1 skeleton correctly uses Ktor; this must be replaced for sharing.

**Assets & bootstrap:**
- Bundled lessons live in `app/src/main/assets/lesson-*.json`. LessonRepository falls back `assetLesson` → cloud. This is Android asset mechanism.

**Google auth:**
- `GoogleSignInHelper.kt` is CredentialManager + `androidx.credentials`. Pure Android actual.

**G2 / translator:**
- Already isolated in `g2trans/`, `even-g2-test/`, `g2-gemini-bridge/`, `captures/`, and `ExternalNowVoicingReceiver.kt`. `parity-manifest.md` correctly marks as out of scope for the phone KMP port.

**Tests:**
- Zero unit tests in `app/src/main/kotlin`. A handful exist only in `g2trans/src/test`. "Test once, both inherit" starts from zero.

### Easy vs hard extraction

**Relatively easy (low platform coupling):**
- Models (`data/model/*`)
- `PracticeGenerators.kt` (pure object, `buildItems` + helpers)
- `LbJson.kt`
- `EnglishConjugator` + conjugation helpers
- `PlaybackController`, `NowVoicingBus`, `AudioActivityBus`
- `GeminiClient` prompt constants + shaping (the network parts move to Ktor)
- `R2SentenceManifest`, sentence version/wipe logic (if file I/O is abstracted)

**Hard / high risk (entangled or platform-heavy):**
- `RandomPlayerState` + `RandomConfig` + `RandomPlayer.kt` usage
- `StudyQueuePlayer` + all callers (VerbsTab, Phrases, etc.)
- `PracticeQuiz.kt` + `PracticeModel.kt` / `Quiz*` (state machine + audio timing + self-grade + expansion logic)
- All lesson `*Screen*State` classes + their `LaunchedEffect` + Gemini + sentence playback
- `LangbangApp.kt` shell (tabs, global NowVoicing slot, WorkManager progress, double-tap reset, offline banner)
- `SettingsScreen.kt` (huge; clipboard, agent token, context-heavy sections)
- `LessonRepository` + all stores + asset loading
- `AudioPlayer` (the real implementation)
- `AzurePronunciationClient` (mic permission + streaming assessment)
- `PrefetchService` / `PrefetchWorker` (WorkManager)
- `NetworkMonitor`
- Auth stores + GoogleSignInHelper
- `PhraseSyncService`, user content merge logic (timing with auth)

### Is "Android stays green at every step" realistic?

Marginally, **only with extreme discipline**:

- The current tree has 61 dirty files. Plan step 1 (clean baseline) is non-negotiable.
- Renaming `app` → `androidApp` + restructuring packages while tablet workflows (ADB wifi, sideload, self-update, debug receivers) are live is invasive.
- The plan's order (models → net → repos → domain → **presentation last**) means the Android UI layer stays on the old entangled code for a long time. You will be maintaining two parallel implementations of some state machines during the transition.
- Every time you touch a store or the audio player to make it `expect/actual`, you risk breaking the only shipping app.

**Better staging inside the monorepo:**
Introduce `:shared` as a new module that the existing `:app` (or renamed `androidApp`) can depend on. Move code into `commonMain`/`androidMain` while the old packages continue to compile against the new shared facades. Only delete the old Android-specific paths once the shared + thin-renderer path is proven for that slice. This is safer than aggressive "move everything then make androidApp thin".

The Pass-1 `kmp-architecture.md` (separate repo recommendation) was explicitly motivated by exactly this risk. The locked decision to do in-place conversion increases risk; the plan does not sufficiently mitigate it.

---

## 3. Pressure-test the guardrails

**Current guardrails are aspirations, not enforceable mechanisms. They are theater until made concrete.**

- `parity-manifest.md` as a "living contract" is just a markdown checklist. It will rot the moment a feature is implemented only in one renderer.
- "CI parity gate" and "checklist/test" are mentioned but not designed. There is no description of what actually fails the build.
- "Test once" has no existing test base to inherit from.

**Concrete, enforceable mechanisms (what should replace the aspirations):**

1. **Generator + pure logic tests (highest leverage):**
   - `PracticeGenerators` (and `QuizGenerators`) must live in `commonTest`. Add property-based or table-driven tests for `buildItems` covering scopes, stages, pronoun filters, helper patterns, spread/interleave. These are the heart of quiz behavior.
   - Same for `EnglishConjugator`, sentence shaping helpers, dedup logic in stores (once abstracted).

2. **Shared model contract tests:**
   - For each major feature model (`RandomPlayerModel`, `LessonTabModel`, `PracticeModel`, `PhraseModel`, etc.), a test suite that drives the state machine through the key paths (start, pause/resume, next, error, empty, star toggle, regen) and asserts the emitted `UiState` sequence.
   - These tests run on JVM (androidTest or commonTest with kotlin-test). Both platforms inherit correctness.

3. **Import / architecture lint (hard guard):**
   - Detekt or Kotlin compiler opt-in rule in `commonMain`: forbid `android.*`, `androidx.*` (except in `androidMain` expect/actual files).
   - Forbid direct `LangbangApplication` (or whatever DI root becomes) from shared.
   - Forbid `Context`, `File`, raw prefs, `WorkManager`, `MediaPlayer` in common.

4. **Parity CI job (not just docs):**
   - A Gradle task or GitHub Action step that:
     - Enumerates the features from `parity-manifest.md` (or a machine-readable subset).
     - For each, asserts that `shared` exposes the corresponding `*Model` / `*Presenter`.
     - Optionally does a lightweight "both sides compile against it" (even if iOS side is stubbed).
   - New shared model without matching renderers in both `androidApp` and `iosApp` → red build.
   - This is cheap to implement and actually catches the "implemented only for one platform" case.

5. **UI parity remains human + manifest:**
   - Because UIs are native, you cannot diff pixels mechanically without heavy screenshot infrastructure. Accept that the enforceable part is *behavior* (via shared models + tests) + PR template requiring manifest check-off + two screenshots or video.

Without the above (especially 1-4), the guardrails will not prevent drift; they will only document it after the fact.

---

## 4. Risks the plan misses or under-weights

- **Azure Speech iOS SDK parity (big one):** Android uses both direct REST fallback (`AzureTtsClient`) and the Azure SDK for pronunciation assessment (`AzurePronunciationClient` + token from `/v1/azure/speech-token`). The iOS SDK (`MicrosoftCognitiveServicesSpeech`) has a meaningfully different shape: different configuration, recognizer lifecycle, result types, and mic permission UX. Streaming assessment + scoring may produce non-identical results. Plan lists it as a risk; it needs a dedicated spike + abstraction boundary before any quiz/pron screen is ported.

- **Audio/TTS timing & pause semantics:** The app's identity includes very specific timing: EN cue → configurable delay → slow PL (or normal), true in-place pause that preserves position, reveal masking in quizzes, prefetch-ahead during queues, "parkCurrent". `MediaPlayer` vs `AVAudioPlayer`/`AVQueuePlayer` have different completion semantics, position accuracy under pause, prepare latency, and error behavior. `StudyQueuePlayer` was written to paper over previous flakiness. Replicating the exact feel on iOS will require careful actual implementations + cross-platform behavioral tests (or acceptance by ear).

- **Navigation + global state ownership:** The tab bar, "select Quizzes twice resets quiz state", Settings toggle that replaces body, and the sticky `NowVoicingPanel` (which must receive transport from *any* source) live in `LangbangApp.kt`. If tab state or panel coordination is reimplemented in SwiftUI vs Compose without sharing the decision logic, you get drift in reset behavior, panel visibility, and transport registration.

- **Persistence abstraction + cross-platform identity:** Current stores are not just "DataStore". They have custom dedup-by-key, replaceAll semantics, specific file names, and merge rules on sign-in. Moving to iOS `UserDefaults` + files or a KMP DataStore port must preserve exact roundtrips for the same user. Sentence caches, random config, pronoun filters, and starred phrases must produce identical behavior.

- **G2 firewalling:** Already mostly isolated, but the monorepo conversion must explicitly keep `g2trans/`, `even-g2-test/`, etc. out of `shared` and out of the phone `androidApp` target. Any Gradle include or package dependency creep will leak Android-only or glasses-only code into the shared source of truth.

- **KMP + SKIE CI friction:** SKIE is not free. It requires macOS runners, specific Xcode/Kotlin versions, extra Gradle config, and careful framework caching. The current LangBang-iOS skeleton already has a built framework tree; real embedding/signing scripts + "embedAndSignAppleFrameworkForXcode" phases are a source of CI flakes. Plan underplays this.

- **iOS framework size/startup:** Pulling generators, Ktor, coroutines, serialization, etc. into a dynamic framework for the iOS app will increase binary size and cold-start time (Kotlin/Native runtime init). Acceptable for a language app, but measure it.

- **Single-repo versioning:** The clever auto-increment of `buildNumber` on assemble lives in `app/build.gradle.kts`. A monorepo needs a coherent story for Android versionCode + iOS CFBundleVersion / marketing version from one source (or two coordinated sources). App Store vs Play distribution models differ.

- **In-place dirty tree risk:** 61 uncommitted changes + tablet debug flows (ADB keepers, receivers, sideload) + self-update + media session. Any restructuring that touches `MainActivity`, `LangbangApplication`, or the WorkManager paths risks the developer's daily driver.

- **"Thin renderer" assumption on Android:** Even on Android, after extraction, the Compose code will still need `LocalContext` for some things (clipboard, share, permissions, LaunchedEffect for side effects that are truly UI). The plan's "only pixel rendering" is aspirational; some platform UI glue will remain.

---

## 5. Corrected / annotated migration sequence

Original plan order is too aggressive on presentation and under-protects the live Android app. Suggested revised sequence:

1. **Baseline (mandatory):** Commit or stash the 61 dirty files. One clean commit on the branch. Tag it. Any refactor happens from here.

2. **Add KMP without destroying the app:**
   - Add KMP + shared module **alongside** the existing app (do not yet rename `app` to `androidApp`).
   - Shared initially produces a library; existing app can depend on it.
   - Verify full tablet build + install + smoke still works with zero behavior change.

3. **Move pure layers first (keep Android green):**
   - Models, `LbJson`, `PracticeGenerators`, `EnglishConjugator`, pure helpers → `commonMain`.
   - Wire existing Android code to use the shared versions (no behavior change).
   - Add `commonTest` for the above immediately.

4. **Storage + networking seams (expect/actual):**
   - Define minimal `expect` storage interface.
   - Port `JsonListStore` / config stores logic into shared using the abstraction.
   - Replace `CloudBackendClient` guts with Ktor (the Pass-1 skeleton is the model). Keep Android using the shared client.

5. **Extract state holders / models (the real presentation work):**
   - Create `RandomPlayerModel`, `PracticeModel`, `LessonTabModel*`, `PhraseModel`, etc. in shared.
   - These should be testable in commonTest.
   - Android screens can temporarily delegate to the new models while keeping old UI code, or incrementally adopt thin renderers.
   - Do **not** wait until "all domain is moved" to start this; interleave with step 3-4 where possible.

6. **Platform audio + buses:**
   - Move buses if not already pure.
   - Implement `expect/actual` audio player surface (play file + onDone, pause/resume/stop, isPlaying).
   - Android actual wraps existing `AudioPlayer` (or vice versa).
   - iOS actual uses AVFoundation.

7. **Safety checkpoints after every major slice:**
   - Full tablet install + smoke: random play (config + transport), one quiz mode, lesson playback, star, phrase add/complete, sign-in stub.
   - Update parity-manifest + run any parity CI check.

8. **iOS side (can start in parallel once core models + Ktor + storage are stable):**
   - Add iOS targets + SKIE in the monorepo.
   - Implement iOS actuals (storage, audio, Google via GIDSignIn + nonce, Azure Speech).
   - Port screens using the shared models, checking off manifest.
   - The existing Pass-1 SwiftUI shell in LangBang-iOS is throwaway scaffolding.

9. **Android thin renderer adoption (last per feature):**
   - Only after a shared model + iOS renderer exists for a feature, replace the old Android `@Composable` + `*State` implementation with a thin renderer over the shared model.
   - Delete old code only when the new path is proven on device.

10. **Monorepo layout cleanup (rename, module structure) only after several vertical slices are dual-rendered and green.**

Add explicit "revert gate": if a step breaks the tablet smoke, the change does not land until fixed or the extraction is re-scoped.

---

## 6. Bottom line

**The plan's intent is sound; the execution plan underestimates the lift and the risk to the live Android app.**

The anti-drift architecture (shared state machines + dumb native renderers + SKIE) is a good fit for the constraint. The current codebase has essentially zero of that architecture today — state and orchestration are inside Composables and ad-hoc `*State` classes that take the full `LangbangApplication` god object. Extracting "presentation" is not a move; it is a substantial invention + inversion of dependencies.

In-place conversion on a dirty tree with complex audio queues, zero tests, platform-tied persistence, and no existing ViewModels is high-risk. "Android stays green" is possible only with much more conservative staging than the current numbered list suggests.

The guardrails as written are documentation theater. They need real tests (generators + models), import bans, and an executable parity check.

### Top 3 things that must change before execution

1. **Change the migration strategy:** Do not treat "move presentation last" as safe. Introduce `:shared`, move pure + seams first, extract state holders incrementally, and keep the existing Android UI working against facades for as long as needed. Make the "thin Android renderer" step explicit and per-feature.

2. **Make guardrails real before claiming anti-drift:** Implement generator/model tests in `commonTest`, add Detekt/common-source lint forbidding Android types in `commonMain`, and add a CI task that treats the parity manifest as executable (missing renderer for a shared model = failure).

3. **Budget explicitly for the seams that are hardest to keep identical:** Azure Speech iOS SDK + full audio timing/pause semantics + global NowVoicing + nav coordination. These are not "just expect/actual"; they are where behavioral drift is most likely and most user-visible.

### Proceed?

**Proceed only after the above three are addressed and a clean baseline exists.** Do the first 3-4 extraction steps in a way that the tablet app can be installed and smoked after each. If the 61 dirty files represent active tablet development, strongly consider proving the shared models + one iOS renderer in a less disruptive branch or the current LangBang-iOS skeleton first, then fold the proven sources into the monorepo conversion.

The goal (no drift) is achievable with this architecture. The current plan does not yet show a realistic path that protects the shipping Android app while building the shared source of truth.

---

**End of review.**