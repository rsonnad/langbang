package com.sponic.langbang.data

import android.content.Context

/**
 * Sticky checkbox state for which pronoun/person forms should be allowed when generating
 * sentence examples. Stored in SharedPreferences as one boolean per person key so the
 * filter survives app launches. Default for any unseen key is `true` (everything included).
 */
class PronounFilterStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pronoun-filter", Context.MODE_PRIVATE)

    fun isIncluded(personKey: String): Boolean =
        prefs.getBoolean("incl-$personKey", true)

    fun setIncluded(personKey: String, included: Boolean) {
        prefs.edit().putBoolean("incl-$personKey", included).apply()
    }

    fun allIncluded(allKeys: List<String>): Set<String> =
        allKeys.filter { isIncluded(it) }.toSet()
}
