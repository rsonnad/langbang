# LangBang Backend Contract (iOS KMP must replicate exactly)

**Base URL (default):** `https://langbangml-api.langbangml.workers.dev`

All paths are under this origin. Client never hard-codes other origins except:
- Public R2 base (from bootstrap `audio.publicR2Base`, typically `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev`)
- Direct Azure TTS (region.tts.speech.microsoft.com) only as fallback or when key present in debug.

**Auth model (critical):**
- Google: native Google Sign-In → `idToken` (JWT) + client-generated `nonce` → `POST /v1/auth/google` → `{user, session: {token, expiresAt}}`
- Worker verifies the `idToken`:
  - Fetches Google JWKS (`https://www.googleapis.com/oauth2/v3/certs`)
  - Checks RS256 signature
  - Checks issuer
  - Checks `exp`
  - **Audience allowlist**: splits `env.GOOGLE_WEB_CLIENT_ID` (or fallbacks) by comma. `body.aud` **must** be in that list. Multiple client IDs supported (Android webClientId + future iOS client ID).
  - Optional nonce match when provided.
- Result: `sessionToken` (format `lb_...`) sent as `Authorization: Bearer <token>` on all protected routes.
- Email code: `POST /v1/auth/email/start` (email only) → code emailed; `POST /v1/auth/email/verify` (email+code+instanceId) → same auth response.
- `agentToken` is a **separate** credential created via `/v1/me/agent-token` (using session bearer). Used for `/v1/agent/*` routes. Has per-day quota.
- `sessionToken` and `agentToken` are **not interchangeable**.

**Headers:**
- `Accept: application/json`
- `Content-Type: application/json` on POST/PUT
- `Authorization: Bearer <sessionToken>` (or agent token for agent routes)
- CORS is present but native clients ignore it.

---

## Public / Unauthenticated Endpoints

| Method | Path | Purpose | Request | Response (key fields) | Notes |
|--------|------|---------|---------|-----------------------|-------|
| GET | `/health` | Liveness | — | `{ok:true, service:"langbangml-api"}` | |
| GET | `/v1/instances` | List language instances | — | `{instances: [{id, displayName, uiLocale, contentVersionId, languagePair}]}` | |
| GET | `/v1/instances/{instanceId}/bootstrap` | Full content + voices + labels | — | `CloudBootstrap`: `{instance, languagePair, content:{versionId, lessons[]}, labels:{}, audio:{manifestEndpoint, publicR2Base, audioPrefix}, syncedAt}` | Lessons contain the canonical verbs/phrases JSON payloads. |
| GET | `/v1/labels/{instanceId}` | UI string overrides | — | map | |
| GET | `/agent` or `/agent/instructions` | Human docs for Agent API | — | HTML page | |
| POST | `/v1/phrases/complete` | LLM fill-in for custom phrase | `{sourceText, targetText, literalText, sourceLanguage, targetLanguage}` | `{consistent, issue, source, target, literal?, words: TokenPair[]}` | Rate-limited; can be called unauth (but app usually does inside phrases UI). |
| POST | `/v1/gemini/generate` | Sentence / conjugation generation (proxied) | `{model, prompt}` (see GeminiClient/LangBangApi) | Gemini JSON | Expensive; send `Authorization: Bearer <sessionToken>` when signed in so the worker can meter per-user quota. |
| POST | `/v1/audio/manifest` | Batch R2 (or synthesize) URLs for (text,voice,locale) | `{phrases: [{text, voice, locale}, ...]}` (≤100 non-admin) | `{summary, manifest: [{text,voice,locale,sha1,url,uploaded?,error?}]}` | Worker synthesizes missing and uploads to R2. Client then downloads the mp3s. |
| POST | `/v1/azure/speech-token` | Short-lived token for Azure SDK (pron) | (auth varies) | token + region/expiry | Used so Azure key never ships in release binaries. |
| POST | `/v1/analytics/events` | Batched product analytics | array of events | `{ok?}` | Rate limited per IP. |

---

## Auth Endpoints

| Method | Path | Purpose | Auth | Request | Response |
|--------|------|---------|------|---------|----------|
| POST | `/v1/auth/google` | Exchange Google idToken | none | `{idToken, nonce, instanceId}` | `CloudAuthResponse` = `{user:{id,email,emailVerified,displayName,pictureUrl}, session:{token,expiresAt}}` |
| POST | `/v1/auth/email/start` | Trigger email code | none | `{email}` | `{ok, email, sent, expiresInMinutes}` |
| POST | `/v1/auth/email/verify` | Redeem code | none | `{email, code, instanceId}` | `CloudAuthResponse` (same) |
| POST | `/v1/auth/sign-out` | Invalidate session | Bearer session | `{}` | 200 |
| POST | `/v1/auth/test-login` | Test-only (disabled unless configured) | — | — | — |

---

## Authenticated (session bearer) Endpoints

All require valid `Authorization: Bearer <sessionToken>`.

| Method | Path | Purpose | Request | Response |
|--------|------|---------|---------|----------|
| GET | `/v1/me` | Current user summary | — | `{user: publicUser}` |
| DELETE | `/v1/me` (or POST `/v1/me/delete`) | Delete account + data | — | 200 |
| POST | `/v1/me/agent-token` | Create/rotate agent token for this user | `{instanceId, label?, rotate?}` | `CloudAgentTokenResponse` `{ok, token, tokenPrefix, dailyLimit, apiBase, instructionsUrl, ...}` |
| GET | `/v1/me/content?instanceId=...` | Pull user's custom phrases, stars, custom words for instance | — | `CloudUserContentResponse` `{instanceId, groups: PhraseGroup[], starredPhrases[], words:{}, hasRemote*, syncedAt}` |
| GET | `/v1/me/phrases?instanceId=...` | Alias / subset of above | — | `CloudUserPhrasesResponse` |
| PUT | `/v1/me/phrases` | Push/replace user's groups + starred | `{instanceId, groups: PhraseGroup[], starredPhrases[], replace:true}` | `CloudUserPhrasesResponse` |
| POST | `/v1/me/phrases/ai-generate` | AI-generate N phrases into a group | `{instanceId, groupId, groupTitle, groupSubtitle?, prompt, count}` | `{ok, group?, phrases: SentenceExample[], quota}` |
| POST | `/v1/me/phrases/ai-quota-request` | Request quota increase (email to admin) | `{instanceId, message}` | `{ok, sent, quota}` |
| GET | `/v1/me/phrases/ai-quota` | Current AI phrase quota | — | `{limit, used, remaining}` |

---

## Agent API (agentToken auth, separate from session)

Header still `Authorization: Bearer <agentToken>`.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/v1/agent/status` | Quota / identity |
| GET/POST | `/v1/agent/phrases` | Add/list user-owned phrases (bypasses global lessons) |
| GET/POST | `/v1/agent/words` | Add/list user-owned words (verbs/nouns/etc) |

See worker `/agent` page for full schema.

---

## Audio & R2 Scheme

- Manifest returns entries with `url` that are **public R2** (or will be after ensure).
- Key pattern inside bucket (from worker): `${AUDIO_PREFIX || "langbang/audio"}/${sha1}.mp3` where sha1 = sha1(`${locale}|${voice}|${text}`)
- Client never writes to R2 directly.
- Slow variants are encoded in the **voice** string the client sends (e.g. `...|slow50v3`).
- Public base is returned in bootstrap so the iOS client does not hard-code it.

---

## Gemini / LLM

- All generation goes through worker proxy `POST /v1/gemini/generate`.
- Shared/iOS Ktor calls must mirror Android's `GeminiClient`: when a LangBang session token is available, include `Authorization: Bearer <sessionToken>` on `/v1/gemini/generate`. Blank or missing session tokens should omit the header and use the worker's anonymous/IP quota path.
- Worker applies safety settings (BLOCK_ONLY_HIGH) and rate limits.
- Prompts and "wipe versions" live in `GeminiClient`; changing them requires bumping `SENTENCE_PROMPT_VERSION` + per-type wipe constants and re-warming R2 bundles.
- Live (streaming) uses WebSocket upgrade on `/v1/gemini/live` (primarily for G2 relay).

---

## Analytics

- `POST /v1/analytics/events` — array, max batch size enforced server-side.
- Admin views at `/v1/admin/analytics/summary` and `/events` (require special email allowlist + basic auth or token).

---

## Instance / Content Notes

- Two primary instances today:
  - `langbangml-en-pl` (English prompt → Polish target)
  - `langbangml-pl-en`
- Switching instance reloads bootstrap and user content for that instance.
- `languagePair.sourceVoice` / `targetVoice` / `targetSlowVoices` drive audio keys.

---

## Error Handling Expectations

- Worker returns `{error, details?}` + appropriate HTTP status for most failures.
- 401 for bad/missing/expired tokens or audience mismatch.
- 429 rate limits on expensive paths.
- Client (Android) treats non-2xx as failure with truncated error body.

---

## What iOS Port Must NOT Do

- Do not call admin endpoints.
- Do not bypass rate limits.
- Do not store or ship real Azure/Gemini keys in release.
- For Google auth on iOS: use a **new iOS OAuth client ID** created in GCP project `langbang-498411`. Append the iOS client ID (and the reversed-client scheme) to the worker's `GOOGLE_WEB_CLIENT_ID` env var and redeploy. No code change in worker needed.
- Never mutate backend data models or add new endpoints without coordinating.

**Reference implementation files (read-only):**
- Client: `LangBangML/app/src/main/kotlin/com/sponic/langbang/cloud/CloudBackendClient.kt`, `CloudModels.kt`, `AuthStore.kt`
- Worker: `LangBangML/cloudflare/langbangml/src/index.js` (routes ~85-220, auth ~395+, verify ~1710+, audioManifest ~3429+, bootstrap ~2490ish)
