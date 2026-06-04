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
prefs="app/src/main/kotlin/com/sponic/langbang/data/PracticePrefsStore.kt"
word_header="app/src/main/kotlin/com/sponic/langbang/ui/common/WordListPlaybackHeader.kt"
now_body="app/src/main/kotlin/com/sponic/langbang/ui/common/NowVoicingBody.kt"
outlined_polish="app/src/main/kotlin/com/sponic/langbang/ui/common/OutlinedPolishText.kt"
polish_syllables="app/src/main/kotlin/com/sponic/langbang/ui/common/PolishSyllables.kt"
grammar_visuals="app/src/main/kotlin/com/sponic/langbang/ui/common/GrammarVisuals.kt"
random_config="app/src/main/kotlin/com/sponic/langbang/data/RandomConfigStore.kt"

require_fixed "$verbs" "VerbPhrasesPanel(" "Verbs/Phrases panel"
require_fixed "$verbs" "PhraseExamplesControls(state = state, allVerbs = allVerbs)" "Verbs/Phrases controls"
require_fixed "$verbs" "includeRelatedPhrases: Boolean = false" "related phrase playback flag"
require_fixed "$verbs" "state.playAll(allVerbs, includeRelatedPhrases = true)" "Verbs/Phrases play includes related phrases"
require_fixed "$verbs" "leadingLabel = \"with\"" "verb play limit leading label"
require_fixed "$verbs" "trailingLabel = \"vars\"" "verb play limit trailing label"
require_fixed "$verbs" "playByPronounConjugations(" "by-pronoun Play All"
require_fixed "$verbs" "byPronounPlayCount(" "by-pronoun play count"

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
