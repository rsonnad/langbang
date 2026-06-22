# Play Store Freemium — Sequenced Engineering Plan

Operationalizes [`docs/prompts/03-play-store-readiness-and-legal-pages.md`](prompts/03-play-store-readiness-and-legal-pages.md)
into a dependency-ordered build plan. Derived from a security/readiness review on
2026-06-22 (four-dimension audit: Android/Play, backend/auth, LLM abuse, billing).

Goal: ship LangBang to Google Play as a freemium product (free tier + paid
"LangBang Plus" including API access), abuse-resistant and policy-compliant.

All work lands in this repo (LangBangML). The legacy `langbang` repo is frozen.

## Assumptions (change these and the plan shifts)

- **v1 = EN→PL only** (`com.sponic.langbangml.enpl`), per
  [`docs/google-auth-account-plan.md`](google-auth-account-plan.md). One Play
  listing for now; `plEn` follows later.
- **Monetization = auto-renewing subscription** ("LangBang Plus"). A consumable
  AI-credits SKU is a possible add-on; the Play Billing + server-verification
  plumbing is ~90% identical either way.
- **Target = freemium at launch.** Billing is isolated as Phase 4 so a free-only
  launch (Option B below) remains possible without rework.

## Critical path / long-lead items (start day 1)

1. **Credential migration** off the temporary `alpacaplayhouse@gmail.com`
   Google/Gemini project + Resend domain sender (LEGAL-6).
2. **Closed test track** (~12 testers, 14 days) required before a new personal
   Play account gets production access (REL-4).

Hard chain: `BE-3/BE-4` (server owns prompts + provider keys) → `AND-4` (client
drops keys) → `BE-7` (rotate keys). And: `AND-7` (signed AAB) + `REL-1` (Console
app) → `BILL-2` (billing client testing).

---

## Phase 0 — Immediate containment (hours; no dependencies)

| ID | Task | Where | Acceptance |
|---|---|---|---|
| BE-0a | Cloudflare WAF rate-limit rules (per-IP) on `/v1/gemini/*`, `/v1/phrases/complete`, `/v1/audio/manifest`, `/v1/auth/email/*`, `/v1/analytics/events` | CF dashboard | flood → 429 at edge |
| BE-0b | GCP billing budget + hard quota cap on `langbang-498411`; Azure spend cap | GCP/Azure console | spend cannot exceed ceiling |
| BE-0c | Lock CORS from `*` to an origin allowlist | `cloudflare/langbangml/src/index.js`, `cloudflare/langbang-org/worker.template.js`, `cloudflare/langbang-phrase-bridge/worker.js` | response ACAO is the allowlist |

## Phase 1 — Backend hardening (launch-blocking; parallel with 2–3)

| ID | Task | Where | Acceptance | Deps |
|---|---|---|---|---|
| BE-1 | Require `requireUser`/agent-token auth on the 3 expensive endpoints | `index.js` routes for `/v1/gemini/generate`, `/v1/phrases/complete`, `/v1/audio/manifest` | unauth → 401 | — |
| BE-2 | Per-user quota on those endpoints (reuse `loadAiPhraseQuota`/`consumeAgentQuota`) | `index.js` | over-quota → 429 | BE-1 |
| BE-3 | Redesign `/v1/gemini/generate` to structured params; build prompts server-side; remove raw-prompt path | `geminiGenerate` handler | endpoint rejects free-form `prompt` | BE-1 |
| BE-4 | Move Azure off-device: authed worker endpoint mints short-lived Azure STS tokens; client SDK uses token | new handler | client never holds raw Azure key | BE-1 |
| BE-5 | Per-user, short-lived Gemini Live token issuance (replace static `GEMINI_LIVE_TOKEN`) | mirror `createAgentToken` | Live token per-user, expiring, revocable | BE-1 |
| BE-6 | Auth hardening: throttle `email/start`, lockout + invalidation on `email/verify`, gate/validate analytics ingest, constant-time admin compare, Gemini `safetySettings` | `index.js` | email-bomb & brute-force blocked | — |
| BE-7 | Rotate all provider keys/tokens after clients stop shipping them | BW `devops-langbang` + wrangler secrets | old extracted keys dead | AND-4 |
| BE-8 | Prompt-injection hardening on remaining user-text fields (delimiters, output caps, schema validation) | phrase/AI prompt builders | injection can't break template/output shape | BE-3 |

**Gate:** each expensive endpoint unauth → 401; authed over-quota → 429; no raw prompt accepted.

## Phase 2 — Play-safe Android build (AND-1/AND-7 start now; AND-4 waits on BE-3/BE-4)

| ID | Task | Where | Acceptance | Deps |
|---|---|---|---|---|
| AND-1 | Play-safe variant (build type/flavor + `src/debug` overlay) isolating internal capabilities | `app/build.gradle.kts`, manifests | internal features only in non-Play artifacts | — |
| AND-2 | Strip `WRITE_SECURE_SETTINGS` + `AdbWifiKeeper` from Play artifacts | `AndroidManifest.xml`, `AdbWifiKeeper.kt` | perm absent from Play AAB | AND-1 |
| AND-3 | Strip `REQUEST_INSTALL_PACKAGES` + `UpdateChecker`/`UpdateBanner` | manifest, `domain/UpdateChecker.kt`, `ui/UpdateBanner.kt` | self-update absent from Play AAB | AND-1 |
| AND-4 | Remove `AZURE_SPEECH_KEY`/`GEMINI_API_KEY` from `BuildConfig`; route via worker | `app/build.gradle.kts`, `AzureTtsClient.kt`, `AzurePronunciationClient.kt`, `GeminiClient.kt`, `g2trans/BridgeConfig.kt` | no secret strings in AAB | BE-3, BE-4 |
| AND-5 | Contextual mic permission (on entering pronunciation, not launch) | `MainActivity` | first run never prompts mic | — |
| AND-6 | Protect/cleanup exported `ExternalNowVoicingReceiver` | `AndroidManifest.xml` | no external app can drive it | — |
| AND-7 | Release signing + Play App Signing + `bundleRelease` (AAB), R8/minify, drop arm64-only split | `app/build.gradle.kts` | signed AAB Play accepts | — |
| AND-8 | Make version bumps explicit (stop mutating `version.properties` on every build) | `app/build.gradle.kts` | ordinary assemble doesn't bump | — |
| AND-9 | In-app Privacy/Terms links + account-deletion entry | `ui/settings/SettingsScreen.kt` | links open live pages; deletion reachable | LEGAL-1/2/3 |
| AND-10 | Lint + CI check that forbidden perms/secrets are absent from the AAB | gradle/CI | build fails if a banned perm/secret reappears | AND-2/3/4 |

**Gate:** unzip AAB → absent: `REQUEST_INSTALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, Azure/Gemini key strings, self-update flow.

## Phase 3 — Legal & compliance (parallel with 1–2)

| ID | Task | Where | Acceptance | Deps |
|---|---|---|---|---|
| LEGAL-1 | Real Privacy Policy at `langbang.org/privacy` (spec in doc 03 §Privacy) | `cloudflare/langbang-org/worker.template.js` | `curl /privacy` returns policy | — |
| LEGAL-2 | Real Terms at `langbang.org/terms` | same | `curl /terms` returns terms | — |
| LEGAL-3 | Account + data deletion: server endpoint (D1/R2/sessions) + user-facing path | new handler + client/web | deletion removes all user data | — |
| LEGAL-4 | Data Safety form answers (audio→Azure, first-party analytics, optional account data) | `docs/` + Console | form matches shipped flows | AND-4, BE-4 |
| LEGAL-5 | AI-generated content disclosure + reporting/contact path | listing + in-app | meets Play AI policy | — |
| LEGAL-6 | Credential migration off temp Google/Gemini project + Resend verified-domain sender | `wrangler.toml`, `local.properties`, BW | launch traffic on dedicated creds | — |
| LEGAL-7 | Retention/deletion policy implemented (analytics retention; sessions already expire) | worker/cron | documented + enforced | — |

**Gate:** both legal URLs return real bodies; deletion removes a test account end-to-end.

## Phase 4 — Freemium / billing (separable; can be Option-B fast-follow)

| ID | Task | Where | Acceptance | Deps |
|---|---|---|---|---|
| BILL-1 | D1 migration: `subscriptions` table + `tier` on users | `cloudflare/langbangml/migrations/012_*.sql` | entitlement attaches to `users.id` | — |
| BILL-2 | Play Billing Library: subscription product, purchase flow, paywall UI | `app/` (+ `billing-ktx`) | purchase Plus in test track | AND-7, REL-1 |
| BILL-3 | Server-side purchase verification (Google Play Developer API) | new handler | purchase token verified server-side | BILL-1 |
| BILL-4 | Real-time Developer Notifications (Pub/Sub) → renew/cancel/refund/grace | new handler | tier updates on lifecycle events | BILL-3 |
| BILL-5 | Tier-aware gating: gate agent-token minting + raise AI quota by tier; replace global `DEFAULT_*` | `index.js` quota/agent paths | free vs Plus differ server-side | BILL-1 |
| BILL-6 | Paywall/upgrade UX; entitlement read from server (never self-asserted) | `app/`, web | locked features unlock after purchase | BILL-2/5 |
| BILL-7 | Restore purchases / entitlement sync | `app/` | reinstall restores Plus | BILL-3 |

**Gate:** test-purchase Plus → server flips tier → agent API + raised AI quota unlock; cancel → reverts.

## Phase 5 — Listing, testing, submission

| ID | Task | Acceptance | Deps |
|---|---|---|---|
| REL-1 | Play Console app + package setup | AAB uploadable | AND-7 |
| REL-2 | Listing copy + screenshots ([`play-store-listing-copy.md`](play-store-listing-copy.md); keep AI/API copy gated until BE+BILL ship) | listing complete | — |
| REL-3 | Data Safety + content rating + permissions declarations | matches LEGAL-4 | LEGAL-4 |
| REL-4 | Closed test (~12 testers, 14 days) | production access unlocked | REL-1 |
| REL-5 | Reviewer instructions / test login (`/v1/auth/test-login`) | reviewer can exercise gated flows | BE-1 |
| REL-6 | Production submission | live | all gates |

---

## Sequencing & milestones

```
Day 0      ──► Phase 0 containment (hours)
              ─┬─ Track A: Phase 1 backend ───────────────┐
               ├─ Track B: Phase 2 Android (AND-1/7 now) ─┤→ AND-4 after BE-3/4 → BE-7 rotate
               ├─ Track C: Phase 3 legal + LEGAL-6 migr. ─┘
               └─ Track D: REL-1 console + REL-4 recruit testers (start EARLY)
M1 "Free-safe":      Phases 0–3 done + signed AAB → begin REL-4 closed test (14-day clock)
                  ──► Phase 4 billing (during the 14-day window)
M2 "Freemium-ready": Phase 4 done + closed test passed
M3:                  REL-6 production submission
```

Tracks A/B/C run concurrently; the only cross-track blocker is AND-4 → BE-7.
Start REL-1 + REL-4 + LEGAL-6 on day 1 (wall-clock-bound, not effort-bound). The
14-day closed test overlaps Phase 4, so billing is mostly free on the calendar.

## Two ways to ship

- **Option A — freemium at launch:** all phases; M3 ≈ 4–6 weeks wall-clock
  (closed-test window dominates; solo dev + AI assist).
- **Option B — free first, billing fast-follow:** ship at M1 (skip Phase 4),
  submit ~2 weeks sooner, land billing as v1.1. Lower risk, faster validation.

## Status log

- 2026-06-22: Plan created. Track A started (Phase 0 BE-0c + Phase 1 BE-1/BE-2).
