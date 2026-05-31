package com.sponic.langbang.data

import android.content.Context

/**
 * Sticky checkbox state for which (tense, person) combinations should be allowed when
 * generating sentence examples. Stored in SharedPreferences as one boolean per
 * (tense, personKey) so the filter survives app launches. Default for any unseen key is
 * `true` (everything included).
 *
 * Back-compat: pre-tense builds stored keys as `"incl-$personKey"`. Those continue to be
 * read as present-tense entries.
 */
class PronounFilterStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pronoun-filter", Context.MODE_PRIVATE)

    fun isIncluded(personKey: String, tense: String = TENSE_PRESENT): Boolean {
        val composite = "incl-$tense-$personKey"
        if (prefs.contains(composite)) return prefs.getBoolean(composite, true)
        // Back-compat: legacy bare "incl-$personKey" entries → present-tense default.
        if (tense == TENSE_PRESENT) {
            return prefs.getBoolean("incl-$personKey", true)
        }
        return true
    }

    fun setIncluded(personKey: String, included: Boolean, tense: String = TENSE_PRESENT) {
        prefs.edit().putBoolean("incl-$tense-$personKey", included).apply()
    }

    fun allIncluded(allKeys: List<String>, tense: String = TENSE_PRESENT): Set<String> =
        allKeys.filter { isIncluded(it, tense) }.toSet()

    companion object {
        const val TENSE_PRESENT = "present"
        const val TENSE_PAST = "past"
    }
}
