package com.sponic.langbang.shared.practice

/**
 * Tiny in-memory lesson dataset lifted faithfully from LangBangML lesson-02.json (core verbs).
 * Used for the skeleton slice so we have real items without full LessonRepository or assets/network.
 */
object SimplePracticeData {
    data class Verb(val lemma: String, val en: String, val forms: Map<String, String>)

    val verbs: List<Verb> = listOf(
        Verb("być", "to be", mapOf("1sg" to "jestem", "2sg" to "jesteś", "3sg" to "jest", "1pl" to "jesteśmy", "2pl" to "jesteście", "3pl" to "są")),
        Verb("mieć", "to have", mapOf("1sg" to "mam", "2sg" to "masz", "3sg" to "ma", "1pl" to "mamy", "2pl" to "macie", "3pl" to "mają")),
        Verb("iść", "to go", mapOf("1sg" to "idę", "2sg" to "idziesz", "3sg" to "idzie", "1pl" to "idziemy", "2pl" to "idziecie", "3pl" to "idą")),
        Verb("robić", "to do", mapOf("1sg" to "robię", "2sg" to "robisz", "3sg" to "robi", "1pl" to "robimy", "2pl" to "robicie", "3pl" to "robią")),
    )

    val personKeys = listOf("1sg", "3sg") // minimal slice: I / he for demo
}