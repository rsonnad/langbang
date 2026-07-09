# Adding verbs & phrases (LangBang content)

A fast, repeatable recipe for adding study content. Read the
**decision** first, then jump to the matching section.

## Decision: default vs. one account

| You want… | Use | Touches |
|---|---|---|
| Content for **everyone** (no account named) — *the default* | **A. Default content** (asset + seed + D1) | global `content_lessons` |
| Content for **one specific account** | **B. Agent API** (that account's token) | that user's own content only |

If nothing is specified, it's **default for all accounts** → section A.

## Where content lives (3 layers)

1. **Source of truth — assets** (bundled into the APK, feeds the D1 seed):
   - Verbs: [`app/src/main/assets/lesson-02.json`](../../app/src/main/assets/lesson-02.json) → `verbs[]`
   - Phrases: [`app/src/main/assets/lesson-05.json`](../../app/src/main/assets/lesson-05.json) → `groups[].sentences[]`
2. **Live default content — D1** `content_lessons`, keyed by `content_version_id`
   (`en-pl-v1`, `pl-en-v1`). This is what signed-in apps actually fetch at
   bootstrap. The apps read **D1 first, asset second** — so an asset edit alone
   does *not* change the live apps until D1 is updated.
3. **Per-account content** — user-owned tables, editable **only** via the Agent
   API. Never mutates the global lessons.

---

## A. Default content (all accounts)

### Schemas

Verb entry (lesson-02 `verbs[]`):
```json
{"lemma": "doceniać", "en": "to appreciate",
 "forms": {"1sg":"doceniam","2sg":"doceniasz","3sg":"docenia","1pl":"doceniamy","2pl":"doceniacie","3pl":"doceniają"},
 "past_forms": {"1sg":"doceniałem","2sg":"doceniałeś","3sg":"doceniał","1pl":"docenialiśmy","2pl":"docenialiście","3pl":"doceniali"}}
```

Phrase (lesson-05, a sentence inside `groups[].sentences[]`):
```json
{"pl":"Nabrałeś ochoty na kawę?","en":"Did you get in the mood for coffee?",
 "literal":"You-got desire for coffee?",
 "words":[{"pl":"Nabrałeś","en":"you-got"},{"pl":"ochoty","en":"desire"},{"pl":"na","en":"for"},{"pl":"kawę?","en":"coffee?"}]}
```
New phrase group: `{"id":"<slug>","title":"…","subtitle":"…","sentences":[ …above… ]}`

> No phonetic/pronunciation field exists — pronunciation is delivered as TTS
> audio (synthesized on demand). The `words[]` array drives word-aligned display.

### Steps

```bash
cd /Users/rahulio/Documents/CodingProjects/LangBangML

# 1. Edit the asset JSON (verbs → lesson-02, phrases → lesson-05). Keep the schema above.

# 2. Validate
python3 -m json.tool < app/src/main/assets/lesson-02.json > /dev/null && echo OK
python3 -m json.tool < app/src/main/assets/lesson-05.json > /dev/null && echo OK

# 3. Regenerate the D1 seeds from the assets (both directions; pl-en is auto-reversed)
node cloudflare/langbangml/scripts/generate-content-seed.mjs
node cloudflare/langbangml/scripts/generate-pl-en-full-lessons.mjs

# 4. Push to LIVE D1 — pick ONE:

#    4a. QUICK, one item at a time (admin content API). collection = verbs | groups | sentences.
#        Add a verb to en-pl-v1:
echo '{"collection":"verbs","item":{ … verb object … }}' \
  | scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/lessons/lesson-02/items -
#        Add a whole phrase group to en-pl-v1:
echo '{"collection":"groups","item":{ … group object … }}' \
  | scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/lessons/lesson-05/items -
#        (Re-run for pl-en-v1 if that flavor is in use. The pl-en phrase lesson is
#         a reversed shape — prefer 4b for the pl-en direction.)

#    4b. WHOLE-LESSON from the regenerated seed (covers both directions, incl. pl-en reversal).
#        Run each `INSERT OR REPLACE INTO content_lessons …` row from migrations
#        003/005 against D1 via the D1 HTTP API (see "D1 HTTP API" below). Idempotent.

# 5. Warm audio (synthesize + upload to R2) for the changed lessons
echo '{"lessonId":"lesson-02"}' | scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/audio/warm-missing -
echo '{"lessonId":"lesson-05"}' | scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/audio/warm-missing -
#    (repeat with pl-en-v1; omit lessonId to warm everything)

# 6. OPTIONAL — refresh offline bundles: rebuild/redeploy web and rebuild the APK
#    so web/public/fallback/bootstrap-*.json and the bundled assets match.
```

New content appears on the next app sync (launch, sign-in, instance switch, or
Settings → Phrases sync).

> **Keep asset + D1 in sync.** The admin API (4a) and D1 SQL (4b) edit D1 only.
> Always also do step 1 (asset edit), or the next seed regen will revert live
> content back to the stale asset, and the APK bundle/offline fallback will drift.

### D1 HTTP API (for step 4b)

`wrangler … --remote` fails for this DB; use the HTTP `/query` endpoint with the
account token. Send individual `INSERT OR REPLACE` statements (strip the
`BEGIN TRANSACTION;`/`COMMIT;` wrapper — D1 manages transactions). Token + curl
recipe: see the langbang project memory `langbangml-d1-access.md`
(BW item **"Cloudflare - LangBang Codex Claude Admin"**, account
`df99afea5ab9636a19adbdead37fc133`, DB `d259445e-d263-4ae2-a391-f0d176492265`).

### Gotchas (verified 2026-06-14)

- **The pl-en direction is fully reversed by the seed scripts — don't hand-build
  it.** pl-en *verbs* are conjugated in **English** (`lemma:"appreciate"`,
  `en:"doceniać"`, English `forms`), and pl-en *phrases* swap `pl`/`en`. Lift the
  generated objects straight out of the regenerated seeds and upsert them; the
  `generate-*.mjs` scripts already did the English conjugation and the swap.
- **Cleanest live push = upsert the seed-generated objects via the admin items
  API**, one call per `(version, lesson)`. Upsert keys on `lemma` (verbs) / `id`
  (groups), so it's idempotent and surgical — it touches only the item you send,
  not other in-progress edits in the same lesson.
- **`pl-en-phrases` (reversed_phrases) is NOT seeded in prod D1** — the pl-en app
  reads `lesson-05`. Pushing to `pl-en-phrases` returns 404; skip it.
- **`warm-missing` warms a whole lesson's missing audio, in payload order.** A new
  verb is the *last* entry, so a small `limit` won't reach it. Either warm with a
  `limit` above the lesson's total missing count, or just let verb audio generate
  on demand (first tap synthesizes + caches; ~1s once).
- Content goes live on the next app sync (launch / sign-in / instance switch /
  Settings → Phrases sync); the bootstrap response is not edge-cached.

---

## B. One specific account (Agent API)

Live docs + copy-paste examples: **https://langbang.org/api**
(`scripts/langbangml-content-api.sh` is for default content; the Agent API uses a
**per-account** token, not the admin token.)

- **Token:** minted in the app by the signed-in account (Settings → Agent API).
  The token *is* the account selector — there is no admin override to write into
  another account. To add to a specific account, use that account's token.
- **Language pair:** omit `version`/`instanceId` for the token's default pair;
  set `"version":"ENPL"` (English→Polish) or `"version":"PLEN"` (Polish→English).
- **Quota:** limited authenticated calls per token per day.
- **Fast path:** for near-real-time phrase writes, send `pl`, `en`, `literal`,
  and `words[]`. The Agent API can save complete structured entries directly.
  If any of those fields are missing, the Worker synchronously calls Gemini to
  fill translation/gloss/alignment before saving; that can take several seconds
  per phrase and one rejected poetic line can fail the whole request.
- **Backfill path:** when you only have Polish or English, write fewer items per
  request or expect slower LLM-backed completion. For bulk content, prefer
  storing reviewed `pl`/`en`/`literal`/`words[]` now and backfilling richer
  grammar metadata later.

```bash
# Add a verb to the account
curl -sS https://langbangml-api.langbangml.workers.dev/v1/agent/words \
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"verb","lemma":"doceniać","en":"to appreciate",
       "forms":{"1sg":"doceniam","2sg":"doceniasz","3sg":"docenia","1pl":"doceniamy","2pl":"doceniacie","3pl":"doceniają"},
       "past_forms":{"1sg":"doceniałem","2sg":"doceniałeś","3sg":"doceniał","1pl":"docenialiśmy","2pl":"docenialiście","3pl":"doceniali"}}'

# Fast add: caller provides complete structured data, so the API saves directly.
curl -sS https://langbangml-api.langbangml.workers.dev/v1/agent/phrases \
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" -H "Content-Type: application/json" \
  -d '{"groupTitle":"In the mood — nabrać ochoty","atomic":true,
       "phrase":{"pl":"Nabrałeś ochoty na kawę?","en":"Did you get in the mood for coffee?",
       "literal":"You-got desire for coffee?",
       "words":[{"pl":"Nabrałeś","en":"you-got"},{"pl":"ochoty","en":"desire"},
                {"pl":"na","en":"for"},{"pl":"kawę?","en":"coffee?"}]}}'

# Slow add: send english, polish, or both; LangBang calls Gemini to fill the
# missing side, literal gloss, and word alignment before saving.
curl -sS https://langbangml-api.langbangml.workers.dev/v1/agent/phrases \
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" -H "Content-Type: application/json" \
  -d '{"groupTitle":"In the mood — nabrać ochoty","polish":"Nabrałeś ochoty na kawę?","atomic":true}'
```

Word shapes: `verb` (lemma, en, forms, past_forms), `noun` (lemma, en, gender,
nom/acc/gen × sg/pl), `adjective` (lemma, en, nom, acc), `adverb` (lemma, en).
`GET /v1/agent/phrases?groupsOnly=true` lists existing groups first.

---

## Quick reference

- Content versions: `en-pl-v1`, `pl-en-v1`
- Worker / API base: `https://langbangml-api.langbangml.workers.dev`
- Default-content helper: `scripts/langbangml-content-api.sh METHOD /path [body|-]`
- Seed scripts: `cloudflare/langbangml/scripts/generate-content-seed.mjs`,
  `…/generate-pl-en-full-lessons.mjs`
- Per-account API docs: https://langbang.org/api
