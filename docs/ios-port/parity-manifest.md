# LangBang iOS KMP Port — Parity Manifest (Pass 1)

**Source of truth:** LangBangML `app/src/main` (Kotlin + Compose) + worker `cloudflare/langbangml/src/index.js`.
**Goal:** exhaustive enumeration of **user-facing** features for verification of the iOS port. Each entry: Name | Source file(s) | One-line behavior | Backend dep.

> G2 glasses / translator modules are noted but marked **out of scope for the phone app** in this port (they are external integrations).

---

## App Shell & Navigation

- **Main tab bar (top pills)** | `ui/LangbangApp.kt:121` (TabSections) + `AppHeader` | Horizontal scrollable top tab strip with 9 sections; Settings is a toggle that replaces the body. Selecting Quizzes twice resets quiz state. | None (local)
- **Global Now Voicing panel** | `ui/LangbangApp.kt:329`, `ui/common/NowVoicingPanel.kt`, `domain/NowVoicing.kt`, `domain/NowVoicingBus.kt`, `PlaybackController.kt` | Sticky right column (or top in some layouts) that shows current EN/PL/literal while any audio source is active. Provides transport (stop/rewind/next/pause/resume/restart) + star toggle. Silences on tab switch. | Optional (stars + user phrases sync when signed in)
- **Random "Play Phrases" pill + config** | `ui/LangbangApp.kt:553`, `ui/RandomPlayer.kt`, `ui/RandomConfigSheet.kt`, `data/RandomConfigStore.kt` | Top-right pill launches configurable random mix (verbs/adjs/advs/nouns/phrases). Config includes tenses, persons, pronoun filters, adjectives/adverbs include modes, must-contain word, syllable shading. | R2 audio + cached sentence bundles (Gemini pregen)
- **Offline banner** | `ui/LangbangApp.kt:302` | Red banner when `NetworkMonitor` reports offline; disables generation + synth. | None
- **Sentence regen banner** | `ui/LangbangApp.kt:708` (SentenceRegenBanner), `domain/SentenceRegenService.kt` | Yellow banner while downloading R2 sentence bundles; retry on tap on failure. | R2 via manifest + bundles
- **Title tap → self-update (debug)** | `ui/LangbangApp.kt:249`, `domain/UpdateChecker.kt` | Tap wordmark checks R2 manifest; downloads + launches APK installer if newer. Debug only. | R2 update manifest JSON
- **Version / flavor badge** | `BuildConfig` + `LangbangApp.kt:547` | Shows "EN/PL" or "PL/EN" + `v{BUILD_NUMBER}`. | None

## Pronunciation Tab

- **PronunciationScreen** | `ui/pronunciation/PronunciationScreen.kt` | Lists phoneme/lesson items; tap plays audio. Supports mic for pronunciation assessment. | Azure Speech (SDK) via `/v1/azure/speech-token` for short-lived token; R2 audio
- **PhonemeQuiz** | `ui/pronunciation/PhonemeQuiz.kt` | Tap-to-answer or mic quiz over pronunciation items. | Same as above

## Numbers Tab

- **NumbersScreen** | `ui/numbers/NumbersScreen.kt` | Dedicated drill for numbers (cardinal/ordinal forms, listening + recall). | Local + R2 audio

## Verbs Tab (LessonScreen → VerbsTab)

- **Lesson list + detail playback** | `ui/lessons/VerbsTab.kt`, `ui/lessons/LessonScreen.kt`, `ui/common/StudyQueuePlayer.kt`, `data/model/VerbEntry` | Browse verbs; tap plays EN cue → (optional delay) → slow PL → normal PL with aligned words. Supports conjugation display. | Lesson JSON (bundled + cloud bootstrap), R2 audio or on-device Azure TTS, Gemini for sentence regen
- **Conjugation display + grammar visuals** | `ui/common/GrammarVisuals.kt`, `data/model/ConjugationClass.kt`, `domain/EnglishConjugator.kt` | Shows 6 persons present/past; visual helpers. | None (local logic + pregen sentences)
- **Random sentence playback per verb** | `ui/lessons/VerbsTab.kt` + `VerbSentenceStore` | Per-verb "play sentences" queue using cached Gemini-generated examples. | Gemini via worker `/v1/gemini/generate` (cached in R2 bundles)

## Adjectives Tab

- **AdjectivesScreen** | `ui/lessons/AdjectivesScreen.kt` | List adjectives; playback of forms + generated sentences. | Same as verbs: asset/cloud lessons + Gemini sentence bundles
- **Adjective form + sentence quiz support** | `ui/quizzes/PracticeGenerators.kt` (adjectiveFormItems, adjectiveSentenceItems) | Drills adjective case/gender/number agreement in context. | Same

## Adverbs Tab

- **AdverbsScreen** | `ui/lessons/AdverbsScreen.kt` | Parallel to adjectives for adverb usage. | Same

## Nouns Tab

- **NounsScreen** | `ui/lessons/NounsScreen.kt` | Noun case drills and example sentences. | Same pattern

## Phrases Tab

- **PhrasesScreen + groups** | `ui/phrases/PhrasesScreen.kt` | Browse phrase groups (default + user). Supports play-all, search, star, edit, add custom. | Cloud bootstrap for defaults; `/v1/me/content`, `/v1/me/phrases` for user groups/stars
- **PhraseDetail + playback** | `ui/phrase/PhraseDetail.kt` | Detail card: EN/PL/literal + word tokens; play controls, slow/normal, mic pronunciation. Star toggle. | Audio (R2 or Azure), optional `/v1/phrases/complete`
- **Custom phrase add + LLM complete** | `PhrasesScreen` + `CloudBackendClient.completePhrase` | User enters 1–3 fields (source/target/literal); calls `/v1/phrases/complete` to fill consistent Polish/English. | POST `/v1/phrases/complete`
- **Starred phrases** | `data/StarredPhrasesStore.kt`, `PhrasesScreen`, `LangbangApp` | Global star set used for quizzes and "my phrases". Toggle from NowVoicing panel or detail. Syncs when signed in. | `/v1/me/phrases` + `/v1/me/content`
- **Phrase group sync** | `cloud/PhraseSyncService.kt`, `cloud/CloudBackendClient` (fetchUserContent, syncUserPhrases, generateAiPhrases) | On sign-in + after edits: pull remote groups/stars/words or push local. AI-generate phrases into a group. Quota request flow. | Auth required: `/v1/me/content`, PUT `/v1/me/phrases`, POST `/v1/me/phrases/ai-generate`, `/v1/me/phrases/ai-quota*`

## Quizzes Tab

- **Quizzes hub + mode switch** | `ui/quizzes/QuizzesScreen.kt` | Card list of modes; selecting replaces body with `MultipleChoiceQuiz` or `PracticeQuiz`. Reset via double-tap tab. | None (local generators + cached sentences)
- **Practice (recall / self-graded)** | `ui/quizzes/PracticeQuiz.kt`, `PracticeModel.kt`, `PracticeGenerators.kt` | Production recall: show English cue (or Polish), user attempts, self-grade. Covers verbs, nouns, adjs, advs, phrases. | Cached Gemini sentence bundles
- **Helper-verb + infinitive practice** | `PracticeGenerators.helperInfinitiveItems` | Drills "muszę + inf", "chcę + inf" patterns etc. | Same
- **Verb conjugation quiz (per-verb, all persons)** | `QuizGenerators`, `PracticeGenerators.verbFormItems` | One verb → 6 persons in chosen tense(s). | Same
- **Verb forms across verbs (per person)** | `verbFormItems` variant | One person slot across many verbs. | Same
- **Pronoun case quiz** | `PracticeGenerators` pronoun handling + `MultipleChoiceQuiz` | ja/mnie/mi etc. case forms. | Local pronoun data
- **Noun / adjective / adverb form quizzes** | Dedicated generators + `QuizModel` | Case/gender/number agreement in sentence context. | Cached sentences
- **MultipleChoiceQuiz vs PracticeQuiz** | `MultipleChoiceQuiz.kt`, `PracticeQuiz.kt` | MCQ tap-to-answer vs free recall + self-grade. | Same

## Numbers Tab (already listed)

## External / LLM Tab ("Now Voicing" live)

- **ExternalNowVoicingScreen** | `ui/external/ExternalNowVoicingScreen.kt`, `domain/ExternalNowVoicing.kt` | Live LLM chat-like interface; Gemini-backed generation of Polish sentences from English prompts or freeform. Can receive external "voicing" intents. | POST `/v1/gemini/generate`; also WebSocket `/v1/gemini/live` (for G2 relay)
- **Agent token + external API** | `ui/settings/AgentApiCard` (shown in External too), `cloud/CloudBackendClient.createAgentToken` | User can create/rotate a long-lived `agentToken` for Claude/Codex/script use against `/v1/agent/*`. Daily quota shown. Instructions page served by worker. | Auth + `/v1/me/agent-token`; public `/agent` docs; agent routes use separate token auth
- **G2 glasses / translator modules** | `ExternalNowVoicingReceiver.kt`, captures/*, docs/even-g2-integration.md, g2trans/, even-g2-test/ | Broadcast receiver and webview test harness for G2 smart glasses real-time translation overlay + teleprompt. **Out of scope for phone KMP port.** | Same backend + extra bridges

## Settings Screen

- **Account card (Google + Email code)** | `ui/settings/SettingsScreen.kt`, `AccountSyncCard` | Google Sign-In (Credential Manager), email magic-code start/verify, sign-out, delete account. Shows user info. | All `/v1/auth/*` + `/v1/me` (DELETE)
- **Agent API card** | `AgentApiCard` | Create/rotate agent token, copy, view quota + instructions link. | `/v1/me/agent-token`
- **Pronoun filters** | `data/PronounFilterStore.kt` | Per-person toggles (1sg..3pl) that affect random/quiz sentence selection. | Local (synced via user content when signed in)
- **Regenerate sentences** | Settings + `GeminiClient` + `SentenceRegenService` | Force re-fetch of verb/adj/adv/noun sentence bundles from R2 (or trigger regen). Per-type wipe versions. | R2 + `/v1/gemini/generate` (admin or cached)
- **Audio / slow style prefs** | `data/AudioPrefsStore.kt`, `SlowStyle` | Choose normal / slow50 / slow60 / articulate for target voice. | Affects audio key + playback
- **Usage / quota** | `domain/UsageTracker.kt` | Tracks Gemini / Azure / complete calls (local + surfaced). | Local counters; some server-side quotas on agent
- **Backup (SFTP)** | `domain/BackupService.kt` | Optional upload of local data to ALPUCA (dev only). | SFTP (jsch)
- **Cloud instance switcher** | `CloudConfigStore`, `LangbangApplication.syncCloudConfig` | Switch between en-pl / pl-en (and potentially others from `/v1/instances`). Reloads bootstrap + user data. | `/v1/instances`, `/v1/instances/{id}/bootstrap`
- **Analytics opt / events** | `analytics/ProductAnalytics.kt` | Batched events to `/v1/analytics/events`. Profile tied to auth. Admin summary endpoints exist. | POST `/v1/analytics/events`; admin GETs protected
- **App update / version info** | UpdateChecker + title tap | As above.

## Audio / Playback / TTS

- **AudioPlayer (MediaPlayer reuse + pause/resume)** | `domain/AudioPlayer.kt` | Single reused MediaPlayer; true pause that preserves position; onDone callbacks. | Local files only
- **R2-first audio + manifest** | `domain/R2AudioDownloader.kt`, `domain/AudioCache.kt`, worker `audioManifest` | POST list of (text,voice,locale) → worker returns R2 (or freshly synthesized) URLs + side-loads to R2. Client downloads in batches. | POST `/v1/audio/manifest`; public R2 `https://pub-*.r2.dev/langbang/...`
- **Azure TTS fallback** | `integrations/AzureTtsClient.kt` | Direct REST SSML call when R2 miss (or for slow variants). Key only in debug; prod uses worker token. | Direct Azure or `/v1/azure/speech-token`
- **Azure Pronunciation (STT + assessment)** | `integrations/AzurePronunciationClient.kt`, `AzureSpeechAuth.kt` | Streaming mic recognition + pronunciation score. Uses short-lived token from worker. | `/v1/azure/speech-token`
- **Transport abstraction** | `PlaybackController`, `PlaybackTransport` | Any queue (random, quiz, lesson) registers stop/next/rewind/pause callbacks so the global panel works uniformly.
- **Prefetch on launch** | `domain/PrefetchService.kt`, `PrefetchWorker.kt` (WorkManager) | Downloads missing audio for current lesson set in background. Progress exposed. | R2 + manifest

## Auth & Identity Flows

- **Google Sign-In** | `cloud/GoogleSignInHelper.kt` (Credential Manager + GoogleId), `CloudBackendClient.signInWithGoogle` | Request idToken + nonce → POST `/v1/auth/google` → receive `sessionToken` + user. Nonce is verified server-side. | Worker verifies against Google JWKS + `GOOGLE_WEB_CLIENT_ID` audience allowlist (comma-separated, supports multiple client IDs)
- **Email code auth** | `startEmailSignIn` / `verifyEmailSignIn` | POST start → code sent; verify with code → same auth response. | `/v1/auth/email/start`, `/v1/auth/email/verify`
- **Session model** | `AuthStore.kt`, worker `createSession` | `sessionToken` (lb_...) stored locally; `expiresAt`. Sent as `Authorization: Bearer`. Worker creates random token + hash in D1. Default 90 days.
- **Sign-out** | `signOut` | POST `/v1/auth/sign-out` with bearer; clears local state. | Same
- **Delete account** | `deleteAccount` | DELETE `/v1/me` (or POST /delete) with bearer. | Same
- **Agent token split** | `createAgentToken` | Separate from sessionToken. Created with bearer session; used for `/v1/agent/*` routes. Has daily quota. | `/v1/me/agent-token`
- **User content merge** | `fetchUserContent`, `syncUserPhrases` | Pulls groups + starred + custom words for the selected instance. | Auth required

## Data / Content Model

- **Bootstrap + instances** | `CloudBackendClient.fetchBootstrap`, worker `bootstrap` | `/v1/instances/{id}/bootstrap` returns lessons (verbs/phrases/etc as JSON), labels, audio config (manifestEndpoint, publicR2Base, audioPrefix), languagePair voices. | Public GET
- **Lessons & entries** | `data/model/*` (VerbEntry, NounEntry, …), `data/LessonRepository.kt`, assets `lesson-*.json` | Bundled seeds + cloud overrides. User words (verbs/nouns/…) stored separately and merged. | Bootstrap + `/v1/me/content`
- **Sentence stores (Gemini pregen)** | `VerbSentenceStore`, `SentenceStore`, `R2SentenceManifest` | Per-type caches of example sentences. Versioned wipes. | Downloaded from R2 on demand/launch
- **Starred / groups persistence** | `StarredPhrasesStore`, `JsonListStore` for user-phrases.json | Local JSON; pushed/pulled on sync when signed in.

## Analytics

- **ProductAnalytics** | `analytics/ProductAnalytics.kt`, `ProductAnalyticsClient` | Event batching (name, feature, action, properties). Flushed on pause + periodically. Profile bound to auth user. | POST `/v1/analytics/events` (rate limited)

## Other / Misc

- **NetworkMonitor** | `domain/NetworkMonitor.kt` | Simple connectivity listener.
- **PrefetchWorker** | WorkManager one-time; constrained to connected.
- **VoicingMediaSession** | Android media session keys (headset) handled in MainActivity.
- **AdbWifi keepers** (tablet dev) | Debug-only; ignore for phone port.
- **Two flavors** | enPl / plEn with different instance IDs and update manifests. iOS port will likely select instance at runtime or via build config.

## Explicitly Out of Scope (phone app)

- G2 glasses integration, teleprompt, webview test harness, g2trans Python bridge, even-g2-test.
- Tablet-specific ADB wifi receivers and sideload flows (keep the concept of self-update but iOS will use different mechanism later).
- Any direct Play Store / billing.
- Admin endpoints and content management UIs.

---

**Backend surface summary (for cross-ref with backend-contract.md):** auth (google/email), instances/bootstrap, me/content + phrases (CRUD + AI), phrases/complete, gemini/generate (+live WS), audio/manifest, azure/speech-token, analytics/events, agent/* (token-gated), sign-out, delete me.

**Verification note:** Every screen, quiz generator path, audio path (R2 + Azure), auth path, and settings toggle above must have a corresponding iOS SwiftUI + shared Kotlin implementation before declaring feature parity.
