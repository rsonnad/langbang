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
FUNCTION_URL = "https://aphrrfprbixmhissnjfn.supabase.co/functions/v1/langbang-pregen-audio"
BATCH_SIZE = 100  # ~100s per batch at ~1s/phrase, leaves headroom under 150s limit

# Match AzureTtsClient constants exactly.
PL_F = "pl-PL-ZofiaNeural"
PL_F_SLOW = "pl-PL-ZofiaNeural|slow60v1"
EN_F = "en-US-JennyNeural"
LOC_PL = "pl-PL"
LOC_EN = "en-US"

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

    return out


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


def main():
    anon = read_anon_key()
    phrases = build_phrases()
    n = len(phrases)
    total_chars = sum(len(p["text"]) for p in phrases)
    print(f"Built manifest: {n} phrases, {total_chars:,} chars")
    print(f"Est Azure TTS cost (synth all): ${total_chars / 1_000_000 * 16:.2f}")
    print(f"Batching {BATCH_SIZE} per call → {(n + BATCH_SIZE - 1) // BATCH_SIZE} calls")
    print()

    grand = {"requested": 0, "synthesized": 0, "cached": 0, "failed": 0}
    t0 = time.time()
    for i in range(0, n, BATCH_SIZE):
        batch = phrases[i : i + BATCH_SIZE]
        b_idx = i // BATCH_SIZE + 1
        b_total = (n + BATCH_SIZE - 1) // BATCH_SIZE
        bt0 = time.time()
        try:
            resp = post_batch(anon, batch)
        except urllib.error.HTTPError as e:
            print(f"  batch {b_idx}/{b_total}: HTTP {e.code} — {e.read()[:200]!r}")
            continue
        except Exception as e:
            print(f"  batch {b_idx}/{b_total}: {type(e).__name__}: {e}")
            continue
        s = resp.get("summary", {})
        for k in grand:
            grand[k] += s.get(k, 0)
        elapsed = time.time() - bt0
        running = time.time() - t0
        print(
            f"  batch {b_idx:3d}/{b_total}: {s} · {elapsed:5.1f}s · "
            f"running {running/60:.1f}min · grand {grand}"
        )

    total = time.time() - t0
    print()
    print(f"Done in {total/60:.1f} min")
    print(f"Grand totals: {grand}")


if __name__ == "__main__":
    main()
