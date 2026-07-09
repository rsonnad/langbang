import unittest

from langbangtrans.layout import HudTextState, TextLayoutManager


class LayoutTests(unittest.TestCase):
    def test_layout_limits_width_and_lines(self) -> None:
        state = HudTextState(
            polish_text="Proszę mówić wolniej, ponieważ uczę się polskiego i potrzebuję chwili.",
            english_text="Please speak more slowly because I am learning Polish and need a moment.",
        )
        rendered = TextLayoutManager(columns=28, lines=10).render(state)

        lines = rendered.splitlines()
        self.assertLessEqual(len(lines), 10)
        self.assertTrue(all(len(line) <= 28 for line in lines))
        self.assertEqual(lines[0], "LangBangTrans")

    def test_append_transcript_avoids_duplicate_suffix(self) -> None:
        state = HudTextState()
        state.apply(input_text="Proszę mówić wolniej", mode="append")
        state.apply(input_text="Proszę mówić wolniej", mode="append")

        self.assertEqual(state.polish_text, "Proszę mówić wolniej")


if __name__ == "__main__":
    unittest.main()

