package com.sponic.langbang.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object GrammarVisuals {
    object Gender {
        val Masculine = Color(0xFF1565C0)
        val Feminine = Color(0xFFC2185B)
        val Neuter = Color(0xFF4B5563)
        val Fallback = Color(0xFF155E63)

        fun color(gender: String): Color = when (gender.lowercase()) {
            "m", "masculine", "masc." -> Masculine
            "f", "feminine", "fem." -> Feminine
            "n", "neuter", "neut." -> Neuter
            else -> Fallback
        }

        fun abbrev(gender: String): String = when (gender.lowercase()) {
            "m", "masculine" -> "masc."
            "f", "feminine" -> "fem."
            "n", "neuter" -> "neut."
            else -> gender
        }
    }

    object Case {
        val Nominative = Color(0xFF0B7A3B)
        val Accusative = Color(0xFFC75A00)
        val Genitive = Color(0xFF6D28D9)
        val Fallback = Color(0xFF8EA19A)

        fun color(caseKey: String): Color = when (caseKey.lowercase()) {
            "nom", "nominative" -> Nominative
            "acc", "accusative" -> Accusative
            "gen", "genitive" -> Genitive
            else -> Fallback
        }
    }

    object NounForm {
        val OutlineBacking = Color(0xFF111111)

        const val NowVoicingOutlineWidth = 14f
        const val NowVoicingBackingExtraWidth = 3f
        val NowVoicingGlyphGap = 10.dp

        const val RowOutlineWidth = 5f
        const val RowBackingExtraWidth = 2f
        val RowGlyphGap = 2.dp

        const val LegendOutlineWidth = 5f
        const val LegendBackingExtraWidth = 1.5f
        val LegendGlyphGap = 2.dp
    }

    object NowVoicingPanel {
        val Background = Color(0xFFF2F4F3)
        val Border = Color(0xFF111111)
        val BorderWidth = 2.dp
        val FilterLabel = Color(0xFF6B4E00)
    }

    object Selector {
        val CaseBand = Color(0xFFFFF1D6)
        val NumberBand = Color(0xFFE3F1F0)
    }
}
