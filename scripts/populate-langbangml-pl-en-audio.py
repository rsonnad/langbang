#!/usr/bin/env python3
"""
Warm the Cloudflare R2 audio cache for LangBangML's Polish-speaker English-study
instance. This uses the Cloudflare Worker only.

It reads the live pl-en bootstrap payload, extracts source Polish prompts and
target English study lines, and POSTs them to /v1/audio/manifest in small batches.
The Worker HEAD-checks R2 first and synthesizes only missing files.
"""

from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.request

API_BASE = "https://langbangml-api.alpacapps.workers.dev"
INSTANCE_ID = "langbangml-pl-en"
BATCH_SIZE = 30
USER_AGENT = "curl/8.4.0"

PL_F = "pl-PL-ZofiaNeural"
PL_F_SLOW = "pl-PL-ZofiaNeural|slow60v1"
PL_F_SLOW_ART = "pl-PL-ZofiaNeural|slowart1"
EN_F = "en-US-JennyNeural"
EN_F_SLOW = "en-US-JennyNeural|slow60v1"
EN_F_SLOW_ART = "en-US-JennyNeural|slowart1"
LOC_PL = "pl-PL"
LOC_EN = "en-US"


def get_json(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(url: str, payload: dict, retries: int = 4) -> dict:
    body = json.dumps(payload).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(
                url,
                data=body,
                method="POST",
                headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
            )
            with urllib.request.urlopen(req, timeout=180) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
            time.sleep(2**attempt)
    raise last_error or RuntimeError("POST failed without an exception")


def add(out: list[dict], seen: set[str], text: str, voice: str, locale: str) -> None:
    text = (text or "").strip()
    if not text:
        return
    key = f"{locale}|{voice}|{text}"
    if key in seen:
        return
    seen.add(key)
    out.append({"text": text, "voice": voice, "locale": locale})


def build_phrases(bootstrap: dict) -> list[dict]:
    out: list[dict] = []
    seen: set[str] = set()
    for lesson in bootstrap.get("content", {}).get("lessons", []):
        payload = lesson.get("payload") or {}
        for group in payload.get("groups", []):
            for sentence in group.get("sentences", []):
                source = sentence.get("source") or sentence.get("pl") or ""
                target = sentence.get("target") or sentence.get("en") or ""
                add(out, seen, source, PL_F, LOC_PL)
                add(out, seen, source, PL_F_SLOW, LOC_PL)
                add(out, seen, source, PL_F_SLOW_ART, LOC_PL)
                add(out, seen, target, EN_F, LOC_EN)
                add(out, seen, target, EN_F_SLOW, LOC_EN)
                add(out, seen, target, EN_F_SLOW_ART, LOC_EN)
    return out


def main() -> int:
    bootstrap = get_json(f"{API_BASE}/v1/instances/{INSTANCE_ID}/bootstrap")
    phrases = build_phrases(bootstrap)
    print(f"instance={INSTANCE_ID} phrases={len(phrases)}")
    synthesized = cached = failed = 0
    for start in range(0, len(phrases), BATCH_SIZE):
        batch = phrases[start : start + BATCH_SIZE]
        response = post_json(f"{API_BASE}/v1/audio/manifest", {"phrases": batch})
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
