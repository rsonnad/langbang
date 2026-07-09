# LangBang Web — implementation status

Chrome-first web port of LangBang, per the TTRAN handoff (ttran `AA28`,
`docs/prompts/04-web-port-claude-agentic-implementation.md`). Lives under `web/`,
isolated from the Android app. Brand is **LangBang** throughout the UI.

**Live:** https://langbang.org/app (behind a login gate). Landing page at
https://langbang.org/ is unchanged.

_Last updated: 2026-06-08._

## What's built (Phase 0 + Phase 1 + login gate)

- **Scaffold** — Vite + React + TypeScript (strict), single-file build via
  `vite-plugin-singlefile` → one self-contained `dist/index.html` (~185 kB,
  ~59 kB gzip). Playwright Chromium config (desktop + tablet viewports).
- **Models** (`src/cloud/models.ts`) — ported from `data/model/Lesson.kt`,
  `Pronunciation.kt`, and `cloud/CloudModels.kt`. Content fields are role-named
  (`en` = source cue, `pl` = target answer) in **both** directions; voices bind
  by role (`sourceVoice`→`en`, `targetVoice`+slow→`pl`), matching `AudioManifest.kt`.
- **Cloud client** (`src/cloud/cloudClient.ts`) — public Worker routes:
  `/v1/instances`, `/v1/instances/{id}/bootstrap`, `/v1/audio/manifest`,
  `/v1/phrases/complete`, and auth (`/v1/auth/google`, `/v1/auth/email/start`,
  `/v1/auth/email/verify`, `/v1/auth/sign-out`). CORS is `*`, so the SPA calls
  the Worker cross-origin.
- **Content repository** (`src/data/contentRepository.ts`) — network bootstrap →
  IndexedDB cache → bundled JSON snapshot (`public/fallback/*.json`), persisting
  last-good content.
- **Audio** — `audioManifest.ts` addresses pre-synthesized clips directly via
  `sha1(locale|voice|text).mp3` under the public R2 base, with on-demand synth
  fallback through `/v1/audio/manifest`. `audioPlayer.ts` is a pooled
  `HTMLAudioElement` (true pause/resume). `audioCache.ts` primes CacheStorage
  (seam for the offline phase).
- **Now Voicing + study queue** — `nowVoicing.ts` (= `NowVoicingBus`) and
  `studyQueue.ts` (= `StudyQueuePlayer`): cue → reveal → (slow) → answer, with
  stop / pause-resume / next / rewind / restart / loop and prefetch-ahead.
  One-off taps park and resume the active queue.
- **Login gate** (`src/auth/`) — required before the app. Email 6-digit code
  (Resend, live in prod) and Google Sign-In via GIS using the Worker's existing
  `GOOGLE_WEB_CLIENT_ID`. Opaque `lb_…` session in localStorage, sent as Bearer.
- **App shell** (`src/app/App.tsx`) — top bar (brand, direction switcher built
  from `languagePair` labels — never the API displayName, content-source status,
  sign out), lesson tabs, responsive desktop/tablet layout, Now Voicing side panel.
- **Lessons** — Phrases (groups, play one/group/all, **local custom phrases** in
  IndexedDB), Verbs, Nouns, Adjectives, Adverbs (forms playable, tap-to-hear),
  Pronunciation (phonemes + example words). Settings (cue/slow/loop, account).

## How to run

```bash
cd web
npm install
npm run dev        # local dev (http://localhost:5173)
npm run typecheck  # strict tsc, clean
npm run build      # → dist/index.html (single file)
npm test           # Playwright Chromium (builds + previews + drives)
```

## Verified

- `npm run typecheck` — 0 errors. `npm run build` — green.
- **Playwright (6/6 pass, desktop + tablet)**: login gate renders with LangBang
  branding + email sign-in; study shell + tabs render; bootstrap content loads;
  Now Voicing updates; clicking a phrase fires a real `.mp3` request (audio
  resolves); Nouns plays forms; no console errors in the study flow; tablet
  viewport has no horizontal overflow.
- **Live production check** (real Chromium vs https://langbang.org/app): login
  gate branded "LangBang"; study content loaded from the live API; audio fetched
  from R2 (`…/langbang/audio/…mp3`). Landing page, `/health`, `/builds.html`
  unchanged. Screenshots in `~/Documents/Screenshotz/langbang-web-*`.

## Deployment

The app is inlined into the existing langbang.org **site Worker**
(`cloudflare/langbang-org/worker.template.js`) via a new `__APP_HTML_BASE64__`
placeholder, served at `/app` (and `/app/*`). `scripts/deploy-langbang-org-site.sh`
builds `web/` (if `dist` missing or `REBUILD_WEB=1`), base64-encodes the single
file, substitutes it, and PUTs the worker. One build, one deploy; landing page
left intact.

```bash
REBUILD_WEB=1 scripts/deploy-langbang-org-site.sh
```

The same site Worker also exposes a same-origin **`/v1/*` proxy** to the backend
that aliases instance IDs (`en-pl` ↔ internal) and rewrites the display name, so
the web app uses friendly IDs and **never surfaces the internal backend name**.
The app's API base is relative on langbang.org and the live proxy elsewhere
(CORS-enabled). The Android app calls the backend directly and is unaffected.

## Known gaps / next (Phase 2+)

- **Google origin**: the GIS button needs `https://langbang.org` (and any
  localhost dev origin) listed as an Authorized JavaScript origin on the
  `GOOGLE_WEB_CLIENT_ID` OAuth client. Until then the Google button may not
  render; **email sign-in is the guaranteed path**. (Worker side already trusts
  the client ID.)
- **Backend name** — DONE (no longer a gap): hidden via the `/v1` proxy above.
  Live-verified that the deployed `/app` HTML and the app's runtime network calls
  contain zero "langbangml".
- **Server-enforced gate**: the gate is client-side (study content is public
  anyway). A server gate would require auth at the edge.
- **Offline (Phase 3)**: service worker + "Download for offline" not built; the
  bundled-fallback JSON isn't served by the worker at `/app` (network is the
  prod path). `audioCache.ts` is the seam.
- **Phase 2 features** deferred: user phrase cloud sync, AI phrase generation,
  agent-token panel, quizzes, grammar coloring, full per-lesson filters,
  English-conjugated verb cues, pl-en parity polish.
- No SEO/public lesson pages yet (Phase 4).
