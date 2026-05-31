package com.sponic.langbang.ui.quizzes

/**
 * Single multiple-choice question. [prompt] is the cue the learner sees ("to be —
 * he/she/it"); [correct] is the answer that scores a point; [distractors] are 3
 * plausibly-wrong options sampled from the same pool (other forms of the same
 * verb, other adjectives in the lesson, etc) so the user can't pick by
 * elimination. The engine in [MultipleChoiceQuiz] shuffles correct + distractors
 * into a 4-option grid each render.
 *
 * Keep distractor count fixed at 3 — the grid is sized for 4 options across all
 * quiz types so a learner builds the same scanning habit.
 */
data class QuizQuestion(
    val prompt: String,
    val correct: String,
    val distractors: List<String>,
    /** Optional small caption shown above the prompt, e.g. "Adjective · nominative". */
    val context: String = ""
)
