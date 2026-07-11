#!/usr/bin/env python3
"""Pre-generate every audio unit needed by a LangBangML cloud content pack.

The Worker is the only synthesizer: it checks R2 first, creates only missing MP3s,
and returns public cache URLs. This script deliberately obtains locale and voice
configuration from the live bootstrap rather than hard-coding Polish assumptions,
so the same pipeline warms EN→JA and future language packs safely.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request


API_BASE = "https://langbangml-api.langbangml.workers.dev"
USER_AGENT = "langbangml-audio-warmer/1.0"
BATCH_SIZE = 20
CONTENT_ENTRY_PARENTS = {"verbs", "adjectives", "adverbs", "nouns"}
FORM_KEYS = {"forms", "past_forms", "nom", "acc", "gen", "case_forms"}
SKIP_DESCENT = {
    "source", "target", "pl", "en", "lemma", "forms", "past_forms", "nom", "acc", "gen",
    "case_forms", "literal", "words", "schema", "sourceLocale", "targetLocale", "sourceField",
    "targetField", "fieldLocales", "id", "title", "summary", "subtitle", "description", "name",
    "ipa", "englishApproximation", "letter", "gender", "focus", "index", "person",
}


def get_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(url: str, payload: dict, retries: int = 4) -> dict:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(
                url,
                data=body,
                method="POST",
                headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
            )
            with urllib.request.urlopen(request, timeout=180) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            time.sleep(2**attempt)
    raise last_error or RuntimeError("audio manifest request failed")


def add(out: list[dict], seen: set[str], text: object, locale: str, voice: str) -> None:
    normalized = str(text or "").strip()
    if not normalized or not locale or not voice:
        return
    key = f"{locale}|{voice}|{normalized}"
    if key in seen:
        return
    seen.add(key)
    out.append({"text": normalized, "locale": locale, "voice": voice})


def string_leaves(value: object) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return [leaf for item in value for leaf in string_leaves(item)]
    if isinstance(value, dict):
        return [leaf for item in value.values() for leaf in string_leaves(item)]
    return []


def build_phrases(bootstrap: dict) -> list[dict]:
    pair = bootstrap["languagePair"]
    source_locale = pair["sourceLocale"]
    target_locale = pair["targetLocale"]
    source_voice = pair["sourceVoice"]
    target_voice = pair["targetVoice"]
    target_slow_voices = pair.get("targetSlowVoices") or []
    out: list[dict] = []
    seen: set[str] = set()

    def add_source(text: object) -> None:
        add(out, seen, text, source_locale, source_voice)

    def add_target(text: object, slow: bool = True) -> None:
        add(out, seen, text, target_locale, target_voice)
        if slow:
            for voice in target_slow_voices:
                add(out, seen, text, target_locale, voice)

    def walk(value: object, parent_key: str = "", field_locales: dict | None = None) -> None:
        if isinstance(value, list):
            for item in value:
                walk(item, parent_key, field_locales)
            return
        if not isinstance(value, dict):
            return

        current_field_locales = value.get("fieldLocales")
        if not isinstance(current_field_locales, dict):
            current_field_locales = field_locales or {}

        if "source" in value:
            add_source(value.get("source"))
        if "target" in value:
            add_target(value.get("target"))
        if "en" in value:
            locale = str(current_field_locales.get("en") or source_locale)
            (add_source if locale == source_locale else add_target)(value.get("en"))
        if "pl" in value:
            locale = str(current_field_locales.get("pl") or target_locale)
            (add_target if locale == target_locale else add_source)(value.get("pl"))

        if parent_key in CONTENT_ENTRY_PARENTS and "lemma" in value:
            add_target(value["lemma"])
        for key in FORM_KEYS:
            for leaf in string_leaves(value.get(key)):
                add_target(leaf)

        for key, child in value.items():
            if key not in SKIP_DESCENT:
                walk(child, key, current_field_locales)

    for lesson in bootstrap.get("content", {}).get("lessons", []):
        walk(lesson.get("payload") or {})
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--instance", default="langbangml-en-ja")
    parser.add_argument("--api-base", default=API_BASE)
    args = parser.parse_args()

    api_base = args.api_base.rstrip("/")
    bootstrap = get_json(f"{api_base}/v1/instances/{args.instance}/bootstrap")
    phrases = build_phrases(bootstrap)
    print(f"instance={args.instance} audio_units={len(phrases)}")

    synthesized = cached = failed = 0
    for start in range(0, len(phrases), BATCH_SIZE):
        batch = phrases[start : start + BATCH_SIZE]
        response = post_json(f"{api_base}/v1/audio/manifest", {"phrases": batch})
        summary = response.get("summary") or {}
        synthesized += int(summary.get("synthesized") or 0)
        cached += int(summary.get("cached") or 0)
        failed += int(summary.get("failed") or 0)
        print(
            f"batch {start // BATCH_SIZE + 1}: "
            f"synth={summary.get('synthesized', 0)} "
            f"cached={summary.get('cached', 0)} "
            f"failed={summary.get('failed', 0)}"
        )

    print(f"totals synthesized={synthesized} cached={cached} failed={failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
