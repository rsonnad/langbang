#!/usr/bin/env python3
"""
One-shot batch: generate past-tense example sentences for every verb in lesson-02.json
via Gemini, save them as a bundled APK asset, then ask the langbang-pregen-audio Edge
Function to synthesise + upload the corresponding mp3s to R2.

Mirrors the Kotlin prompt + parsing logic so generated sentences are shaped exactly
like what the on-device flow would produce.

Run from the repo root:
    python3 scripts/pregen-past-tense.py
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parent.parent
LESSON_PATH = REPO / "app/src/main/assets/lesson-02.json"
ASSET_OUT = REPO / "app/src/main/assets/verb-past-sentences-pregen.json"
LOCAL_PROPS = REPO / "local.properties"

PRONOUNS = {
    "1sg": "ja",
    "2sg": "ty",
    "3sg": "on",
    "1pl": "my",
    "2pl": "wy",
    "3pl": "oni",
}

EN_VOICE = "en-US-JennyNeural"
PL_VOICE = "pl-PL-ZofiaNeural"
PL_VOICE_SLOW = "pl-PL-ZofiaNeural|slow60v1"
LOCALE_EN = "en-US"
LOCALE_PL = "pl-PL"


def load_props() -> dict[str, str]:
    props: dict[str, str] = {}
    for line in LOCAL_PROPS.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, _, v = line.partition("=")
            props[k.strip()] = v.strip()
    return props


def gemini_post(api_key: str, prompt: str) -> str:
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.1,
            "responseMimeType": "application/json",
        },
    }
    url = (
        "https://generativelanguage.googleapis.com/v1beta/"
        f"models/gemini-2.5-flash:generateContent?key={api_key}"
    )
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read().decode("utf-8")


def build_past_prompt(lemma: str, en: str, past_forms: dict[str, str]) -> str:
    forms_blurb = ", ".join(f"{k}={v}" for k, v in past_forms.items())
    return (
        f'Write 20 very simple example Polish sentences that use the verb "{lemma}" '
        f"(English: {en}). "
        "Use only the most common everyday vocabulary that a beginner would know "
        "(food, family, basic objects, places). "
        "CRITICAL NATURALNESS RULE: every sentence must be something a real person "
        "would actually say in ordinary conversation. Grammatical correctness is NOT "
        "enough — the sentence must be idiomatic and commonplace in BOTH Polish AND "
        'English. Before including a sentence, ask yourself: "Would a native speaker '
        'actually say this in real life?" If the answer is no, pick a different noun '
        "or object. Use the right verb collocation for the noun (you LOOKED AT a "
        "painting, you didn't WATCH it; you LISTENED TO music, you didn't HEAR-TO it). "
        "Reject contrived or whimsical scenarios. Prefer the most boring, ordinary "
        "everyday combinations possible (drank water, ate bread, saw the dog, read a "
        "book, bought milk, had a sister). "
        "Every sentence must be in the PAST TENSE. "
        "STRICT REQUIREMENT: every sentence's main verb must be one of these "
        "past-tense conjugations — do NOT use any other conjugation and do NOT "
        f"fall back to present tense: {forms_blurb}. "
        "These are the collapsed masculine-singular (1sg/2sg/3sg) and virile "
        "masculine-personal plural (1pl/2pl/3pl) past forms. Use them exactly. "
        "Vary which of those allowed forms you use across the 20 sentences. "
        "The English translations must also be in the simple past tense "
        '(e.g. "I drank water", not "I drink water"). '
        "For the 2pl form (wy), render the English subject as \"Y'all\" at the start "
        "of a sentence and \"y'all\" mid-sentence — NEVER write \"You (plural)\", "
        "\"you (pl.)\", or any \"(plural)\" annotation. For 1pl use \"We\"; for 3pl "
        "use \"They\". "
        "Keep each Polish sentence to 5 words or fewer. "
        "PREPOSITION COVERAGE: at least 5 of the 20 sentences must use a Polish "
        "preposition. Include AT LEAST ONE sentence for each of these five common "
        "prepositions: w (in/at), na (on/at), do (to), z (with/from), o (about). "
        "Examples: \"Byłem w domu\", \"Pisałem na komputerze\", \"Szedłem do sklepu\", "
        "\"Rozmawiałem z bratem\", \"Myślałem o tobie\". Use the correct case. "
        "For each sentence, return THREE fields: "
        '(1) "pl": the Polish sentence with correct diacritics and natural capitalization. '
        '(2) "en": a GRAMMATICALLY CORRECT, NATURAL English translation in simple past. '
        "    Use proper English articles where English requires them. "
        "    Use natural English prepositions. Do NOT paraphrase to a different scene; keep the same "
        "    subject and meaning, just render it in idiomatic English. "
        '(3) "literal": a WORD-FOR-WORD gloss that preserves the Polish word order and '
        "    matches each Polish word to its closest English equivalent. Skip articles "
        "    that don't exist in the Polish. Keep the literal Polish prepositions. "
        '(4) "words": an ARRAY of per-token mappings in the same left-to-right order '
        "    as the Polish sentence. Each element is {\"pl\":\"polish-token\",\"en\":"
        '    "english-gloss"}. Tokens correspond to whitespace-separated Polish words. '
        "    When a Polish word maps to a multi-word English gloss use a hyphen. The number "
        '    of "words" entries MUST equal the Polish word count exactly. '
        "Return ONLY a JSON array (no prose, no markdown fence) where each element has "
        'the exact shape {"pl":"...","en":"...","literal":"...",'
        '"words":[{"pl":"...","en":"..."}]}.'
    )


def parse_sentences(raw: str) -> list[dict[str, Any]]:
    root = json.loads(raw)
    text = root["candidates"][0]["content"]["parts"][0]["text"].strip()
    # Some Gemini responses wrap arrays in markdown fences despite the constraint.
    text = re.sub(r"^```(?:json)?", "", text).rstrip("`").strip()
    arr = json.loads(text)
    cleaned: list[dict[str, Any]] = []
    for el in arr:
        pl = (el.get("pl") or "").strip()
        en = (el.get("en") or "").strip()
        lit = (el.get("literal") or "").strip() or None
        words_raw = el.get("words")
        words = None
        if isinstance(words_raw, list):
            words = []
            for w in words_raw:
                wpl = (w.get("pl") or "").strip()
                wen = (w.get("en") or "").strip()
                if wpl:
                    words.append({"pl": wpl, "en": wen})
            if not words:
                words = None
        if not pl or not en:
            continue
        out: dict[str, Any] = {"pl": pl, "en": en}
        if lit:
            out["literal"] = lit
        if words:
            out["words"] = words
        cleaned.append(out)
    if not cleaned:
        raise RuntimeError("Gemini returned no usable sentences")
    return cleaned[:20]


def edge_post(supabase_url: str, anon: str, phrases: list[dict[str, str]]) -> dict:
    body = {"phrases": phrases}
    req = urllib.request.Request(
        f"{supabase_url}/functions/v1/langbang-pregen-audio",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {anon}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(r.read().decode("utf-8"))


def main() -> int:
    props = load_props()
    gemini_key = props["GEMINI_API_KEY"]
    supabase_url = props["SUPABASE_URL"].rstrip("/")
    anon = props["SUPABASE_ANON_KEY"]

    lesson = json.loads(LESSON_PATH.read_text(encoding="utf-8"))
    verbs = lesson["verbs"]
    print(f"Loaded {len(verbs)} verbs from lesson-02.json", flush=True)

    # ── Step 1: Generate past-tense sentences via Gemini ───────────────────────
    out_map: dict[str, list[dict[str, Any]]] = {}
    if ASSET_OUT.exists():
        out_map = json.loads(ASSET_OUT.read_text(encoding="utf-8"))
        print(f"Resuming — {len(out_map)} verb(s) already generated", flush=True)

    for i, verb in enumerate(verbs, 1):
        lemma = verb["lemma"]
        past = verb.get("past_forms")
        if not past:
            print(f"  [{i:2}/{len(verbs)}] skip {lemma} — no past_forms")
            continue
        if lemma in out_map and len(out_map[lemma]) >= 15:
            print(f"  [{i:2}/{len(verbs)}] cached {lemma} ({len(out_map[lemma])} sentences)")
            continue
        prompt = build_past_prompt(lemma, verb["en"], past)
        for attempt in range(3):
            try:
                raw = gemini_post(gemini_key, prompt)
                sentences = parse_sentences(raw)
                out_map[lemma] = sentences
                ASSET_OUT.write_text(
                    json.dumps(out_map, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
                print(f"  [{i:2}/{len(verbs)}] {lemma} → {len(sentences)} sentences")
                break
            except Exception as e:
                wait = 4 * (attempt + 1)
                print(f"  [{i:2}/{len(verbs)}] {lemma} ERR attempt {attempt+1}: {e} — retry in {wait}s", file=sys.stderr)
                time.sleep(wait)
        else:
            print(f"  [{i:2}/{len(verbs)}] {lemma} FAILED after 3 attempts", file=sys.stderr)

    print(f"\nGenerated past-tense sentences for {len(out_map)} verbs.", flush=True)

    # ── Step 2: Build audio manifest ───────────────────────────────────────────
    phrases: list[dict[str, str]] = []

    def add_pl(text: str):
        text = text.strip()
        if not text:
            return
        phrases.append({"text": text, "voice": PL_VOICE, "locale": LOCALE_PL})
        phrases.append({"text": text, "voice": PL_VOICE_SLOW, "locale": LOCALE_PL})

    def add_en(text: str):
        text = text.strip()
        if not text:
            return
        phrases.append({"text": text, "voice": EN_VOICE, "locale": LOCALE_EN})

    # Past-form pronoun audios: "ja byłem", "ty byłeś", … for every verb.
    for verb in verbs:
        past = verb.get("past_forms") or {}
        for k, form in past.items():
            spoken = f"{PRONOUNS.get(k, '')} {form}".strip()
            add_pl(spoken)

    # All past-tense sentences (en + pl + pl-slow).
    for lemma, sentences in out_map.items():
        for s in sentences:
            add_en(s["en"])
            add_pl(s["pl"])

    # Dedup on (text, voice, locale).
    seen: set[tuple[str, str, str]] = set()
    unique: list[dict[str, str]] = []
    for p in phrases:
        key = (p["text"], p["voice"], p["locale"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(p)

    print(f"\nAudio manifest: {len(unique)} unique phrases (was {len(phrases)} before dedup).", flush=True)

    # ── Step 3: POST to the Edge Function in batches ───────────────────────────
    BATCH = 80
    total_synth = 0
    total_cached = 0
    total_failed = 0
    sample_urls: list[str] = []
    for start in range(0, len(unique), BATCH):
        chunk = unique[start:start + BATCH]
        idx = f"{start//BATCH + 1}/{(len(unique)+BATCH-1)//BATCH}"
        for attempt in range(3):
            try:
                resp = edge_post(supabase_url, anon, chunk)
                summary = resp.get("summary", {})
                total_synth += summary.get("synthesized", 0)
                total_cached += summary.get("cached", 0)
                total_failed += summary.get("failed", 0)
                if not sample_urls:
                    for m in resp.get("manifest", []):
                        if m.get("url"):
                            sample_urls.append(m["url"])
                            if len(sample_urls) >= 3:
                                break
                print(
                    f"  batch {idx} ({len(chunk)} phrases) → "
                    f"synth={summary.get('synthesized', 0)} "
                    f"cached={summary.get('cached', 0)} "
                    f"failed={summary.get('failed', 0)}"
                )
                break
            except Exception as e:
                wait = 6 * (attempt + 1)
                print(f"  batch {idx} ERR attempt {attempt+1}: {e} — retry in {wait}s", file=sys.stderr)
                time.sleep(wait)
        else:
            print(f"  batch {idx} FAILED after 3 attempts", file=sys.stderr)

    print(
        f"\nDone. Edge function totals: synthesized={total_synth} "
        f"cached={total_cached} failed={total_failed}",
        flush=True,
    )
    print("Sample URLs:")
    for u in sample_urls:
        print(f"  {u}")
    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
