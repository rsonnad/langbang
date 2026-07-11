package com.sponic.langbang.shared.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Table-driven / behavioral tests over the shared FeatureModel.
 * Proves correctness once in commonTest — both platforms inherit it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testItems(): List<PracticeItem> = listOf(
        PracticeItem("a", PracticeKind.VERB_FORM, "I have", "mam", "I have", "mieć 1sg", targetForm = "mam"),
        PracticeItem("b", PracticeKind.VERB_FORM, "he has", "ma", "he has", "mieć 3sg", targetForm = "ma"),
        PracticeItem("c", PracticeKind.VERB_FORM, "I do", "robię", "I do", "robić 1sg", targetForm = "robię"),
    )

    private class RecordingAudioPlayer : AudioPlayer {
        val plays = mutableListOf<String>()
        var stopped = 0
        override fun play(audioKey: String, onDone: () -> Unit) {
            plays += audioKey
            // Immediately invoke to unblock test (sync for test)
            onDone()
        }
        override fun stop() { stopped++ }
        override fun isPlaying(): Boolean = false
    }

    @Test
    fun start_withItems_setsFirstItem_notRevealed() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        val s = model.state.value
        assertNotNull(s.current)
        assertEquals("I have", s.current!!.prompt)
        assertFalse(s.isRevealed)
        assertEquals(0, s.index)
        assertEquals(3, s.total)
        assertFalse(s.isFinished)
    }

    @Test
    fun reveal_setsRevealed_and_emitsPlayIntent_viaPlayer() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        model.onEvent(PracticeModel.Event.Reveal)
        val s = model.state.value
        assertTrue(s.isRevealed)
        assertTrue(player.plays.isNotEmpty())
        assertEquals("mam", player.plays.last())
    }

    @Test
    fun next_advances_and_resetsReveal() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        model.onEvent(PracticeModel.Event.Next)
        val s = model.state.value
        assertEquals(1, s.index)
        assertNotNull(s.current)
        assertEquals("he has", s.current!!.prompt)
        assertFalse(s.isRevealed)
    }

    @Test
    fun next_fromLast_wraps_toFirst() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        model.onEvent(PracticeModel.Event.Next)
        model.onEvent(PracticeModel.Event.Next)
        model.onEvent(PracticeModel.Event.Next) // should wrap
        val s = model.state.value
        assertEquals(0, s.index)
        assertEquals("I have", s.current!!.prompt)
    }

    @Test
    fun emptySet_finishesImmediately() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, emptyList())
        val s = model.state.value
        assertNull(s.current)
        assertTrue(s.isFinished)
        assertEquals(0, s.total)
    }

    @Test
    fun restart_resets_toStart_unrevealed() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        model.onEvent(PracticeModel.Event.Next)
        model.onEvent(PracticeModel.Event.Reveal)
        model.onEvent(PracticeModel.Event.Restart)
        val s = model.state.value
        assertEquals(0, s.index)
        assertFalse(s.isRevealed)
        assertEquals("I have", s.current!!.prompt)
    }

    @Test
    fun playAudioEvent_triggersPlayer_forCurrent() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        model.onEvent(PracticeModel.Event.PlayAudio)
        assertEquals(1, player.plays.size)
        assertEquals("mam", player.plays[0])
    }

    @Test
    fun previous_fromFirst_wraps_toLast() = runTest {
        val player = RecordingAudioPlayer()
        val model = PracticeModel(player, testItems())
        model.onEvent(PracticeModel.Event.Previous)
        val s = model.state.value
        assertEquals(2, s.index)
        assertEquals("I do", s.current!!.prompt)
    }
}