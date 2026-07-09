from __future__ import annotations

import re
import textwrap
from dataclasses import dataclass


_SPACE_RE = re.compile(r"[^\S\n]+")


@dataclass
class HudTextState:
    polish_text: str = ""
    english_text: str = ""

    def apply(
        self,
        *,
        input_text: str | None = None,
        output_text: str | None = None,
        mode: str = "append",
    ) -> None:
        if input_text:
            self.polish_text = _apply_text(self.polish_text, input_text, mode)
        if output_text:
            self.english_text = _apply_text(self.english_text, output_text, mode)


@dataclass(frozen=True)
class TextLayoutManager:
    columns: int = 28
    lines: int = 10

    def render(self, state: HudTextState) -> str:
        polish = _clean_text(state.polish_text) or "..."
        english = _clean_text(state.english_text) or "..."

        pl_lines = self._wrap_labeled_block("PL", polish)
        en_lines = self._wrap_labeled_block("EN", english)

        frame = ["LangBangTrans"]
        remaining = self.lines - len(frame)
        if len(pl_lines) + len(en_lines) <= remaining:
            frame.extend(pl_lines + en_lines)
        else:
            pl_budget = max(2, min(4, remaining // 2))
            en_budget = max(1, remaining - pl_budget)
            frame.extend(_tail(pl_lines, pl_budget))
            frame.extend(_tail(en_lines, en_budget))

        return "\n".join(_fit(line, self.columns) for line in frame[: self.lines])

    def _wrap_labeled_block(self, label: str, text: str) -> list[str]:
        prefix = f"{label}: "
        wrapper = textwrap.TextWrapper(
            width=self.columns,
            subsequent_indent=" " * len(prefix),
            break_long_words=True,
            break_on_hyphens=False,
            replace_whitespace=False,
            drop_whitespace=True,
        )
        return wrapper.wrap(prefix + text) or [prefix.rstrip()]


def _apply_text(current: str, incoming: str, mode: str) -> str:
    incoming = _clean_text(incoming)
    if not incoming:
        return current
    if mode == "replace":
        return incoming
    if mode != "append":
        raise ValueError("transcript mode must be append or replace")
    if not current:
        return incoming
    if current.endswith(incoming):
        return current
    spacer = "" if current.endswith((" ", "\n")) or incoming.startswith((" ", "\n")) else " "
    return current + spacer + incoming


def _clean_text(value: str) -> str:
    return _SPACE_RE.sub(" ", value).strip()


def _tail(lines: list[str], count: int) -> list[str]:
    return lines[-count:] if len(lines) > count else lines


def _fit(line: str, width: int) -> str:
    if len(line) <= width:
        return line
    return line[:width]

