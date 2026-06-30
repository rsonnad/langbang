# Phrase API review — LangBangML Worker (`langbangml-api`)

**Scope:** Deep code + API design review of `cloudflare/langbangml/src/index.js`
(deployed at `https://langbangml-api.langbangml.workers.dev`) focused on two goals:
(1) make custom-phrase adds near-instant, and (2) make the API usable by a
rudimentary LLM agent that can only issue one trivial parameterized call.

**Reviewed at:** commit `46751f5` / `dc764ce` HEAD on branch `codex/langbangml`
(file is the full current worker, 4226 lines).

**Method:** full read of the routing handler + phrase/LLM/auth pipeline; one live
non-mutating latency probe against the deployed worker; an independent
adversarial second opinion via the `grok` CLI, reconciled below. This document is
**review + design only** — no code was modified.

**Live measurement (2026-06-29, browser UA):** `POST /v1/phrases/complete` on the
trivial 5-word phrase *"Where is the train station?"* returned in **6.1s and
6.9s** across two runs — full pipeline (translation + literal gloss +
token-aligned `words[]` with gender/case) on a tiny input. This empirically
confirms the latency complaint: the cost is the *model and prompt shape*, not the
input size.

---

## 0. TL;DR — the two goals in one paragraph each

**GOAL 1 (speed).** Every JSON text-gen call in the worker funnels through
`geminiGenerateText` (L2643), which — while `AZURE_OPENAI_KEY` is set — hard-routes
to Azure deployment **`gpt-5.5`, a reasoning model** with `reasoning_effort:"low"`
(`azureOpenAiGenerateText`, L2657–2696; deployment pinned in `wrangler.toml`).
Reasoning models do not get fast just because the prompt shrinks, so prompt
surgery alone cannot reach sub-second. The single biggest win is a **non-reasoning
chat deployment** on the same `sponic-openai-eastus2` resource; the second is to
**save `en`+`pl` immediately and enrich `literal`/`words[]` asynchronously** so the
user-facing add is a DB write (~50–200ms). A *latent* second LLM round-trip
(`upsertUserPhraseGroup` → `generateLiteralForSentence`, L1156–1166 + L1135) can
**double** the worst-case latency and must be removed from the hot path.

**GOAL 2 (LLM-agnostic flexibility).** `POST /v1/agent/phrases` is hostile to a
weak agent: it requires a JSON body (`readJsonLimited` does `JSON.parse`
unconditionally, L2925), reads `groupId` **only** from the body (never query
params, L999), and **silently mints a new timestamped group** when `groupId` is
omitted (L1007 → `timestampedPhraseGroupId`, L1431) — while `GET ?groupTitle=…`
resolves to a *non-timestamped* slug (L1253). So an agent that "create group by
title, then add to it" gets a different id on every write and can never read its
own group back. This is a **data-fragmentation bug, not cosmetics**. The fix is a
query-param-friendly POST with a **stable default group** and `?en=&pl=&group=`
(or `?text=`) ergonomics.

---

## 1. PRIMARY review — API structure

### 1.1 Routing (`fetch`, L74–226)

- **Shape:** one big `async` IIFE inside a `try`; each route is an
  `if (method && path === …)` ladder, ending in `json({error:"not found"}, 404)`
  (L216). CORS is applied once via `withCors` on the way out (L218); errors are
  funneled through a single `catch` (L219–224). This is clean and easy to follow.
- **No 405 layer.** Routes match on method *and* path together, so a wrong method
  on a real path falls through to the catch-all **404** rather than 405. Verified
  live: `GET /v1/phrases/complete` → `404`, not 405. Minor, but it misleads
  clients ("endpoint doesn't exist" vs "wrong verb"). The agent endpoints *do*
  return a real 405 because they branch on method *inside* the handler
  (`agentPhrases` L1103) — so the behavior is inconsistent across the API.
- **Path normalization** strips trailing slashes (L81) — good. Routes that take a
  path segment `decodeURIComponent` it (L183 etc.) — good.
- **Per-route auth is inline and inconsistent**, which is the root of several
  issues below: `completePhrase`/`geminiGenerate` use `guardLlmEndpoint`;
  `agentPhrases` uses `agentApiRequest`→`requireAgentToken`; `geminiG2Translate`
  uses a shared static `GEMINI_LIVE_TOKEN`; admin routes use `requireAdmin`. There
  is no single "this is how an LLM-spend endpoint is gated" abstraction, so the
  agent phrase path quietly has **no `RL.*` LLM rate limit at all** (see §1.5).

### 1.2 `HttpError` + error surface (L228–234, L219–224)

- Shape is good: `{ error: string, details?: object }` + HTTP status. 429s carry
  `retryAfterSeconds` (L333). Agent quota 429s carry `{limit, used, remaining,
  resetsAt}` (L918). This is the right envelope for an agent to parse.
- **Two leakage / clarity problems:**
  - The non-`HttpError` catch (L223) returns `error.message` raw. Because
    `parseGeminiJsonText` (L2825) is a bare `JSON.parse`, a malformed model
    response throws a `SyntaxError` that surfaces as a **500 with a JS error
    string** instead of a structured `502 "model returned invalid JSON"`. An
    agent can't distinguish "my input was bad" from "the server's LLM hiccuped."
  - Azure 502s attach `details.body` = first 500 chars of the upstream response
    (L2683). That **leaks provider-side error text** (deployment names, quota
    messages) to any caller. Same for Gemini (L2715).
- **Inconsistent failure semantics for the same logical event.** When the LLM
  rejects a phrase, `completePhrase` returns a *successful* `200` with
  `consistent:false` and blanked fields (L2838–2846), but the agent path throws
  `502 "Gemini returned no usable phrase entries"` (L1319). Two callers, two
  shapes, for "the model didn't like this phrase."

### 1.3 The phrase pipeline (the heart of both goals)

**`completePhrase` (L2609)** — unauthenticated (because `LLM_REQUIRE_AUTH="false"`
in `wrangler.toml`, so `guardLlmEndpoint` only IP-rate-limits, L347–358). Reads
`sourceText`/`targetText`/`literalText`, builds **one mega-prompt**
(`buildPhraseCompletionPrompt`, L2729) that asks the model to *simultaneously*:
translate the missing side, silently fix typos, judge `consistent`, write a
word-for-word `literal` gloss, AND emit a per-token `words[]` array with Polish
gender + caseKey. That is four distinct cognitive tasks in one reasoning call —
bad for latency *and* reliability (one bad token-alignment can fail the whole
parse).

**`agentPhrases` POST (L994–1066)** — the per-account write path:

1. `normalizeAgentPhraseSentences` (L1290) → `generateAgentPhraseEntries` (L1303).
2. **Fast path** only if `hasCompleteAgentPhraseFields` is true, i.e.
   `en && pl && literal && words.length>0` (L1324). `en`+`pl` alone is *not*
   enough — it always calls the LLM (L1311). So the "fast path" is unreachable for
   any agent that won't hand-build a token-aligned `words[]`.
3. **Latent double LLM call.** Even on the slow path, the LLM result may omit
   `literal` (it's optional in `normalizeSentenceExample`, L1928). Then
   `upsertUserPhraseGroup` (L1156) sees `needsLiteral` and calls
   `generateLiteralForSentence` (L1135) — **a second `geminiGenerateText` call**,
   one per sentence, via `Promise.all`. Worst-case agent add ≈ **2× the single-call
   latency** (~12–14s observed-equivalent, up to the 9–19s × 2 the prompt warns
   about). This is the highest-leverage latency bug in the file and is invisible
   from the endpoint signature.

**`atomic:false` is a phrase-explosion footgun (L1380–1404, L1029–1038).** With
`atomic:false`, `buildAgentPhraseEntriesPrompt` instructs the model to *split*
input into multiple phrases, and `agentPhrases` then merges **all** of them into
the group (L1029). One agent call can balloon into many phrases. The `atomic`
guard only truncates to 1 in the `atomic:true` case (L1320); `atomic:false`
over-splitting is unbounded except by `MAX_AGENT_PHRASE_OUTPUTS` (25).

**Dedup/replace is brittle (L1435, L1056).** `sentenceKey` = `pl|en` lowercased.
Any LLM typo-fix on either side changes the key, so a re-add becomes a *new*
sentence instead of a replace. The reported `action` ("replace_phrase" only if
`replaced>0 && added===0`, L1056) mislabels mixed batches.

### 1.4 Group-targeting footgun (confirmed; the duplicate-group bug)

This is the single worst ergonomics defect and the prompt's named concern:

| Call | Where `groupId` comes from | Omitting it does… |
|---|---|---|
| `POST /v1/agent/phrases` | `body.groupId \|\| body.group?.id` only (L999) — **query params ignored** | `timestampedPhraseGroupId(title)` → `slug-<base36ts>`, a **brand-new group every call** (L1007, L1431) |
| `GET /v1/agent/phrases?groupTitle=…` | `slug(groupTitle)`, **no timestamp** (L1253) | n/a |
| `DELETE /v1/agent/phrases` | body **or** query `groupId`, plus `groupTitle`→`slug` (L1072) | deletes whole group |

Consequences for any agent following the documented "name a group, add to it"
flow:
- Repeated `POST {groupTitle:"Restaurant"}` (no `groupId`) creates
  `restaurant-ab12`, `restaurant-cd34`, … — **N duplicate groups, never an
  append.**
- `GET ?groupTitle=Restaurant` resolves to id `restaurant` and **404s** because no
  group has that bare id.
- The in-worker docs example (`agentInstructionsPage`, L3868) literally shows
  `GET ?groupTitle=Discussion Conversation`, which cannot match anything created
  by the `POST` example above (which omits `groupId`).

The asymmetry between POST (JSON-only, body-only `groupId`, timestamp-on-omit) and
DELETE (query+body, slug-on-omit) is the core inconsistency to fix.

### 1.5 Auth & rate limiting

- **`guardLlmEndpoint` (L347)** caps per-IP (`RL.completeIpPerHour`=180/hr) and,
  if a session is present, per-user/day (1200/day). With `LLM_REQUIRE_AUTH="false"`
  an **anonymous** caller gets 180 LLM calls/hr/IP on the (reasoning-model-backed)
  `completePhrase`. That's a real cost-amplification surface for an unauthenticated
  endpoint, mitigated only by the edge WAF.
- **`isAdmin` full-bypass (L348–349).** The admin/content token (same
  `Authorization: Bearer` shape as session and agent tokens) **completely bypasses
  every `RL.*` limit**. Because all three token types share the header shape, a
  tooling mix-up (admin token pasted into an app build) silently removes all rate
  limiting. Consider a distinct header or token prefix check.
- **The agent phrase path has NO `RL.*` LLM cap.** `agentPhrases` never calls
  `guardLlmEndpoint`; the only ceiling is the **100 calls/day** agent quota
  (`agentDailyLimit`, L1519). With the latent double-LLM call, that's up to
  `100 × 2 × ~10–19s` of reasoning-model spend per token per day, entirely
  separate from the `RL.complete*` budget. This is both a cost and a slow-DoS
  vector.
- **Quota is consumed on reads (L859–863).** `agentApiRequest` calls
  `consumeAgentQuota` before dispatch for **every** operation, including
  `GET /v1/agent/status` and `GET /v1/agent/phrases`. The documented "list groups
  first, then add" flow therefore burns **2 quota per add** — halving an agent's
  effective add budget to ~50/day. Reads almost certainly should not count, and
  this single change helps weak agents more than any new endpoint.
- **Quota burns even on failures.** `consumeAgentQuota` runs before the handler, so
  a `502` from the LLM still costs the caller a quota unit (L863).
- **`enforceRateLimit` fails open (L336–340)** on *any* non-HttpError D1 problem
  (missing migration, transient error) → unlimited until fixed. It's also
  increment-then-read (L317–327), a fixed window with a concurrency race that
  permits short bursts above the limit. Documented as intentional ("front with
  edge WAF") — acceptable as a backstop, but worth stating that the WAF is
  load-bearing.
- **`geminiGenerate` bypasses the Azure failover (L2513).** It calls
  `geminiGenerateRaw` **directly**, not `geminiGenerateText`. So while the Gemini
  key's billing is depleted, the public `/v1/gemini/generate` endpoint is **broken**
  (503/Gemini-HTTP error) even though `completePhrase`/`agentPhrases` work via
  Azure. Inconsistent failover coverage.
- **`authTestLogin` (L483)** is a username/password→session endpoint, correctly
  **disabled unless** both `TEST_LOGIN_EMAIL` and `TEST_LOGIN_PASSWORD` are set
  (404 otherwise, L486). Low risk as written, but it is a persistent password
  backdoor if those vars ever leak into prod config — keep them out of
  `wrangler.toml` and rotate.

### 1.6 Content-type handling

- POST agent writes: **JSON only** (`readJsonLimited`, L2925 → `JSON.parse`). No
  `application/x-www-form-urlencoded`, no query-param fallback. A weak agent that
  can only emit `?k=v` literally cannot add a phrase.
- DELETE agent writes: `readOptionalJson` (empty body OK) **and** full query-param
  fallback (L1070–1077). So the API is *more* capable for deletes than adds — the
  wrong way round for "make adds trivial."

---

## 2. SECONDARY review — adversarial pass (grok CLI), reconciled

An independent adversarial review was run via the `grok` CLI (`grok -p` headless,
pointed at a copy of the worker). It read the routing, phrase pipeline, and auth
independently. **It converged strongly with the primary review** and surfaced
several findings I then verified against source and folded into §1. Reconciliation:

**Confirmed by grok and independently verified (now in §1):**
- Group-identity bug: POST body-only `groupId` + timestamp-on-omit vs GET
  slug-only → duplicate groups + unfindable reads. (grok: "data corruption bug for
  agents, not cosmetic." Agreed — escalated to a P0.)
- Latent double LLM call via `upsertUserPhraseGroup`→`generateLiteralForSentence`;
  worst case ~2×. (grok flagged it as "fix this before any prompt rewrite." Agreed.)
- Quota consumed on GET reads → 2/add for list-then-write agents.
- Agent path has no `RL.*` LLM cap → cost/DoS vector separate from `RL.complete*`.
- `geminiGenerate` (L2513) bypasses the Azure failover.
- 502 leaks first 500 chars of upstream provider body; bare `JSON.parse` in
  `parseGeminiJsonText` surfaces as a 500 SyntaxError.
- `enforceRateLimit` fails open + fixed-window race.
- `authTestLogin` is an env-gated backdoor (both flagged it; verified it is
  off-by-default — kept as a low-severity note).
- `atomic:false` phrase-explosion.

**Where grok pushed back on over-engineering (I agree):**
- *"Sub-second AND synchronous `words[]` with gender/case — pick one."* Morphological
  alignment is the expensive tail; the app can study a phrase without it initially.
  → Reflected in the tiered design (§4): `words[]` is **lazy/Tier-2**, never on the
  add path.
- *"Prompt splitting on a reasoning model won't fix 9–19s — change the model or go
  async first."* Correct. Splitting into 3 sync calls on `gpt-5.5` would be *worse*
  (3× RTT). Splitting only helps if parallelized **and** on a fast model. → §3
  orders model-swap and async **before** any prompt decomposition.
- *"KV cache is marginal for a bespoke-phrase workload; `sentenceKey` dedup on write
  is cheaper."* Agreed — cache demoted to optional in §3/§4.
- *"The agent slow path doesn't need the `consistent`/`issue` judgment that
  `completePhrase` carries."* Correct — a leaner agent-translate prompt cuts tokens
  and failure modes (§3, prompt-split row).

**Where I differ from / temper grok:**
- grok suggested an idempotent `GET /v1/agent/phrases/add?...` "with side effect"
  as a fallback for GET-only agents. I'd **avoid a GET that mutates** (breaks HTTP
  caching/retry semantics, prefetch can double-write). The better answer for a
  truly GET-only agent is a body-less **POST with query params** (most "simple
  HTTP" agents can issue a parameterized POST even if they can't build JSON), or a
  separate explicit `/v1/agent/quickadd` POST. Documented in §4.
- grok's latency table is directionally right; I've re-grounded the numbers in §3
  against the live 6–7s measurement (its "9–19s" is the prompt's own warning, my
  live figure is lower because the test phrase was trivial — the *floor* is still
  multiple seconds on the reasoning model).

**Net:** the two reviews agree on the diagnosis and the prescription. No material
contradiction; the adversarial pass mainly *sharpened severity* (group bug → P0;
double-LLM → fix first) and *guarded against over-engineering* (no sync `words[]`,
no prompt-split-first, cache optional).

---

## 3. Prioritized recommendations

Ranked by (impact on the two goals) × (low effort first). Severities: **P0** =
correctness/agent-blocking, **P1** = major speed/ergonomics, **P2** = hardening.

| # | Sev | Recommendation | Why / expected win |
|---|---|---|---|
| 1 | **P0** | **Stable default group + accept `group`/`groupId` from query on POST.** Default to a fixed id (e.g. `agent`) instead of a timestamped slug; resolve `groupTitle`→`slug` to the **same** id GET uses. | Kills the duplicate-group bug and makes "add to my group" actually append. Pure logic change in `agentPhrases` (L999–1007) + `requestedAgentPhraseGroupId`. |
| 2 | **P1** | **Swap `gpt-5.5` → a non-reasoning chat deployment** (e.g. `gpt-4o-mini`/`gpt-4.1-mini` on the same `sponic-openai-eastus2` resource), or restore Gemini Flash. Change `AZURE_OPENAI_DEPLOYMENT` var + drop `reasoning_effort`. | ~85–95% latency cut on every LLM path (multi-second → ~0.4–2s). **Biggest single win; config-only.** |
| 3 | **P1** | **Remove the synchronous literal backfill from `upsertUserPhraseGroup`** (L1156–1166). Don't second-call the LLM on save; let `literal` be filled by the same call or async. | Eliminates the latent **2× latency**; one-line removal from the hot path. |
| 4 | **P0** | **Add a query-param / body-less POST add path** (`?en=&pl=&group=` and `?text=`). `readParamsOrJson`: if body empty, build the input object from `searchParams`. | Goal 2: a weak agent adds a phrase in one trivial call. |
| 5 | **P1** | **Save-now / enrich-async (`fast=true` default).** Persist `en`+`pl` immediately (`enrichment:"pending"`), return 200 in ~100ms, backfill `literal` (and later `words[]`) via `ctx.waitUntil`/Queue. Add `complete=true` to opt into the current synchronous behavior. | Sub-second user-facing adds **independent of model speed**; combine with #2 for fast backfill too. |
| 6 | **P1** | **Stop consuming agent quota on GETs** (and ideally on failed writes). Gate `consumeAgentQuota` to mutating ops (L859–863). | Doubles a weak agent's effective add budget; removes the list-then-write penalty. |
| 7 | **P2** | **Cap LLM spend on the agent path.** Wrap the synchronous-completion branch in an `enforceRateLimit`/`guardLlmEndpoint`-style budget, or only allow LLM completion when `complete=true`. | Closes the uncapped cost/DoS vector (§1.5). |
| 8 | **P2** | **Split the mega-prompt into purpose-built prompts** *after* #2/#5: a cheap *translate-only* prompt for the add path; *literal* and *words[]* as separate async prompts (parallelizable). Drop `consistent`/`issue` from the agent prompt. | Lower tokens + fewer parse failures; only pays off on a fast model / async. |
| 9 | **P2** | **Harden errors:** wrap `parseGeminiJsonText` to throw `HttpError(502,…)`; stop attaching upstream `details.body` to client 502s; return real **405** for wrong-method-on-real-path. | Agents get parseable, non-leaky, correctly-typed errors. |
| 10 | **P2** | **Fix `geminiGenerate` failover** (L2513): route through `geminiGenerateText` so `/v1/gemini/generate` also uses Azure during the Gemini outage. | Consistent failover; un-breaks the public generate endpoint. |
| 11 | **P2** | **Tame `atomic:false`:** cap split output (e.g. ≤3) and echo a clear `added` count + the split list so the caller isn't surprised. Keep `atomic:true` the **default** (it already is). | Prevents one call → many phrases surprises. |

---

## 4. Proposed improved API design (parameter-only agent)

Design principle: **one trivial call adds a phrase; the server fills everything
else; the group is stable; JSON is never required.**

### 4.1 The "quick add" contract

```
POST /v1/agent/phrases            # JSON body OR query params, both accepted
Authorization: Bearer <agent-token>
```

Resolution rules (new):
- Input may come from **query params or JSON body**; query wins when body is empty
  (`readParamsOrJson`). Accept `en`/`english`, `pl`/`polish`, `text`, `group`,
  `groupTitle`, `atomic`, `fast`, `complete`, `version`.
- **`group`** is the canonical group selector (alias of `groupId`). If omitted,
  default to a **stable** id `agent` (NOT a timestamp). `groupTitle` is resolved to
  `slug(title)` for both write and read — same id on every call.
- **`fast` defaults to true:** if `en`+`pl` are both present, **save immediately**,
  no synchronous LLM, return `{ok:true, enrichment:"pending"}`. `literal`/`words[]`
  backfill asynchronously.
- **`text` / single-side input:** if only one side (or `?text=`) is given, save a
  stub immediately and translate asynchronously (still one agent call, returns
  fast). `complete=true` forces the old synchronous fill if the caller really wants
  the completed entry in the response.

### 4.2 Copy-paste examples a parameter-only agent can use

```bash
API=https://langbangml-api.langbangml.workers.dev
TOKEN=lba_xxx   # per-account token from Settings → Agent API

# 1) Trivially add a phrase — both sides, instant, stable group:
curl -X POST "$API/v1/agent/phrases?group=restaurant&en=The+bill%2C+please&pl=Poprosz%C4%99+rachunek" \
  -H "Authorization: Bearer $TOKEN"

# 2) Even simpler — just the English; server translates + enriches async:
curl -X POST "$API/v1/agent/phrases?group=restaurant&text=Where+is+the+bathroom%3F" \
  -H "Authorization: Bearer $TOKEN"

# 3) Polish-only:
curl -X POST "$API/v1/agent/phrases?group=travel&pl=Gdzie+jest+dworzec%3F" \
  -H "Authorization: Bearer $TOKEN"

# 4) Want the completed entry back in the response (opt into slow path):
curl -X POST "$API/v1/agent/phrases?group=travel&en=Where+is+the+station&complete=true" \
  -H "Authorization: Bearer $TOKEN"

# 5) Read your group back — SAME id, GET should not cost quota:
curl "$API/v1/agent/phrases?group=restaurant" -H "Authorization: Bearer $TOKEN"
```

JSON still works unchanged for richer agents (token-aligned `words[]`, batch
`phrases[]`), so this is purely additive and backward-compatible.

### 4.3 Why this satisfies a weak agent

- **One call, no nesting:** `?en=&pl=&group=` (or `?text=`) — no JSON, no `words[]`,
  no chained "create-then-add."
- **Idempotent target:** stable `group` id means re-adding to the same group
  appends instead of fragmenting.
- **No mandatory reads:** the agent never has to `GET ?groupsOnly=true` first, so it
  never burns quota discovering groups.
- **Self-documenting response:** `{ok, group, added, enrichment}` tells the agent
  exactly what happened.

---

## 5. Proposed fast-path architecture (sub-second adds)

Three tiers; the **add path touches only Tier 0**.

```
Tier 0  — INSTANT (on the request, ~50–200ms)
  validate → D1 upsert { en, pl, enrichment:"pending" } → return 200
  (if only one side present, store the known side; mark needs_translation)

Tier 1  — ASYNC ENRICH (ctx.waitUntil or a Queue consumer, ~0.4–2s background)
  - translate the missing side (if any)         } single small prompt,
  - generate `literal` word-for-word gloss       } NON-REASONING model
  write back, flip enrichment → "ready"

Tier 2  — LAZY MORPHOLOGY (on first open of the phrase, or nightly batch)
  - words[]: per-token gender / caseKey
  never on the add path; this is the expensive tail
```

Supporting changes:
- **Model:** non-reasoning chat deployment (rec #2). This makes even the Tier-1
  background work finish in ~1–2s rather than 6–19s.
- **Kill the sync literal backfill** (rec #3) — Tier 1 owns `literal` now.
- **Optional cache** keyed on `sha256(instanceId|en|pl)` → completion, for common
  phrases ("thank you", "the bill please"). Low hit-rate for bespoke learner
  phrases, so optional — `sentenceKey` dedup on write (already present, L1435) is
  the cheaper 80% win.

**Expected outcome.** Tier-0 + async-enrich + non-reasoning model → **sub-second,
model-speed-independent** user-facing adds, retaining ~90% of current output
quality (full `words[]` morphology lands a beat later or on first view).
**Counterfactual:** keeping `gpt-5.5` + the monolithic prompt + synchronous literal
backfill will *not* reach sub-second by any amount of prompt rewording — both this
review and the adversarial pass agree on that.

---

## Appendix — key source coordinates

| Concern | Function | Line |
|---|---|---|
| Router | `fetch` | 74 |
| Error type | `HttpError` | 228 |
| Admin token + bypass | `requireAdmin` / `isAdmin` | 236 / 250 |
| LLM gate | `guardLlmEndpoint` | 347 |
| Fixed-window limiter (fails open) | `enforceRateLimit` | 312 |
| Agent dispatch + quota-on-every-call | `agentApiRequest` | 859 |
| Agent quota | `consumeAgentQuota` | 901 |
| **Agent phrases (POST/GET/DELETE)** | `agentPhrases` | 972 |
| Group id from body only / timestamp-on-omit | (within `agentPhrases`) | 999 / 1007 |
| GET group resolution (slug, no timestamp) | `requestedAgentPhraseGroupId` | 1253 |
| Slow-path generation | `generateAgentPhraseEntries` | 1303 |
| Fast-path gate (needs full `words[]`) | `hasCompleteAgentPhraseFields` | 1324 |
| **Latent 2nd LLM call on save** | `upsertUserPhraseGroup` → `generateLiteralForSentence` | 1154 / 1135 |
| Agent prompt | `buildAgentPhraseEntriesPrompt` | 1399 |
| `completePhrase` endpoint | `completePhrase` | 2609 |
| **Mega-prompt** | `buildPhraseCompletionPrompt` | 2729 |
| LLM dispatch + Azure failover | `geminiGenerateText` | 2643 |
| Azure reasoning call | `azureOpenAiGenerateText` | 2657 |
| `geminiGenerate` (bypasses failover) | `geminiGenerate` | 2507 |
| Bare JSON.parse of model output | `parseGeminiJsonText` | 2825 |
| Agent docs page (shown to agents) | `agentInstructionsPage` | 3730 |
| Config: deployment = `gpt-5.5`, `LLM_REQUIRE_AUTH="false"` | `wrangler.toml [vars]` | — |
