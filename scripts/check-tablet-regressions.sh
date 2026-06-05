#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

fail=0

require_fixed() {
  local file="$1"
  local text="$2"
  local label="$3"
  if ! grep -Fq "$text" "$file"; then
    echo "[missing] $label" >&2
    echo "  expected in $file: $text" >&2
    fail=1
  fi
}

require_absent_fixed() {
  local file="$1"
  local text="$2"
  local label="$3"
  if grep -Fq "$text" "$file"; then
    echo "[unexpected] $label" >&2
    echo "  found in $file: $text" >&2
    fail=1
  fi
}

verbs="app/src/main/kotlin/com/sponic/langbang/ui/lessons/VerbsTab.kt"
lesson_screen="app/src/main/kotlin/com/sponic/langbang/ui/lessons/LessonScreen.kt"
prefs="app/src/main/kotlin/com/sponic/langbang/data/PracticePrefsStore.kt"
word_header="app/src/main/kotlin/com/sponic/langbang/ui/common/WordListPlaybackHeader.kt"
now_body="app/src/main/kotlin/com/sponic/langbang/ui/common/NowVoicingBody.kt"
outlined_polish="app/src/main/kotlin/com/sponic/langbang/ui/common/OutlinedPolishText.kt"
polish_syllables="app/src/main/kotlin/com/sponic/langbang/ui/common/PolishSyllables.kt"
grammar_visuals="app/src/main/kotlin/com/sponic/langbang/ui/common/GrammarVisuals.kt"
random_config="app/src/main/kotlin/com/sponic/langbang/data/RandomConfigStore.kt"
quizzes="app/src/main/kotlin/com/sponic/langbang/ui/quizzes/QuizzesScreen.kt"
practice_quiz="app/src/main/kotlin/com/sponic/langbang/ui/quizzes/PracticeQuiz.kt"
multiple_choice_quiz="app/src/main/kotlin/com/sponic/langbang/ui/quizzes/MultipleChoiceQuiz.kt"
theme="app/src/main/kotlin/com/sponic/langbang/ui/theme/LangbangTheme.kt"
app_shell="app/src/main/kotlin/com/sponic/langbang/ui/LangbangApp.kt"

require_absent_fixed "$lesson_screen" "L2Tab" "legacy Verbs/Phrases sub-tab state"
require_absent_fixed "$verbs" "VerbPhrasesPanel(" "legacy Verbs/Phrases panel"
require_absent_fixed "$verbs" "PhraseExamplesControls(" "legacy Verbs/Phrases controls"
require_absent_fixed "$verbs" "includeRelatedPhrases" "legacy related phrase playback flag"
require_absent_fixed "$verbs" "L2TabChips(" "legacy Verbs/Phrases chip row"
require_absent_fixed "$verbs" "playByPronounConjugations(" "legacy by-pronoun Play All"
require_absent_fixed "$verbs" "byPronounPlayCount(" "legacy by-pronoun play count"
require_absent_fixed "$verbs" "Sent. quiz" "Verbs screen Sent quiz button"
require_fixed "$verbs" "label = \"+ Slow\"" "renamed slow-first toggle"
require_fixed "$verbs" "includeWordTypeVariations" "word-type variation playback"
require_fixed "$verbs" "targetPlayCount(allVerbs)" "word-type variation play count"
require_fixed "$verbs" "leadingLabel = \"with\"" "verb play limit leading label"
require_fixed "$verbs" "trailingLabel = \"vars\"" "verb play limit trailing label"
require_fixed "$quizzes" "QuizMode.SentenceAudio" "Quiz page Sentence audio card"
require_fixed "$quizzes" "Text(\"End quiz\"" "Sentence audio visible End quiz action"
require_fixed "$practice_quiz" "Text(\"End quiz\"" "practice quiz visible End quiz action"
require_fixed "$multiple_choice_quiz" "Text(\"End quiz\"" "multiple-choice quiz visible End quiz action"
require_fixed "$theme" "val Bar = Color(0xFF4A0B12)" "visible dark red top strip"
require_fixed "$app_shell" ".padding(start = 10.dp, end = 12.dp, top = 6.dp)" "top tabs flush to strip bottom"
require_fixed "$app_shell" "modifier = Modifier.height(50.dp)" "taller tab strip"
require_fixed "$app_shell" "shape = RoundedCornerShape(" "tab-shaped top navigation"
require_fixed "$lesson_screen" "VerbsTab(app, prefetch, nowVoicing)" "Lesson routes directly to VerbsTab"
require_absent_fixed "$lesson_screen" "L2Tab.Phrases -> PhrasesPane" "legacy lesson phrase pane route"
require_absent_fixed "$lesson_screen" "private fun PhrasesPane" "legacy lesson phrase pane implementation"

require_fixed "$prefs" "verbPhraseIncludeAdjectives()" "verb phrase adjective preference"
require_fixed "$prefs" "verbPhraseIncludeAdverbs()" "verb phrase adverb preference"
require_fixed "$prefs" "verbPhraseIncludeNouns()" "verb phrase noun preference"

require_absent_fixed "$word_header" "leadingLabel: String = \"groups of\"" "implicit groups-of default"
require_fixed "$word_header" "leadingLabel: String," "explicit leading label parameter"
require_fixed "$word_header" "trailingLabel: String?," "explicit trailing label parameter"

require_fixed "$now_body" "syllableShading: Boolean = true" "Now Voicing syllable shading parameter"
require_fixed "$now_body" "val showSyllableShading = syllableShadingAvailable && syllableShading" "Now Voicing syllable shading gate"
require_fixed "$now_body" "syllableShades = shades" "Now Voicing passes syllable shades"
require_fixed "$now_body" "GrammarVisuals.SyllableShading.ShadeA to GrammarVisuals.SyllableShading.ShadeB" "Now Voicing shade colors"
require_fixed "$outlined_polish" "drawSyllableShadeBands(" "syllable shade band drawing"
require_fixed "$outlined_polish" "englishPronunciationGuide(" "phonetic helper labels"
require_fixed "$outlined_polish" "underlineY = bottom - underlineWidth / 2f" "accent line at shade edge"
require_fixed "$polish_syllables" "fun polishSyllables(word: String): List<String>" "Polish syllabifier"
require_fixed "$grammar_visuals" "object SyllableShading" "syllable shading palette"
require_fixed "$random_config" "prefs.getBoolean(KEY_SYLLABLE_SHADING, true)" "syllable shading default on"

if [[ "$fail" -ne 0 ]]; then
  echo "Tablet regression check failed." >&2
  exit 1
fi

echo "Tablet regression check passed."
