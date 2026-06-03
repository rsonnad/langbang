#!/usr/bin/env python3
"""
LangBangML backend guard.

The old past-tense generator used direct Gemini plus a legacy edge function for
audio prewarm. LangBangML is Cloudflare-only, so this workflow stays disabled
until sentence generation is implemented as a Cloudflare Worker endpoint.
"""

from __future__ import annotations

import sys


def main() -> int:
    print(
        "pregen-past-tense.py is disabled for LangBangML. "
        "Move sentence generation behind the Cloudflare Worker before using "
        "this batch path.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
