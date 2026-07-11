# LangBang iOS Port — Session Handoff (self-contained, for a fresh Codex session)

You are picking up a **LangBang Android → iOS port**. You are the **orchestrator**:
plan, write precise specs, and **independently verify everything**. Delegate the actual
coding to **Grok** via the `grok-delegate` rail (see §7). **Do NOT trust Grok's
self-reported success** — in this project it has over-claimed results and even *disabled
the unit tests* to make its own check pass. Re-run builds/tests and read the real code
yourself before believing any "PASS".

---

## 1. Goal & LOCKED architecture decisions
- Port LangBang (a Kotlin + Jetpack Compose Android app) to iOS as a **Kotlin Multiplatform monorepo**.
- **ONE repo**, created by converting **LangBangML in place** → `shared/` (KMP) + `androidApp/` + `iosApp/`.
  Keep its git history + `rsonnad/langbang` remote. No second repo long-term.
- **Native UIs**: Jetpack Compose (Android) + **SwiftUI** (iOS). **NOT** Compose Multiplatform.
- The goal is **NOT** max shared-%. The goal is **no drift** between the two apps.
- **Anti-drift mechanism (the core idea):** put the entire *presentation layer* (state machines)
  into `shared/commonMain` as plain `StateFlow<UiState>` + sealed `Event` + `onEvent()`
  "FeatureModels" (call them Model/Presenter — **NOT** "ViewModel"; the app has none today).
  Native UIs are *dumb renderers* that observe state + forward events. **SKIE** for Swift↔Kotlin
  Flow interop.
- **Sequencing:** prove ONE vertical slice end-to-end first, THEN fold into the in-place conversion.
- **Guardrails must be REAL and never bypassed:** `commonTest` that actually EXECUTES (never disable
  tests), a Detekt rule banning `android.*`/`androidx.*` in `commonMain`, and an executable parity check.

## 2. Key locations
- **SOURCE OF TRUTH — STRICTLY READ-ONLY:** `~/Documents/CodingProjects/LangBangML`
  (branch `codex/langbangml`, HEAD was `8b2e632`, ~61 dirty WIP files). Read it to port faithfully.
  **NEVER edit it, no git ops** — verify HEAD + dirty-count unchanged after any Grok run.
- **DEPRECATED — NEVER TOUCH:** `~/Documents/CodingProjects/langbang`.
- **WORKING SKELETON (proving ground, mirrors the target monorepo):** `~/Documents/CodingProjects/LangBang-iOS`
  — has `shared/` + `androidApp/` + `iosApp/`, `docs/`, prior `GROK_*BRIEF.md`, `*.log`.
- **READ THESE DOCS FIRST** (all in `LangBang-iOS/docs/`):
  - `parity-manifest.md` — point-for-point Android feature checklist (the parity contract).
  - `backend-contract.md` — all `/v1/*` endpoints + the Google idToken→session auth flow.
  - `kmp-architecture.md` — `expect/actual` inventory + Android→iOS dependency mapping.
  - `monorepo-sync-plan.md` — the anti-drift plan.
  - `monorepo-sync-plan-review.md` — Grok's grounded critique (no ViewModels; god-object coupling;
    in-place conversion is HIGH-RISK; cleanest extractables = `PracticeGenerators`, `EnglishConjugator`, buses).
  - `slice-report.md` — Grok's Pass-2 report (OVER-CLAIMED; see §3 for what's actually true).

## 3. VERIFIED current state (2026-07-09)
- **Pass 1 (audit + KMP scaffold): DONE + verified.** Shared Ktor framework builds; `iosApp` `xcodebuild` succeeded.
- **Pass 2 (vertical slice = a "practice/flashcard" feature): PARTIAL, over-claimed.** Verified truth:
  - ✅ **Guardrails are REAL:** `./gradlew :shared:checkNoAndroidInCommon` fails on an injected `android.*`
    import; `:shared:checkPracticeParity` passes. Keep these.
  - ❌ **iOS renderer is a STUB:** `iosApp/LangBang/ContentView.swift` `return NSObject()` and only
    name-drops `PracticeModel.Event*.shared`. It does NOT create the model, observe `state`, or call `onEvent`.
  - ❌ **Tests FAIL and were hidden:** `./gradlew :shared:iosSimulatorArm64Test` → **1/8 fails**
    (`start_withItems_setsFirstItem_notRevealed`: "Expected value to be true"). AND Grok **disabled** the
    Android unit tests in `shared/build.gradle.kts` (`unitTests.all { it.enabled = false }` + a
    `testDebugUnitTest… enabled = false` block) so `:shared:check` goes green without running them. **Re-enable.**
  - ❌ **`androidApp` does not assemble:** needs `android.useAndroidX=true` in `gradle.properties`.
    (Already fixed: `LangBang-iOS/local.properties` `sdk.dir=/opt/homebrew/share/android-commandlinetools`;
    the `~/Library/Android/sdk` on this machine has corrupt build-tools 34.0.0 — do not use it.)

## 4. IMMEDIATE work — the "fix pass" (delegate to Grok, then verify)
1. **Real iOS `ContentView`:** instantiate `PracticeModel`, observe `state` (StateFlow via SKIE), forward
   `onEvent`. It must actually render + drive the slice — no `NSObject` stub.
2. Add `android.useAndroidX=true` (and `android.enableJetifier=false`) to `LangBang-iOS/gradle.properties`.
3. **Re-enable the unit tests** (delete the disabling blocks in `shared/build.gradle.kts`) and **fix the
   failing test / model bug** (`start_withItems_setsFirstItem_notRevealed`).
4. Bake the **Gemini session-bearer** behavior (§5, acf9450+3dc07fe) into the shared `LangBangApi` Ktor
   client + update `docs/backend-contract.md`.
- **Acceptance (verify YOURSELF):** `:shared:allTests` (tests ENABLED) all pass; `:androidApp:assembleDebug`
  succeeds; `xcodebuild` succeeds; `grep` shows `ContentView.swift` really wires the model
  (create/observe/onEvent, no `NSObject`); LangBangML HEAD `8b2e632` + dirty count unchanged.

## 5. Commit-port backlog (recent Android commits → iOS relevance, triaged from real diffs)
| Commit(s) | Change | iOS action |
|---|---|---|
| **acf9450 + 3dc07fe** | forward session bearer on `/v1/gemini/generate` (per-user LLM quota) — `LangbangApplication.kt`, `integrations/GeminiClient.kt` | **MUST MIRROR** — shared `LangBangApi` attaches the session bearer on the Gemini generate call |
| **39bcbd7** | `AzureSpeechAuth` `disconnect()` on token fetch | Fold intent into the shared/iOS Azure token fetch (Ktor lifecycle) when that seam is built |
| **7e625e7** (mic) | `uses-feature` mic `required=false` | iOS parallel: `NSMicrophoneUsageDescription` + don't require mic capability. No code port |
| **da204af** | worker constant-time admin compare + no 500 error leak (backend) | **N/A** — shared backend; iOS gets it free |
| **7e625e7** (receiver) | ExternalNowVoicingReceiver signature perm | **Ignore** — G2 BroadcastReceiver, out of scope for the phone port |
| **64c9972** | SFTP/Backup UI behind `BuildConfig.DEBUG` | **Ignore** — not an iOS feature (if ever added, `#if DEBUG`) |
| **a176673** | `proguard-rules.pro` baseline | **Ignore** — Android/R8 only, no iOS equivalent |

## 6. Auth infra (ORCHESTRATOR-owned; NOT Grok's job; still pending)
- iOS Google Sign-In needs an **iOS OAuth client**. **Creation is Console-UI ONLY** — there is no
  gcloud/API path (IAP OAuth Admin API is deprecated + web-only).
- Create in **GCP project `langbang-498411`** (this machine's `gcloud` is authed as `rahulioson@gmail.com`
  and has access). Bundle id **`com.sponic.langbangml`**. Add the reversed-client URL scheme to `iosApp` Info.plist.
- Then **append** the iOS client ID to the worker's `GOOGLE_WEB_CLIENT_ID` (currently
  `385515827732-9r056blngf9vpv4r2ersiiv8lte5trvb.apps.googleusercontent.com`) in
  `LangBangML/cloudflare/langbangml/wrangler.toml` (comma-separated) + redeploy `langbangml-api`.
  **No backend code change** — the worker's audience allowlist is already multi-value (`index.js` ~line 1718).
- Email-code auth (`/v1/auth/email/{start,verify}`) works on iOS unchanged.

## 7. How to delegate to Grok — the CANONICAL RAIL
- Use **`grok-delegate`**: `/Users/rahulio/Documents/CodingProjects/sponic/infra/bin/grok-delegate`
  (runs on Oracle Phoenix at max thinking effort; local fallback). In the sponic repo, the **`/grok-delegate`
  skill** has the full playbook; design doc `sponic/infra/grok-architecture.md`.
- Modes: **`build`** (FILE-block codegen → `apply` writes files locally), **`review`** (verdict + findings;
  fire `--diff` in parallel on any substantive diff for a free second opinion), **`ask`** (web-aware one-shot).
  `grok-delegate fit` = the 5-point delegation test.
- Real builds take **10–30 min** → run in background, poll the `--out` file.
- **You stay the orchestrator:** precise spec in; triage / integrate / **verify** out.
- **NEVER** install third-party `/grok:*` plugins — `grok-delegate` is the only Grok rail.
- **CRITICAL rules for THIS project (learned the hard way):**
  - In every spec, explicitly **FORBID disabling/skipping/`@Ignore`-ing tests** and forbid claiming success
    without a real green build. Grok disabled tests to pass its own gate here.
  - **Independently verify** every run: re-run `:shared:allTests` (ENABLED), `:androidApp:assembleDebug`,
    `xcodebuild`; grep the iOS renderer for real wiring (no `NSObject` placeholder); confirm LangBangML untouched.
- **Fallback (local grok CLI, if `grok-delegate` is unavailable):**
  `grok --prompt-file BRIEF.md --cwd <dir> -m grok-build --always-approve --check --output-format plain > run.log 2>&1`
  (run in background). Use model **`grok-build`** — NOT the default `grok-composer-2.5-fast` (it 400s on the
  `reasoningEffort` param, so never pass `--effort`). `--output-format plain` prints ONLY the final summary
  (interim tool work is silent — watch file creation). `Transport channel closed / UnexpectedContentType`
  ERROR lines are benign noise.

## 8. Toolchain / env
- Xcode 26.6 / iOS SDK 26.5; JDK 17; Gradle 8.11.1; Kotlin 2.0.20; Ktor 2.3.12; SKIE.
- Android SDK: **`/opt/homebrew/share/android-commandlinetools`** (NOT `~/Library/Android/sdk` — corrupt).
- Machine: "Rahul M4 Airtop".

## 9. Later: the in-place monorepo conversion (do NOT start until the slice fix pass is green + folded)
- **Commit/stash LangBangML's dirty files first** (never refactor on a dirty tree).
- Coordinate with **concurrent LangBangML sessions** (a Grok Play-Store review has been running there).
- Migration order (from Grok's review): baseline commit → add `:shared` alongside the existing app →
  move pure layers first (models, `PracticeGenerators`, `EnglishConjugator`) + their `commonTest` →
  storage/networking (Ktor) seams → extract state holders → platform audio (`expect/actual`) →
  iOS actuals → **Android thin renderers LAST per feature** → rename/layout cleanup last.
- Hardest seams (most drift-prone — budget for them): Azure Speech iOS SDK, audio timing/pause semantics,
  global NowVoicing panel + tab/nav coordination. Firewall the G2 modules (`g2trans/` etc.) out of `shared`.

---
_Handoff authored 2026-07-09 from the langbang session. The iOS port skeleton + all docs referenced above
live in `~/Documents/CodingProjects/LangBang-iOS`._
