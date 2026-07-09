package com.sponic.langbangtrans.hud

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies transcript merging handles Gemini's cumulative updates without repeating. */
class TextLayoutTest {

    @Test
    fun cumulativeTranscriptReplacesInsteadOfRepeating() {
        val state = HudTextState()
        state.apply("Czy", null)
        state.apply("Czy chcesz", null)
        state.apply("Czy chcesz spróbować", null)
        state.apply("Czy chcesz spróbować masażu", null)
        assertEquals("Czy chcesz spróbować masażu", state.sourceText)
    }

    @Test
    fun genuineNewFragmentsAppend() {
        val state = HudTextState()
        state.apply("Hello", null)
        state.apply("world", null)
        assertEquals("Hello world", state.sourceText)
    }

    @Test
    fun exactDuplicateIsIgnored() {
        val state = HudTextState()
        state.apply("powtórzenie", null)
        state.apply("powtórzenie", null)
        state.apply("powtórzenie", null)
        assertEquals("powtórzenie", state.sourceText)
    }

    @Test
    fun inputAndOutputTrackedSeparately() {
        // Both sides grow cumulatively as the model hears/translates more of the sentence.
        val state = HudTextState()
        state.apply("Czy", "Do")
        state.apply("Czy chcesz", "Do you want")
        assertEquals("Czy chcesz", state.sourceText)
        assertEquals("Do you want", state.targetText)
    }
}
