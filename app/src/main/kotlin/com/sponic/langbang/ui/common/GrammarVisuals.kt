package com.sponic.langbang.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sponic.langbang.data.model.TokenPair

object GrammarVisuals {
    object Gender {
        val Masculine = Color(0xFF2F6FE6)
        val Feminine = Color(0xFFB8366E)
        val Neuter = Color(0xFF0C9676)
        val Fallback = Color(0xFF2F6FE6)

        fun color(gender: String): Color = when (gender.lowercase()) {
            "m", "mp", "masculine", "masc.", "virile" -> Masculine
            "f", "feminine", "fem." -> Feminine
            "n", "other", "neuter", "neut.", "non-virile" -> Neuter
            else -> Fallback
        }

        fun abbrev(gender: String): String = when (gender.lowercase()) {
            "m", "masculine" -> "masc."
            "f", "feminine" -> "fem."
            "n", "neuter" -> "neut."
            else -> gender
        }
    }

    object Variable {
        val CaseOrGender = Color(0xFF2F6FE6)
        val Conjugation = Color(0xFF0FAE89)

        fun color(token: TokenPair): Color? = when {
            token.gender != null -> Gender.color(token.gender)
            token.variableKind.equals("conjugation", ignoreCase = true) -> Conjugation
            token.variableStart != null || token.caseKey != null -> CaseOrGender
            else -> null
        }
    }

    object NowVoicingPanel {
        val Background = Color(0xFFFFFFFF)
        val Border = Color(0xFFE1E7EB)
        val BorderWidth = 1.dp
        val FilterLabel = Color(0xFF7D8994)
    }

    /**
     * Two slightly different neutral greys painted behind alternating syllables of a
     * target Polish word so the learner can see where one syllable ends and the next
     * begins. Kept subtle on the white Now Voicing surface — just enough contrast to
     * read the chunk boundaries without competing with the text itself.
     */
    object SyllableShading {
        val ShadeA = Color(0xFFEFF1F4)
        val ShadeB = Color(0xFFDFE4EA)
    }

    object Selector {
        val CaseBand = Color(0xFFE9F1FF)
        val NumberBand = Color(0xFFE4F8F3)
    }
}
