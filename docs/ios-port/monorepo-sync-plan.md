# LangBang — KMP Monorepo + Anti-Drift Plan (for Grok 4.5 review)

Status: DRAFT by Claude, for Grok review. Decisions below are locked by the user
unless Grok surfaces a strong reason to revisit.

## Locked decisions
1. **One monorepo**, created by converting the existing **LangBangML in place** (keep its
   git history + `rsonnad/langbang` remote + tablet build flows). No second repo.
   Target layout:
   ```
   langbang/
     shared/       # Kotlin Multiplatform — all logic + presentation (the source of truth)
     androidApp/   # the APK: thin Jetpack Compose renderer over shared
     iosApp/       # the iOS app: thin SwiftUI renderer over shared
   ```
2. **KMP with NATIVE UIs** — Jetpack Compose on Android, **SwiftUI** on iOS. NOT Compose
   Multiplatform. Maximizing shared % is explicitly **not** the goal.
3. **The goal is parity — the two apps must not drift.** This plan is mostly about *how*.

## The anti-drift strategy (the core of this plan)
Two native UIs drift when each contains its own logic/decisions. The fix is to make the
**only** per-platform code be pixel rendering, and put everything else in `shared`:

1. **Shared presentation layer (the key move).** Move ViewModels / screen state + event
   handling into `shared/commonMain`. Each screen exposes:
   - an immutable `UiState` (a `StateFlow<UiState>`), and
   - an `onEvent(Event)` sink.
   Compose and SwiftUI become "dumb" renderers: observe `UiState`, draw it, forward user
   events. No business logic, no branching decisions, no formatting rules in either UI.
   A new feature is implemented **once** (shared VM + state), then each UI just renders the
   new state — behavior physically cannot diverge because there's only one behavior.
2. **Everything below the UI is shared:** domain models, use-cases, repositories, networking
   (Ktor, replacing the Android-only HTTP client), persistence, and the meat of LangBang —
   `RandomPlayer`, `PracticeGenerators`, all quiz/verb/adjective/adverb generators, lesson
   repositories, sentence-regen orchestration, auth orchestration, sync.
3. **`expect/actual` ONLY for true platform seams:** audio playback (ExoPlayer ↔ AVFoundation),
   TTS / **Azure Speech** (Android SDK ↔ Azure Speech **iOS** SDK), secure/persistent storage,
   **Google Sign-In** (Credential Manager ↔ GIDSignIn), permissions, clipboard, share intents.
4. **SKIE** (touchlab) over the shared framework so SwiftUI consumes Kotlin `StateFlow` as a
   Swift `AsyncSequence` and sealed classes as Swift enums. This is what makes
   "native SwiftUI over shared ViewModels" ergonomic rather than painful.

## Parity guardrails (catch drift, don't hope it away)
- **`parity-manifest.md` becomes a living contract.** Every feature maps to: one shared
  component + an androidApp renderer + an iosApp renderer. A feature with only one renderer
  is a tracked gap.
- **CI parity gate:** a checklist/test enumerating features and asserting both platforms wire
  the shared VM. New shared VM without both renderers → CI flags it.
- **Test once, both inherit:** unit/state tests target the shared ViewModels/generators in
  commonMain. Correctness proven there is inherited by both UIs.
- **Content/strings/config already server-driven** (worker + R2) — copy can't drift by design;
  keep any static strings in shared resources, not per-platform.
- **"Add a feature once" workflow** + PR template: shared VM/state first, then two thin views.

## Migration (incremental; Android stays green + tablet-installable throughout)
1. Branch off `codex/langbangml`. **Commit or stash the 61 dirty WIP files first** — never
   refactor on a dirty tree. Establish a clean baseline commit.
2. Introduce Gradle KMP: add `shared` module; rename/adapt `app` → `androidApp` consuming
   `shared`. Verify the Android build + a tablet install still work.
3. Move layers into `shared` lowest-risk-first, building Android green after each step:
   models → networking (Ktor) → repositories → domain/generators → **presentation/ViewModels**.
4. Add `iosApp` (SwiftUI) consuming the shared framework via SKIE; implement the auth + audio
   + TTS `expect/actual` iOS actuals.
5. Port iOS screens one-by-one against the shared VMs, checking off `parity-manifest.md`.
6. Reuse the already-built Pass-1 shared Ktor client + audit docs; discard only the throwaway
   SwiftUI shell as real screens land.

## Auth (iOS) — infra, Claude-owned
- Create an **iOS OAuth client** (bundle `com.sponic.langbangml`) in GCP project
  `langbang-498411`. NOTE: this is **Console-UI only** — there is no gcloud/API path (the IAP
  OAuth Admin API is deprecated, shuts down 2026-03, and only makes web clients). Add the
  reversed-client URL scheme to `iosApp` `Info.plist`.
- Append the iOS client ID to the worker's `GOOGLE_WEB_CLIENT_ID` in
  `cloudflare/langbangml/wrangler.toml` (currently
  `385515827732-9r056blngf9vpv4r2ersiiv8lte5trvb.apps.googleusercontent.com`, comma-append)
  and redeploy `langbangml-api`. The worker allowlist is already multi-value → **no backend
  code change**.
- Email-code auth (`/v1/auth/email/{start,verify}`) works on iOS unchanged.

## Risks / open questions (for Grok to pressure-test)
- **In-place conversion of the live app** is the biggest risk. Is the branch + keep-Android-green
  + committed-baseline discipline enough, or should we stage `shared` extraction behind the
  existing app first?
- **How much of LangBangML's presentation is currently entangled with Compose/Android** (e.g.
  `remember`, `Context`, Android ViewModel) — how big is the lift to extract clean shared VMs?
- **SKIE** adoption cost + whether StateFlow-per-screen is the right shared-presentation shape
  vs. an MVI library (e.g. Molecule/Decompose) for navigation state.
- Azure Speech iOS SDK API parity with the Android usage.
- Anything in `parity-manifest.md`'s "out of scope" (G2 glasses/translator) that should stay
  Android-only and be explicitly firewalled from `shared`.
```
