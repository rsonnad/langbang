package com.sponic.langbangtrans.hud

/**
 * Rough Russian Cyrillic → Latin transliteration for an at-a-glance English pronunciation guide.
 * Not a strict standard (ignores stress/vowel-reduction), but readable — e.g.
 * "Вы хотите попробовать?" → "Vy khotite poprobovat?".
 */
private val CYRILLIC_MAP: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
    'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
    'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
    'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
    'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)

fun transliterateCyrillic(text: String): String = buildString {
    for (ch in text) {
        val mapped = CYRILLIC_MAP[ch.lowercaseChar()]
        when {
            mapped == null -> append(ch) // spaces, punctuation, digits, already-Latin
            ch.isUpperCase() -> append(mapped.replaceFirstChar { it.uppercaseChar() })
            else -> append(mapped)
        }
    }
}
