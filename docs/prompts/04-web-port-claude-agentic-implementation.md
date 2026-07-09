# Prompt 4: Chrome Web Port Claude Agentic Implementation

TTRAN publication:

- TTRAN ID: `AA28`
- Alpuca store: `/Volumes/PortoSams2T/ttran`
- Primary `ttran` host alias: `alpuca-ts`
- Verified Tailscale fallback: `alpuca@100.74.59.97`
- Tailscale UI also lists MagicDNS: `alpuca.tail9c9221.ts.net`
- Retrieve from any Codex or Claude session with Alpuca SSH access:
  - Stream text: `ttran cat AA28`
  - Download file: `ttran get AA28 <destination-directory>`
  - If the `alpuca-ts` alias is missing:
    `TTRAN_HOST=alpuca@100.74.59.97 ttran cat AA28`
- Local source copy:
  `docs/prompts/04-web-port-claude-agentic-implementation.md`

You are working in the LangBangML fork:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

Goal: implement a Chrome-first LangBangML web app in phases, starting with an
online MVP and ending with a downloadable local/offline package that can run in
Chrome without Android. This is a TTRAN-style implementation handoff for a
Claude agentic coding session. It is meant to be executed, not treated as a
brainstorming note.

Do not ask the user to do steps you can do. Do not route any work through the
legacy `/Users/rahulio/Documents/CodingProjects/langbang` checkout. Do not
publish artifacts to the old `alpacapps` R2 bucket. Do not print or commit
secrets. Keep the current Android/R2 tablet debug lane working while adding the
web lane.

## Current Context

LangBangML is the Cloudflare-backed LangBang fork. Current backend:

```text
Worker: https://langbangml-api.langbangml.workers.dev
D1 database: langbangml
R2 bucket: langbangml
Public R2 base: https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev
Main R2 prefix: langbang/
```

Current Android instances:

```text
enPl package: com.sponic.langbangml.enpl
enPl instance: langbangml-en-pl
plEn package: com.sponic.langbangml.plen
plEn instance: langbangml-pl-en
```

The Worker already exposes most data and service boundaries a web port needs:

- `GET /v1/instances`
- `GET /v1/instances/{instanceId}/bootstrap`
- `POST /v1/audio/manifest`
- `POST /v1/phrases/complete`
- `POST /v1/auth/google`
- `POST /v1/auth/email/start`
- `POST /v1/auth/email/verify`
- `GET /v1/me/content`
- `GET/PUT /v1/me/phrases`
- `POST /v1/me/agent-token`
- `GET/POST/DELETE /v1/agent/phrases`
- `GET/POST/DELETE /v1/agent/words`

The Android client is Jetpack Compose/Kotlin. The web port should not try to
compile or reuse Compose UI. Reuse product shape, JSON models, backend routes,
R2 audio behavior, and playback semantics.

## Chrome-Only Decision

The first web lane targets current stable Chrome only. That allows:

- `HTMLAudioElement` or Web Audio for playback.
- `CacheStorage` and service workers for app-shell and audio/content caching.
- `IndexedDB` for lessons, user prefs, starred phrases, custom phrases, and
  downloaded audio metadata.
- Chrome File System Access API in later phases for import/export and local
  bundle workflows.

Do not spend MVP time on Safari, Firefox, iOS PWA behavior, cross-browser media
quirks, or a Trusted Web Activity. Treat those as optional future work.

## Architecture Recommendation

Create a new web app at:

```text
web/
```

Recommended stack:

- Vite + React + TypeScript.
- `idb` or a tiny local wrapper around IndexedDB.
- Plain CSS modules or scoped CSS. Do not introduce a heavy design system.
- Playwright Chromium for verification.
- Workbox only when the offline phase begins, unless a hand-written service
  worker stays smaller and clearer.

Expected directory shape:

```text
web/
  package.json
  index.html
  vite.config.ts
  playwright.config.ts
  public/
    manifest.webmanifest
    icons/
  src/
    main.tsx
    app/App.tsx
    app/routes.ts
    cloud/cloudClient.ts
    cloud/models.ts
    data/localStore.ts
    data/contentRepository.ts
    audio/audioManifest.ts
    audio/audioCache.ts
    player/studyQueue.ts
    player/nowVoicing.ts
    ui/
      NowVoicingPanel.tsx
      PlaybackToolbar.tsx
      LessonShell.tsx
      WordAlignedText.tsx
      VariablePolishText.tsx
    lessons/
      PhrasesPage.tsx
      VerbsPage.tsx
      PronunciationPage.tsx
      AdjectivesPage.tsx
      AdverbsPage.tsx
      NounsPage.tsx
      NumbersPage.tsx
    settings/
      SettingsPage.tsx
  scripts/
    build-offline-package.mjs
```

If Claude finds a clearly better local pattern after reading the repo, it may
adjust file names, but keep the web app isolated under `web/` and keep Android
source edits minimal.

## Core Product Shape To Preserve

The first screen should be the usable learning app, not a marketing landing
page. The public website can have marketing and SEO pages later, but `/app` or
the web app root should open directly into study mode.

Preserve these LangBang behaviors:

- Speaking-first learning, with visible Now Voicing state.
- English/Polish direction comes from instance bootstrap.
- Lessons are grouped similarly to Android: pronunciation, verbs, phrases,
  adjectives, adverbs, nouns, numbers/quizzes where practical.
- Play queues support play, stop, pause/resume, next, rewind, and loop.
- Now Voicing shows source cue, target answer, literal gloss, position, and
  token/word metadata when available.
- Playback preferences persist: English cue on/off, slow target on/off, loop,
  per-category checked words, random order, and count/variation limit.
- Audio uses cached R2/generated mp3s via the Worker, not client-shipped Azure
  keys.
- Custom phrase and word features stay user-owned. Do not mutate global content
  from the public web app.

## What Not To Build In MVP

Do not block Phase 1 on:

- Microphone pronunciation scoring.
- Full Google sign-in UX.
- Email magic-code sign-in.
- User phrase cloud sync.
- Agent API token management.
- Offline zip export/import.
- Complete Android parity for every filter and every quiz.
- Non-Chrome support.
- Play Store release work.

These are later phases unless a small piece is required to unblock the MVP.

## Phase 0: Audit And Scaffold

Target effort: 0.25 to 0.5 Claude agent day.

Deliverables:

1. Read the source-of-truth Android and backend files:
   - `AGENTS.md`
   - `app/src/main/kotlin/com/sponic/langbang/cloud/CloudModels.kt`
   - `app/src/main/kotlin/com/sponic/langbang/cloud/CloudBackendClient.kt`
   - `app/src/main/kotlin/com/sponic/langbang/data/model/`
   - `app/src/main/kotlin/com/sponic/langbang/domain/AudioManifest.kt`
   - `app/src/main/kotlin/com/sponic/langbang/ui/common/StudyQueuePlayer.kt`
   - `app/src/main/kotlin/com/sponic/langbang/domain/NowVoicing.kt`
   - `cloudflare/langbangml/src/index.js`
   - `app/src/main/assets/lesson-*.json`

2. Create `web/` scaffold:
   - Vite React TypeScript app.
   - `npm run dev`, `npm run build`, `npm run test` if tests are added.
   - Playwright Chromium config with desktop and tablet-ish viewports.
   - Strict TypeScript where practical.

3. Port model types:
   - `SentenceExample`
   - `TokenPair`
   - `PhraseGroup`
   - `VerbEntry`
   - `AdjectiveEntry`
   - `AdverbEntry`
   - `NounEntry`
   - `PronunciationData`
   - `CloudBootstrap`
   - `CloudInstanceSummary`
   - user content response shapes

4. Add a web content repository:
   - First tries `/v1/instances/{instanceId}/bootstrap`.
   - Falls back to bundled JSON assets copied from `app/src/main/assets`.
   - Normalizes `sourceField`, `targetField`, and locale metadata.
   - Persists the last good bootstrap in IndexedDB.

5. Add an implementation status note:
   - `web/docs/status.md` or equivalent.
   - Record what is built, what is stubbed, how to run, and what was verified.

Verification:

```bash
cd web
npm install
npm run build
npx playwright test
```

If Playwright is not added in Phase 0, explain why in the status note and add it
before Phase 1 is considered done.

## Phase 1: Online MVP Web App

Target effort: 0.75 to 1.5 Claude agent days.

Goal: a Chrome web app that is useful online and can be deployed live. It should
let a learner open the app, choose EN-PL or PL-EN if needed, browse core
lessons, and play audio queues with Now Voicing.

Build:

1. App shell and navigation:
   - Top navigation for lesson families.
   - Direction/instance switcher.
   - Current connection/content status.
   - Settings surface for playback preferences.
   - Responsive layout for desktop and tablet-width Chrome.

2. Now Voicing and shared playback:
   - Implement `nowVoicing` store, equivalent to Android `NowVoicingBus`.
   - Implement `studyQueue` equivalent to Android `StudyQueuePlayer`.
   - Controls: stop, pause/resume, next, rewind, loop.
   - Preferences: English cue, slow target, loop.
   - Queue item publishes Now Voicing before each spoken segment.
   - One-off word taps should not destroy an active queue. If a one-off tap is
     allowed during queue playback, park the current item and resume cleanly.

3. Audio:
   - Use `/v1/audio/manifest` to resolve mp3 URLs for target/source phrases.
   - Use `HTMLAudioElement` for playback first.
   - Cache fetched audio blobs in CacheStorage or IndexedDB.
   - Add visible cache/download progress only when the user asks to pre-cache,
     not as a blocking first-run operation.
   - Do not expose Azure/Gemini keys in browser code.

4. Lessons:
   - Phrases: render phrase groups, phrase rows, play one, play group/all.
   - Verbs: render core verb list and present forms first; add sentence/phrase
     playback if bootstrap content is present.
   - Nouns: render declension forms and play forms even if examples are absent.
   - Adjectives/adverbs: render lists and example rows/forms.
   - Pronunciation: render phoneme examples and play sample words. Mic scoring
     can be deferred.
   - Numbers: implement only if cheap; otherwise document as Phase 2.

5. Custom local content:
   - Add local-only custom phrase group create/edit/delete.
   - Persist in IndexedDB.
   - Keep cloud sync deferred unless auth is implemented.

6. Public deployment:
   - Add script for a live preview/deploy path.
   - Recommended: Cloudflare Pages or R2-hosted static build under the current
     new Cloudflare account.
   - Do not deploy to old `alpacapps`.
   - After deploy, verify the public URL in Chrome/Playwright.

Definition of done:

- `npm run build` passes.
- Playwright opens the live or local app in Chromium and verifies:
  - app shell renders nonblank
  - instance/bootstrap loads or fallback loads
  - at least one phrase plays or resolves an audio URL
  - Now Voicing updates during playback
  - no console errors in the core flow
  - tablet viewport has no overlapping text/controls
- Live URL is reported in final/status.
- Android build/publish scripts are not broken.

## Phase 2: Feature Parity Push

Target effort: 1 to 3 Claude agent days.

Goal: make the web app feel like LangBang, not a thin content browser.

Build:

1. Full lesson controls:
   - Per-category checked lists.
   - Random order.
   - Variation count steppers.
   - Verb filters: pronouns, helper verb, nouns, adjectives, adverbs where the
     Android screen supports them.
   - Noun/adjective/adverb filters matching accepted Android behavior.

2. Phrase generation and completion:
   - Use `/v1/phrases/complete` for manual phrase editing assistance.
   - If signed in, use `/v1/me/phrases/ai-generate` for custom AI phrases.
   - Preserve local-only behavior when not signed in.
   - Add clear quota/error handling without surfacing secrets.

3. Auth and sync:
   - Email code sign-in is likely simpler than Google sign-in for web MVP.
   - Add Google sign-in only if the web OAuth client config is already valid or
     can be created without blocking.
   - Implement `/v1/me/content` and `/v1/me/phrases` sync.
   - Make conflict behavior explicit: local wins, remote wins, or merge.

4. Agent API:
   - Add an account/settings panel that can create/rotate an agent token via
     `/v1/me/agent-token`.
   - Show the instructions URL from the backend.
   - Never show a token again after creation unless the backend returns it.
   - Give copyable examples, but avoid raw admin tokens.

5. Grammar visuals:
   - Port `WordAlignedPolish`, variable endings, gender/case coloring, and
     syllable shading where data is available.
   - Keep layout dense and usable. Do not use decorative hero/card-heavy UI.

6. Quizzes:
   - Add sentence audio quiz.
   - Add recall drills for verbs, nouns, and adjectives.
   - Add end-quiz action and deterministic cleanup of active audio.

Verification:

- Unit tests for queue sequencing and content normalization.
- Playwright tests for each lesson family.
- Manual Chrome audio smoke test.
- Console/network audit for missing CORS, failed audio, or stale bootstrap.

## Phase 3: Offline Web App

Target effort: 1 to 2 Claude agent days.

Goal: after a user opens/downloads the app once, Chrome can run the study app
with cached lessons and selected audio while offline.

Important technical boundary:

- A true PWA/service worker works on `https://` and `localhost`.
- Do not promise that a double-clicked `file://.../index.html` package will have
  full service-worker offline behavior.
- For a downloadable local package, include a tiny local server script and tell
  the user to open `http://127.0.0.1:<port>` in Chrome.

Build:

1. Service worker:
   - Precache app shell assets with revision hashes.
   - Cache last-good bootstrap/content JSON.
   - Cache selected audio on demand.
   - Add a versioned cache cleanup strategy.
   - Add an offline fallback screen only for missing uncached routes.

2. Offline settings:
   - "Download for offline" button.
   - Let user select:
     - app shell only
     - current lesson
     - all visible lessons
     - selected audio only
     - all common audio for current direction
   - Show size estimate before downloading where feasible.
   - Show progress and failures.
   - Add "Clear offline data" and "Verify offline data" actions.

3. Storage:
   - Store metadata in IndexedDB:
     - content version
     - instance id
     - lesson ids cached
     - audio keys cached
     - byte counts
     - last verification time
   - Store audio in CacheStorage when fetched by URL, or IndexedDB blobs if URL
     cache semantics become awkward.
   - Guard against Chrome quota failures and show partial-cache state.

4. Offline mode behavior:
   - If offline and content exists, boot from IndexedDB immediately.
   - Disable server-only actions: AI generation, phrase completion, sign-in,
     sync, agent token creation, audio that has not been downloaded.
   - Playback must never silently no-op. Missing offline audio should show a
     specific "not downloaded" state.

5. Local downloadable package:
   - Add `web/scripts/build-offline-package.mjs`.
   - Produce `dist/langbang-web-offline.zip` containing:
     - static web build
     - selected lesson JSON
     - selected audio manifest
     - optional audio files if package size is acceptable
     - `serve-offline.sh`
     - `serve-offline.ps1`
     - `README-offline.md`
   - `serve-offline.sh` may use Python's built-in HTTP server if available.
   - The package must run at `http://127.0.0.1:<port>` in Chrome.

Verification:

- Playwright installs/loads app online.
- Playwright switches Chromium context offline and verifies cached app boot.
- Offline playback succeeds for downloaded audio.
- Missing non-downloaded audio shows an explicit disabled/error state.
- Local zip is unpacked and served from localhost; app boots without internet.

## Phase 4: Discovery And Public Web Growth

Target effort: 0.5 to 1.5 Claude agent days for the first pass.

Goal: create a web presence that can produce discovery traffic, not only a
private app shell.

Core point: a pure SPA/PWA app shell has weak search discovery because there is
little indexable, useful public content. A web version can beat Play discovery
only if it exposes useful, crawlable pages.

Build:

1. Public pages:
   - `/` public product page.
   - `/app` web app.
   - `/learn-polish`
   - `/polish-phrases`
   - `/polish-pronunciation`
   - `/polish-verbs`
   - `/polish-nouns`
   - Equivalent PL-EN pages if useful.
   - Privacy/terms links if not already live.

2. SEO basics:
   - Descriptive URLs.
   - Unique titles and meta descriptions.
   - Crawlable HTML content, not only client-rendered placeholders.
   - Internal links between public pages and `/app`.
   - Canonical URLs.
   - Open Graph/Twitter metadata.
   - Sitemap and robots.txt.
   - Useful sample phrases and pronunciation content, written for learners.

3. Analytics:
   - Cloudflare Web Analytics or existing Worker analytics.
   - Separate events for:
     - public page visit
     - app open
     - first audio play
     - offline download started/completed
     - sign-in started/completed
   - Do not over-collect. Match privacy policy.

4. Discovery estimate model:
   - Baseline Android Play organic for a new, low-review niche app: `1x`.
   - Web app shell only: roughly `0.3x to 1x` Play organic because it lacks app
     store browse and has little indexable content.
   - Web with useful indexable lesson/phrase/pronunciation pages: roughly
     `2x to 10x` Play organic over the first 90 to 180 days, assuming it is
     crawlable, linked, measured, and has pages matching learner search intent.
   - These are planning estimates, not guaranteed traffic. Measure with Google
     Search Console, Cloudflare analytics, and Play Console acquisition data.

5. Measurement plan:
   - Add Search Console for `langbang.org`.
   - Track impressions, clicks, queries, and landing pages weekly.
   - Track Play Console store listing visitors/acquisitions weekly.
   - Compare discovery by first-touch source, not total sessions.
   - Revisit the `2x to 10x` estimate after 30, 60, and 90 days.

Verification:

- Public pages return real HTML with titles/meta.
- `site:` query indexing can be checked later; do not block deploy on indexing.
- Lighthouse/Chrome DevTools basic PWA and SEO checks have no obvious blockers.

## Phase 5: Optional Expansion

Do this only after the Chrome web app is useful and measured.

Options:

- Safari/iOS compatibility pass.
- Firefox compatibility pass.
- Android Trusted Web Activity wrapper.
- Web microphone pronunciation scoring.
- Bulk offline audio packs by lesson/direction.
- Shared component/data model generation between Android and web.
- Dedicated web admin/content editor.
- Multi-language landing pages.

## Estimated Agentic Effort

The earlier "3 to 5 weeks" estimate is too high if interpreted as focused
Codex/Claude implementation time. The existing Android app moved quickly because
agentic work compressed a lot of coding into a small number of focused sessions.

Practical estimate for a Claude agentic implementation:

```text
Phase 0 scaffold/audit:         0.25 to 0.5 agent day
Phase 1 online MVP:             0.75 to 1.5 agent days
Phase 2 feature parity push:    1 to 3 agent days
Phase 3 offline/download:       1 to 2 agent days
Phase 4 public discovery pages: 0.5 to 1.5 agent days
```

Expected elapsed time depends on review, network/deploy failures, and how much
tablet/browser QA the user wants between phases. A credible first online MVP is
closer to 1 to 3 elapsed days than 3 to 5 calendar weeks. Full parity plus
offline packaging is more like 4 to 10 elapsed days if Claude keeps executing
and verification is done seriously.

## Risks And Decisions

1. Audio size and browser storage quota.
   - Risk: full audio cache can be large.
   - Decision: MVP downloads audio on demand; offline phase lets user choose
     current lesson/current direction/all common audio.

2. Service worker/local package confusion.
   - Risk: user expects double-clicked `file://` to work offline.
   - Decision: document and implement localhost package runner. PWA offline
     install works from HTTPS; local offline package works from localhost.

3. Feature parity sprawl.
   - Risk: cloning every Android surface blocks the web MVP.
   - Decision: Phase 1 prioritizes useful playback and core lessons. Auth,
     sync, AI generation, and mic scoring are Phase 2+.

4. Backend cost exposure.
   - Risk: public browser app can call generation/audio endpoints more broadly.
   - Decision: do not add unauthenticated generation-heavy flows beyond what the
     Worker already intentionally exposes. Prefer authenticated user flows for
     AI generation.

5. SEO vs app UX conflict.
   - Risk: turning the app into a marketing page harms actual learning UX.
   - Decision: app stays `/app`; public crawlable content pages live around it.

6. Android regression.
   - Risk: web work accidentally breaks Android build/publish lane.
   - Decision: keep web files isolated. If Android code is touched, run the
     relevant Gradle compile and `scripts/check-tablet-regressions.sh`.

## Required Verification Commands

For web-only work:

```bash
cd web
npm install
npm run build
npx playwright test
```

For any Android-touching work:

```bash
scripts/check-worktree-integrity.sh --allow-current-dirty
scripts/check-tablet-regressions.sh
./gradlew :app:compileEnPlDebugKotlin :app:compilePlEnDebugKotlin
```

For public deploy verification:

```bash
curl -fsS https://langbang.org/app | head
curl -fsS https://langbang.org/learn-polish | head
```

Use Playwright Chromium screenshots for visual proof. Do not claim the web app is
verified from build output alone.

## Implementation Order For Claude

1. Read this doc and `AGENTS.md`.
2. Run `git status --short` and preserve existing dirty work.
3. Scaffold `web/`.
4. Implement data models and bootstrap loading.
5. Implement app shell, lesson navigation, and Now Voicing.
6. Implement audio manifest lookup and playback.
7. Implement Phrases plus at least one word-form lesson, preferably Nouns or
   Verbs.
8. Add Playwright tests and verify in Chromium.
9. Deploy or produce a live preview if deploy credentials/path are available.
10. Update `web/docs/status.md` with exact done/not-done state.
11. Continue to Phase 2 only after Phase 1 is usable and verified.

## Official References

- Chrome File System Access API:
  `https://developer.chrome.com/docs/capabilities/web-apis/file-system-access`
- Chrome DevTools PWA debugging:
  `https://developer.chrome.com/docs/devtools/progressive-web-apps`
- MDN PWA offline/background operation:
  `https://developer.mozilla.org/docs/Web/Progressive_web_apps/Guides/Offline_and_background_operation`
- MDN PWA caching:
  `https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Caching`
- Workbox:
  `https://developer.chrome.com/docs/workbox`
- Google Search Essentials:
  `https://developers.google.com/search/docs/essentials`
- Google SEO Starter Guide:
  `https://developers.google.com/search/docs/fundamentals/seo-starter-guide`
