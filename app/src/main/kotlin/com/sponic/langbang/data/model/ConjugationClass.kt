package com.sponic.langbang.data.model

/**
 * Traditional Polish present-tense conjugation classes, identified from the (1sg, 2sg)
 * ending pair. Used to group verbs that conjugate the same way so the learner sees
 * patterns instead of memorising 11 isolated paradigms.
 */
enum class ConjugationClass(val label: String, val description: String) {
    CLASS_I("Class I (-ę / -esz)",  "Stem ends in a soft consonant; alternation common"),
    CLASS_II("Class II (-ę / -isz)", "Soft-stem -ić / -eć verbs"),
    CLASS_III("Class III (-am / -asz)", "-ać verbs with regular -am, -asz, -a, -amy, -acie, -ają"),
    CLASS_IV("Class IV (-em / -esz)", "Old athematic verbs: jeść, wiedzieć, dać"),
    IRREGULAR("Irregular", "Doesn't fit a standard pattern (być, etc.)")
}

fun VerbEntry.conjugationClass(): ConjugationClass {
    val sg1 = forms["1sg"].orEmpty()
    val sg2 = forms["2sg"].orEmpty()
    return when {
        sg1.endsWith("am") && sg2.endsWith("asz") -> ConjugationClass.CLASS_III
        sg1.endsWith("em") && sg2.endsWith("esz") -> ConjugationClass.CLASS_IV
        sg1.endsWith("ę")  && sg2.endsWith("isz") -> ConjugationClass.CLASS_II
        sg1.endsWith("ę")  && sg2.endsWith("ysz") -> ConjugationClass.CLASS_II
        sg1.endsWith("ę")  && sg2.endsWith("esz") -> ConjugationClass.CLASS_I
        else -> ConjugationClass.IRREGULAR
    }
}
