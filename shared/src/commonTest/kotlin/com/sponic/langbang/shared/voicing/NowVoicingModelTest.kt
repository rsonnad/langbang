package com.sponic.langbang.shared.voicing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NowVoicingModelTest {
    @Test
    fun defaultSequenceIsEnglishSlowPolishThenPolish() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())

        assertEquals("EN", model.state.value.statusDetail)
        complete(model)
        assertEquals("PL (slow)", model.state.value.statusDetail)
        complete(model)
        assertEquals("PL", model.state.value.statusDetail)
        complete(model)

        assertEquals("idle", model.state.value.statusDetail)
        assertNull(model.state.value.audioRequest)
        assertTrue(model.state.value.isVisible)
    }

    @Test
    fun optionsChangeTheNextSequence() {
        val model = NowVoicingModel()
        model.toggleEnglish()
        model.toggleSlow()
        model.start(NowVoicingSamples.greeting())

        assertEquals("PL", model.state.value.statusDetail)
        complete(model)
        assertNull(model.state.value.audioRequest)
    }

    @Test
    fun loopIssuesANewRequestInsteadOfGoingIdle() {
        val model = NowVoicingModel()
        model.toggleEnglish()
        model.toggleSlow()
        model.toggleLoop()
        model.start(NowVoicingSamples.greeting())
        val firstId = model.state.value.audioRequest!!.id

        complete(model)

        assertEquals("PL", model.state.value.statusDetail)
        assertTrue(model.state.value.audioRequest!!.id > firstId)
    }

    @Test
    fun pauseResumeKeepsTheSameRequest() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        val request = model.state.value.audioRequest

        model.togglePause()
        assertTrue(model.state.value.isPaused)
        assertEquals(request, model.state.value.audioRequest)
        model.togglePause()
        assertFalse(model.state.value.isPaused)
        assertEquals(request, model.state.value.audioRequest)
    }

    @Test
    fun stopClearsPlaybackButPreservesOptions() {
        val model = NowVoicingModel()
        model.toggleLoop()
        model.start(NowVoicingSamples.greeting())

        model.stop()

        assertFalse(model.state.value.isVisible)
        assertNull(model.state.value.audioRequest)
        assertTrue(model.state.value.loop)
    }

    @Test
    fun staleCompletionCannotAdvanceANewerRequest() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        val oldId = model.state.value.audioRequest!!.id
        model.replay()
        val currentId = model.state.value.audioRequest!!.id

        model.audioCompleted(oldId)

        assertEquals(currentId, model.state.value.audioRequest!!.id)
        assertEquals("EN", model.state.value.statusDetail)
    }

    @Test
    fun queueNavigationPublishesTheSelectedPhrase() {
        val model = NowVoicingModel()
        model.startQueue(
            listOf(NowVoicingSamples.greeting(), NowVoicingSamples.appreciate()),
        )

        assertFalse(model.state.value.canGoPrevious)
        assertTrue(model.state.value.canGoNext)
        model.next()
        assertEquals("appreciate", model.state.value.phrase!!.id)
        assertTrue(model.state.value.canGoPrevious)
        assertFalse(model.state.value.canGoNext)
        model.previous()
        assertEquals("greeting", model.state.value.phrase!!.id)
    }

    @Test
    fun optionChangesDuringSpeechApplyToTheNextPlayback() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        model.toggleSlow()

        complete(model)

        assertEquals("PL (slow)", model.state.value.statusDetail)
        model.replay()
        assertEquals("EN", model.state.value.statusDetail)
        complete(model)
        assertEquals("PL", model.state.value.statusDetail)
    }

    @Test
    fun starsFollowPhrasesAcrossQueueNavigation() {
        val model = NowVoicingModel()
        model.startQueue(listOf(NowVoicingSamples.greeting(), NowVoicingSamples.appreciate()))
        model.toggleStar()
        model.next()
        assertFalse(model.state.value.isStarred)
        model.previous()
        assertTrue(model.state.value.isStarred)
    }

    @Test
    fun wordPlaybackDoesNotAdvanceThePhraseSequence() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        model.speakWord(1)
        val wordRequest = model.state.value.audioRequest!!

        assertEquals("dobry", wordRequest.text)
        assertFalse(wordRequest.advancesSequence)
        model.audioCompleted(wordRequest.id)
        assertEquals("idle", model.state.value.statusDetail)
        assertTrue(model.state.value.isVisible)
        model.replay()
        assertEquals("EN", model.state.value.statusDetail)
        assertTrue(model.state.value.audioRequest!!.advancesSequence)
    }

    @Test
    fun completionWhilePausedDoesNotAdvance() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        val request = model.state.value.audioRequest!!
        model.togglePause()

        model.audioCompleted(request.id)

        assertTrue(model.state.value.isPaused)
        assertEquals(request, model.state.value.audioRequest)
        assertEquals("PAUSED", model.state.value.statusDetail)
    }

    @Test
    fun emptyQueueStopsAndOutOfRangeIndexIsCoerced() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        model.startQueue(emptyList())
        assertFalse(model.state.value.isVisible)

        model.startQueue(
            listOf(NowVoicingSamples.greeting(), NowVoicingSamples.appreciate()),
            startIndex = 99,
        )
        assertEquals("appreciate", model.state.value.phrase!!.id)
        assertEquals(1, model.state.value.queueIndex)
    }

    @Test
    fun duplicateCompletionAndOutOfRangeWordAreNoOps() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        val first = model.state.value.audioRequest!!.id
        model.audioCompleted(first)
        val second = model.state.value.audioRequest
        model.audioCompleted(first)
        model.speakWord(99)
        assertEquals(second, model.state.value.audioRequest)
    }

    @Test
    fun transportAtQueueEdgesIsANoOp() {
        val model = NowVoicingModel()
        model.startQueue(listOf(NowVoicingSamples.greeting(), NowVoicingSamples.appreciate()))
        val first = model.state.value.audioRequest
        model.previous()
        assertEquals(first, model.state.value.audioRequest)
        model.next()
        val last = model.state.value.audioRequest
        model.next()
        assertEquals(last, model.state.value.audioRequest)
    }

    @Test
    fun requestsExposeExpectedLanguagesRatesAndAdvancePolicy() {
        val model = NowVoicingModel()
        model.start(NowVoicingSamples.greeting())
        val english = model.state.value.audioRequest!!
        assertEquals("en-US", english.languageTag)
        assertTrue(english.advancesSequence)
        complete(model)
        val slow = model.state.value.audioRequest!!
        assertEquals("pl-PL", slow.languageTag)
        complete(model)
        val normal = model.state.value.audioRequest!!
        assertTrue(slow.rate < normal.rate)
    }

    private fun complete(model: NowVoicingModel) {
        model.audioCompleted(model.state.value.audioRequest!!.id)
    }
}
