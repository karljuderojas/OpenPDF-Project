package io.github.karljuderojas.freepdf.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {

    private class FakeSpeaker : Speaker {
        override var listener: Speaker.Listener? = null
        val spoken = ArrayList<String>()
        var lastId = ""
        var stops = 0

        override fun speak(text: String, id: String) {
            spoken += text
            lastId = id
        }

        override fun stop() {
            stops++
        }

        override fun shutdown() = Unit

        /** The engine finishing the sentence it was last given. */
        fun finish() = listener!!.onDone(lastId)
    }

    private val pages = listOf(
        listOf("One a.", "One b."),
        emptyList(),
        listOf("Three a."),
    )
    private val speaker = FakeSpeaker()
    private val reader = ReadAloud(speaker, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { pages.size }) { pages[it] }

    @Test
    fun readsASentenceAtATimeAndPassesOverEmptyPages() {
        reader.start(0)
        assertEquals(listOf("One a."), speaker.spoken)
        speaker.finish()
        speaker.finish()
        assertEquals(listOf("One a.", "One b.", "Three a."), speaker.spoken)
        assertEquals(2, reader.state.value.page)
        speaker.finish()
        assertFalse("Done at the end of the document", reader.state.value.active)
    }

    @Test
    fun startsAtThePageGivenOrTheNextOneWithText() {
        reader.start(1)
        assertEquals(listOf("Three a."), speaker.spoken)
        assertEquals(2, reader.state.value.page)
    }

    @Test
    fun pauseStopsAndResumeSaysTheSentenceAgain() {
        reader.start(0)
        reader.pause()
        assertFalse(reader.state.value.speaking)
        assertTrue(reader.state.value.active)
        reader.resume()
        assertEquals(listOf("One a.", "One a."), speaker.spoken)
        assertTrue(reader.state.value.speaking)
    }

    @Test
    fun aSentenceFinishingAfterAPauseIsIgnored() {
        reader.start(0)
        val stale = speaker.lastId
        reader.pause()
        speaker.listener!!.onDone(stale)
        assertEquals(listOf("One a."), speaker.spoken)
        assertEquals(0, reader.state.value.sentence)
    }

    @Test
    fun nextAndPreviousMoveBySentenceAcrossPages() {
        reader.start(0)
        reader.next()
        assertEquals("One b.", reader.state.value.text)
        reader.next()
        assertEquals("Three a.", reader.state.value.text)
        reader.previous()
        assertEquals("One b.", reader.state.value.text)
        assertEquals(0, reader.state.value.page)
    }

    @Test
    fun previousAtTheStartReadsTheFirstSentenceAgain() {
        reader.start(0)
        reader.previous()
        assertEquals(listOf("One a.", "One a."), speaker.spoken)
        assertEquals(0, reader.state.value.sentence)
    }

    @Test
    fun movingWhilePausedStaysPaused() {
        reader.start(0)
        reader.pause()
        reader.next()
        assertEquals("One b.", reader.state.value.text)
        assertEquals(listOf("One a."), speaker.spoken)
        assertFalse(reader.state.value.speaking)
    }

    @Test
    fun stopEndsReading() {
        reader.start(0)
        reader.stop()
        assertFalse(reader.state.value.active)
        assertFalse(reader.state.value.speaking)
    }

    @Test
    fun anEngineErrorStopsAndSaysUnavailable() {
        reader.start(0)
        speaker.listener!!.onError(speaker.lastId)
        assertFalse(reader.state.value.active)
        assertTrue(reader.state.value.unavailable)
        reader.start(0)
        assertFalse(reader.state.value.unavailable)
    }

    @Test
    fun aDocumentWithNoTextReadsNothing() {
        val silent = ReadAloud(speaker, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { 2 }) { emptyList() }
        silent.start(0)
        assertTrue(speaker.spoken.isEmpty())
        assertFalse(silent.state.value.active)
    }
}
