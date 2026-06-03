#!/usr/bin/env python3
"""
LangBangML backend guard.

The old LangBang script used a legacy edge function to prewarm audio. That path
is intentionally disabled in LangBangML: audio must go through the Cloudflare
Worker at /v1/audio/manifest so D1 metadata and R2 files stay in sync.

Use:
    python3 scripts/populate-langbangml-pl-en-audio.py
"""

from __future__ import annotations

import sys


def main() -> int:
    print(
        "populate-r2-audio.py is disabled for LangBangML. "
        "Use scripts/populate-langbangml-pl-en-audio.py or POST to "
        "https://langbangml-api.langbangml.workers.dev/v1/audio/manifest.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
