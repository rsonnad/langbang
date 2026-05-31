#!/usr/bin/env python3
"""
Populate the langbang R2 audio cache from the dev machine.

Walks the bundled lesson assets exactly the way R2AudioDownloader.buildManifestPhrases()
does on-device, slices the result into batches of 100 phrases (under the 150-sec
Supabase Edge Function timeout), and POSTs each batch to langbang-pregen-audio.
The function HEAD-checks R2 for each sha1 key and skips synth on cache hits, so
this is idempotent — rerun any time content changes.

Run from the langbang repo root:
    python3 scripts/populate-r2-audio.py

Reads the anon key from local.properties so no extra config needed.
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
LOCAL_PROPS = REPO / "local.properties"
GEMINI_KT = REPO / "app" / "src" / "main" / "kotlin" / "com" / "sponic" / "langbang" / "integrations" / "GeminiClient.kt"
FUNCTION_URL = "https://aphrrfprbixmhissnjfn.supabase.co/functions/v1/langbang-pregen-audio"
PUBLIC_BASE = "https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev"
BATCH_SIZE = 100  # ~100s per batch at ~1s/phrase, leaves headroom under 150s limit

# Match AzureTtsClient constants exactly. We pre-generate all THREE PL variants the
# on-device R2AudioDownloader.buildManifestPhrases() pulls — normal, the "stretch"
# slow style (slow60v1), and the "articulate" slow style (slowart1) — so flipping
# Settings → Slow audio style is instant on every device (no lazy on-device synth).
PL_F = "pl-PL-ZofiaNeural"
PL_F_SLOW = "pl-PL-ZofiaNeural|slow60v1"
PL_F_SLOW_ART = "pl-PL-ZofiaNeural|slowart1"
EN_F = "en-US-JennyNeural"
LOC_PL = "pl-PL"
LOC_EN = "en-US"


# Cloudflare's r2.dev public endpoint 403s the default "Python-urllib/x" User-Agent
# (bot filtering). A curl-style UA sails through, same as the on-device HttpURLConnection.
R2_UA = "curl/8.4.0"


def r2_get_json(url, timeout=20, retries=5):
    """GET + parse JSON from r2.dev with retry-on-throttle. A burst of ~120 sequential
    bundle GETs trips Cloudflare's rate limiter (429/403/dropped connection) after the
    first few dozen, so back off and retry rather than dropping the bundle."""
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": R2_UA})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            last = e
            time.sleep(0.5 * (2 ** attempt))  # 0.5, 1, 2, 4, 8s
    raise last if last else RuntimeError("r2_get_json: no error captured")


def read_prompt_version():
    """Read SENTENCE_PROMPT_VERSION from GeminiClient.kt so the R2 sentence tree we
    walk for now-voicing audio matches what the client downloads."""
    import re
    m = re.search(r"SENTENCE_PROMPT_VERSION\s*=\s*(\d+)", GEMINI_KT.read_text())
    return int(m.group(1)) if m else None

# Match audioPronoun() in data/model/Pronoun.kt.
PRONOUN = {"1sg": "ja", "2sg": "ty", "3sg": "on", "1pl": "my", "2pl": "wy", "3pl": "oni"}


def read_anon_key() -> str:
    for line in LOCAL_PROPS.read_text().splitlines():
        if line.startswith("SUPABASE_ANON_KEY="):
            return line.split("=", 1)[1].strip()
    sys.exit("SUPABASE_ANON_KEY not found in local.properties")


def load_json(name):
    return json.loads((ASSETS / name).read_text())


def build_phrases():
    """Mirror of R2AudioDownloader.buildManifestPhrases() — keep in sync."""
    out = []
    seen = set()

    def add(text, voice, locale):
        text = (text or "").strip()
        if not text:
            return
        key = f"{text}|{locale}|{voice}"
        if key in seen:
            return
        seen.add(key)
        out.append({"text": text, "voice": voice, "locale": locale})

    def add_pl(text):
        add(text, PL_F, LOC_PL)
        add(text, PL_F_SLOW, LOC_PL)
        add(text, PL_F_SLOW_ART, LOC_PL)

    lesson2 = load_json("lesson-02.json")
    lesson3 = load_json("lesson-03.json")
    lesson4 = load_json("lesson-04.json")
    lesson1 = load_json("lesson-01.json")

    # lesson-02 fixed phrases
    for p in lesson2.get("phrases", []):
        add(p["en"], EN_F, LOC_EN)
        add_pl(p["pl"])

    # lesson-02 verb forms (present + past) with audio pronoun prefix
    for v in lesson2.get("verbs", []):
        for k, f in (v.get("forms") or {}).items():
            add_pl(f"{PRONOUN.get(k, '')} {f}".strip())
        for k, f in (v.get("past_forms") or {}).items():
            add_pl(f"{PRONOUN.get(k, '')} {f}".strip())

    # lesson-02 pronoun case forms
    for p in lesson2.get("pronouns", []):
        for f in (p.get("case_forms") or {}).values():
            add_pl(f)

    # lesson-03 adjective forms (nom + acc)
    for a in lesson3.get("adjectives", []):
        for f in (a.get("nom") or {}).values():
            add_pl(f)
        for f in (a.get("acc") or {}).values():
            add_pl(f)

    # lesson-04 adverbs (lemma only — adverbs are uninflected)
    for adv in lesson4.get("adverbs", []):
        add_pl(adv["lemma"])

    # lesson-06 noun forms (nom + acc + gen, each singular & plural)
    lesson6_path = ASSETS / "lesson-06.json"
    if lesson6_path.exists():
        lesson6 = json.loads(lesson6_path.read_text())
        for n in lesson6.get("nouns", []):
            for case in ("nom", "acc", "gen"):
                for f in (n.get(case) or {}).values():
                    add_pl(f)

    # lesson-01 phoneme examples
    for ph in lesson1.get("phonemes", []):
        for ex in ph.get("examples", []):
            add_pl(ex["pl"])

    # Bundled past-tense sentence pregen, if it exists.
    past_path = ASSETS / "verb-past-sentences-pregen.json"
    if past_path.exists():
        past = json.loads(past_path.read_text())
        for sentences in past.values():
            for s in sentences:
                add(s["en"], EN_F, LOC_EN)
                add_pl(s["pl"])

    # NOW-VOICING sentence audio — walk the R2 sentence manifest for the current
    # prompt version and add EN cue + PL target (+ both slow PL styles) for every
    # generated example sentence. This is the bulk of "all the now voicing": the
    # on-device R2AudioDownloader requests these from its local sentence cache, so
    # pre-warming R2 here makes those device pulls cache hits instead of lazy synth.
    add_sentence_audio(add, add_pl)

    return out


def _iter_sentence_bundles(v):
    """Yield each sentence bundle (a list of {pl,en,...}) for prompt version v.

    Prefers a locally-synced directory ($LANGBANG_SENTENCES_DIR) — populated via
    `aws s3 sync s3://alpacapps/langbang/sentences/v{N}/ <dir>` — because the public
    r2.dev endpoint rate-limits a burst of ~120 sequential GETs (403/429 after the
    first few dozen). Falls back to throttled HTTP if no local dir is provided."""
    local_dir = os.environ.get("LANGBANG_SENTENCES_DIR")
    if local_dir and Path(local_dir).is_dir():
        files = sorted(p for p in Path(local_dir).rglob("*.json") if p.name != "manifest.json")
        print(f"  reading {len(files)} sentence bundles from {local_dir} …")
        for p in files:
            try:
                yield json.loads(p.read_text())
            except Exception:
                continue
        return
    # HTTP fallback — slower and rate-limit-prone.
    man_url = f"{PUBLIC_BASE}/langbang/sentences/v{v}/manifest.json"
    try:
        manifest = r2_get_json(man_url)
    except Exception as e:
        print(f"  (sentence manifest v{v} unreachable: {e} — skipping now-voicing audio)")
        return
    entries = manifest.get("entries", {})
    print(f"  walking {len(entries)} sentence bundles (v{v}) over HTTP "
          f"(set LANGBANG_SENTENCES_DIR to avoid rate limits)…")
    for key, meta in entries.items():
        url = meta.get("url") or f"{PUBLIC_BASE}/langbang/sentences/v{v}/{key}"
        time.sleep(0.05)
        try:
            yield r2_get_json(url)
        except Exception:
            continue


def add_sentence_audio(add, add_pl):
    """Register EN cue + PL target (all 3 PL styles) audio for every generated
    now-voicing example sentence in the current prompt version's R2 tree."""
    v = read_prompt_version()
    if not v:
        print("  (could not read SENTENCE_PROMPT_VERSION — skipping now-voicing audio)")
        return
    n_sent = 0
    for bundle in _iter_sentence_bundles(v):
        for s in bundle:
            en = (s.get("en") or "").strip()
            pl = (s.get("pl") or "").strip()
            if en:
                add(en, EN_F, LOC_EN)
            if pl:
                add_pl(pl)
            n_sent += 1
    print(f"  registered {n_sent} now-voicing sentences")


def post_batch(anon, batch, timeout=180, max_retries=5):
    """POST a batch with retry-with-backoff. Supabase Edge Functions drop the
    connection (RemoteDisconnected) after sustained load — empirically after ~22
    min of continuous batches the function instance recycles mid-request. Retrying
    with a short pause lets the new instance pick up, and the function is
    idempotent (HEAD-checks R2 per phrase) so the retry is free for cached items."""
    body = json.dumps({"phrases": batch}).encode("utf-8")
    last_err = None
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(
                FUNCTION_URL,
                data=body,
                method="POST",
                headers={
                    "Authorization": f"Bearer {anon}",
                    "Content-Type": "application/json",
                },
            )
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except (urllib.error.URLError,) + http_client_errors() as e:
            last_err = e
            sleep_s = 2 ** attempt  # 1s, 2s, 4s, 8s, 16s
            time.sleep(sleep_s)
    raise last_err if last_err else RuntimeError("post_batch: no error captured")


def http_client_errors():
    """Lazy import so the script still runs if http.client isn't available
    (it always is on CPython but keeps the top-level import list tight)."""
    import http.client
    return (http.client.RemoteDisconnected, http.client.HTTPException, ConnectionError, TimeoutError)


def _run_one_batch(anon, batch, b_idx, b_total):
    """Worker: POST a single batch, return (b_idx, summary_or_None, err_or_None, secs)."""
    bt0 = time.time()
    try:
        resp = post_batch(anon, batch)
    except urllib.error.HTTPError as e:
        return (b_idx, None, f"HTTP {e.code}", time.time() - bt0)
    except Exception as e:
        return (b_idx, None, f"{type(e).__name__}: {e}", time.time() - bt0)
    return (b_idx, resp.get("summary", {}), None, time.time() - bt0)


def main():
    import argparse
    ap = argparse.ArgumentParser()
    # Concurrency: the Edge Function HEAD-checks R2 per phrase and Supabase scales to
    # multiple instances, so 4-6 concurrent batches finish a full pass in ~1h instead
    # of ~5h sequential — and a shorter window is far less likely to be killed by the
    # laptop idle-sleeping mid-run. Default stays 1 (sequential) for safety.
    ap.add_argument("--concurrency", type=int,
                    default=int(os.environ.get("POP_CONCURRENCY", "1")))
    opts = ap.parse_args()
    concurrency = max(1, opts.concurrency)

    anon = read_anon_key()
    phrases = build_phrases()
    n = len(phrases)
    total_chars = sum(len(p["text"]) for p in phrases)
    b_total = (n + BATCH_SIZE - 1) // BATCH_SIZE
    print(f"Built manifest: {n} phrases, {total_chars:,} chars")
    print(f"Est Azure TTS cost (synth all): ${total_chars / 1_000_000 * 16:.2f}")
    print(f"Batching {BATCH_SIZE} per call → {b_total} calls · concurrency={concurrency}")
    print()

    grand = {"requested": 0, "synthesized": 0, "cached": 0, "failed": 0}
    batches = [
        (anon, phrases[i:i + BATCH_SIZE], i // BATCH_SIZE + 1, b_total)
        for i in range(0, n, BATCH_SIZE)
    ]
    t0 = time.time()
    done = 0

    def record(b_idx, summary, err, secs):
        nonlocal done
        done += 1
        if err is not None:
            print(f"  batch {b_idx:3d}/{b_total}: ERROR {err} · {secs:.1f}s "
                  f"(skipped; re-run to retry — idempotent)")
            return
        for k in grand:
            grand[k] += summary.get(k, 0)
        print(f"  batch {b_idx:3d}/{b_total} (#{done} done): {summary} · {secs:5.1f}s · "
              f"running {(time.time()-t0)/60:.1f}min · grand {grand}")

    if concurrency == 1:
        for args in batches:
            record(*_run_one_batch(*args))
    else:
        from concurrent.futures import ThreadPoolExecutor, as_completed
        with ThreadPoolExecutor(max_workers=concurrency) as ex:
            futs = [ex.submit(_run_one_batch, *args) for args in batches]
            for fut in as_completed(futs):
                record(*fut.result())

    total = time.time() - t0
    print()
    print(f"Done in {total/60:.1f} min")
    print(f"Grand totals: {grand}")


if __name__ == "__main__":
    main()
