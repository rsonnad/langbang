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
adjectives="app/src/main/kotlin/com/sponic/langbang/ui/lessons/AdjectivesScreen.kt"
adverbs="app/src/main/kotlin/com/sponic/langbang/ui/lessons/AdverbsScreen.kt"
nouns="app/src/main/kotlin/com/sponic/langbang/ui/lessons/NounsScreen.kt"
now_voicing_selection="app/src/main/kotlin/com/sponic/langbang/ui/lessons/NowVoicingSelection.kt"
theme="app/src/main/kotlin/com/sponic/langbang/ui/theme/LangbangTheme.kt"
app_shell="app/src/main/kotlin/com/sponic/langbang/ui/LangbangApp.kt"
phrases="app/src/main/kotlin/com/sponic/langbang/ui/phrases/PhrasesScreen.kt"
settings="app/src/main/kotlin/com/sponic/langbang/ui/settings/SettingsScreen.kt"
auth_store="app/src/main/kotlin/com/sponic/langbang/cloud/AuthStore.kt"
google_sign_in="app/src/main/kotlin/com/sponic/langbang/cloud/GoogleSignInHelper.kt"
phrase_sync="app/src/main/kotlin/com/sponic/langbang/cloud/PhraseSyncService.kt"
cloud_backend="app/src/main/kotlin/com/sponic/langbang/cloud/CloudBackendClient.kt"
gemini="app/src/main/kotlin/com/sponic/langbang/integrations/GeminiClient.kt"
phrase_audit="scripts/audit-phrase-quality.mjs"

require_absent_fixed "$lesson_screen" "L2Tab" "legacy Verbs/Phrases sub-tab state"
require_absent_fixed "$verbs" "VerbPhrasesPanel(" "legacy Verbs/Phrases panel"
require_absent_fixed "$verbs" "PhraseExamplesControls(" "legacy Verbs/Phrases controls"
require_absent_fixed "$verbs" "includeRelatedPhrases" "legacy related phrase playback flag"
require_absent_fixed "$verbs" "L2TabChips(" "legacy Verbs/Phrases chip row"
require_absent_fixed "$verbs" "playByPronounConjugations(" "legacy by-pronoun Play All"
require_absent_fixed "$verbs" "byPronounPlayCount(" "legacy by-pronoun play count"
require_absent_fixed "$verbs" "Sent. quiz" "Verbs screen Sent quiz button"
require_absent_fixed "$verbs" "val label = if (playing) \"Stop\"" "duplicate Verbs toolbar stop button"
require_absent_fixed "$verbs" "Generating required phrases ·" "blocking Gemini generation during Verbs Play"
require_fixed "$verbs" "label = \"Pronoun\"" "Pronoun checkbox before phrase word types"
require_fixed "$verbs" "label = \"helper\"" "helper verb phrase checkbox"
require_fixed "$verbs" "sentencePassesCommonUsageGate" "phrase common-usage gate"
require_fixed "$verbs" "TokenPair(\"ochotę\"" "common Polish helper construction support"
require_fixed "$verbs" "minimumPhraseWordCount()" "required phrase minimum word count"
require_fixed "$verbs" "buildLocalPhraseSentences" "local phrase fallback for Verbs Play"
require_fixed "$verbs" "phraseVerbCuesFor" "local phrase verb-form queue"
require_fixed "$verbs" "showPhraseGenerationNotice()" "phrase-generation ETA notice"
require_fixed "$verbs" "playLabel = if (playCount == null) \"Play phrases\" else null" "no overpromised phrase play count"
require_fixed "$verbs" "includeWordTypeVariations" "word-type variation playback"
require_fixed "$verbs" "targetPlayCount(allVerbs)" "word-type variation play count"
require_fixed "$verbs" "leadingLabel = \"with\"" "verb play limit leading label"
require_fixed "$verbs" "trailingLabel = \"vars\"" "verb play limit trailing label"
require_fixed "$quizzes" "QuizMode.SentenceAudio" "Quiz page Sentence audio card"
require_fixed "$quizzes" "Text(\"End quiz\"" "Sentence audio visible End quiz action"
require_fixed "$practice_quiz" "Text(\"End quiz\"" "practice quiz visible End quiz action"
require_fixed "$multiple_choice_quiz" "Text(\"End quiz\"" "multiple-choice quiz visible End quiz action"
require_fixed "$theme" "val Bar = Color(0xFF4A0B12)" "visible dark red top strip"
require_fixed "$app_shell" ".padding(start = 6.dp, end = 12.dp, top = 6.dp)" "top tabs flush to strip bottom"
require_fixed "$app_shell" "modifier = Modifier.height(50.dp)" "taller tab strip"
require_fixed "$app_shell" "shape = RoundedCornerShape(" "tab-shaped top navigation"
require_fixed "app/src/main/kotlin/com/sponic/langbang/ui/common/NowVoicingPanel.kt" "label = \"English\"" "Now Voicing English toggle"
require_fixed "app/src/main/kotlin/com/sponic/langbang/ui/common/NowVoicingPanel.kt" "label = \"Slow\"" "Now Voicing Slow toggle"
require_fixed "$now_voicing_selection" "nowVoicingVerb" "Now Voicing verb detail sync"
require_fixed "$adjectives" "nowVoicingAdjective(activeNowVoicing" "Now Voicing adjective detail sync"
require_fixed "$adverbs" "nowVoicingAdverb(activeNowVoicing" "Now Voicing adverb detail sync"
require_fixed "$nouns" "nowVoicingNoun(activeNowVoicing" "Now Voicing noun detail sync"
require_fixed "$lesson_screen" "VerbsTab(app, prefetch, nowVoicing)" "Lesson routes directly to VerbsTab"
require_absent_fixed "$lesson_screen" "L2Tab.Phrases -> PhrasesPane" "legacy lesson phrase pane route"
require_absent_fixed "$lesson_screen" "private fun PhrasesPane" "legacy lesson phrase pane implementation"

require_fixed "$prefs" "verbPhraseIncludeAdjectives()" "verb phrase adjective preference"
require_fixed "$prefs" "verbPhraseIncludeAdverbs()" "verb phrase adverb preference"
require_fixed "$prefs" "verbPhraseIncludeNouns()" "verb phrase noun preference"
require_fixed "$prefs" "verbPhraseIncludePronouns()" "verb phrase pronoun preference"
require_fixed "$prefs" "verbPhraseIncludeHelperVerb()" "verb phrase helper-verb preference"
require_fixed "$prefs" "rejectedVerbPhraseKeys()" "persisted rejected phrase keys"
require_fixed "$auth_store" "customItemGateSatisfied" "custom phrase account gate preference"
require_fixed "$auth_store" "skippedCustomSyncWarning" "custom phrase local-only skip warning"
require_fixed "$google_sign_in" "GoogleIdTokenCredential" "real Google ID token sign-in helper"
require_fixed "$phrase_sync" "syncUserPhrases" "user phrase sync backend call"
require_fixed "$cloud_backend" "/v1/auth/google" "Google auth backend route"
require_fixed "$cloud_backend" "/v1/me/phrases" "user phrase sync backend route"
require_fixed "$phrases" "AccountGateDialog(" "first custom phrase account gate"
require_fixed "$phrases" "If you skip, they stay only on this tablet" "custom phrase skip warning"
require_absent_fixed "$phrases" "signInWithGooglePlaceholder" "placeholder Google account flow"
require_fixed "$settings" "Account & custom phrases" "top settings account card"
require_fixed "$settings" "Sign in with Google" "real settings Google sign-in button"
require_fixed "$settings" "SettingsGroupHeader(" "grouped settings sections"
require_fixed "$gemini" "const val SENTENCE_PROMPT_VERSION = 5" "cleaned R2 sentence bundle version"
require_fixed "$gemini" "const val VERB_WIPE_VERSION = 4" "verb cache wipe for cleaned sentence bundles"
require_fixed "$phrase_audit" "writeCleanR2Tree" "repeatable phrase library cleanup script"

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
