package io.github.karljuderojas.freepdf.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where reading aloud is: [page] (zero-based) and [sentence] of that page, with its [text]. */
data class ReadAloudState(
    val active: Boolean = false,
    val speaking: Boolean = false,
    val page: Int = 0,
    val sentence: Int = 0,
    val text: String = "",
    /** The speech engine failed or is not set up; shown once, then cleared by [ReadAloud.acknowledgeUnavailable]. */
    val unavailable: Boolean = false,
)

/**
 * Reads a document aloud from a page onward, a sentence at a time, so the reader can pause, skip
 * to the next or previous sentence, and see which one is being spoken. Pages are loaded as the
 * reading reaches them, and pages with no text are passed over. [pageCount] says how many pages
 * there are now; [loadSentences] gives the sentences of a page.
 *
 * Text-to-speech engines cannot pause, so pausing stops speaking and resuming says the current
 * sentence again from its start.
 */
class ReadAloud(
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val pageCount: () -> Int,
    private val loadSentences: suspend (page: Int) -> List<String>,
) : Speaker.Listener {

    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    private var sentences: List<String> = emptyList()

    // Each utterance gets the generation it was asked for in, so the word that one is done or
    // failed after the reader has moved on is ignored.
    private var generation = 0
    private var job: Job? = null

    init {
        speaker.listener = this
    }

    /** Starts reading at the first page from [page] on that has text. */
    fun start(page: Int) {
        halt()
        _state.value = ReadAloudState(active = true, speaking = true, page = page)
        go(page, 0, onNothing = ::finish)
    }

    fun pause() {
        if (!_state.value.active || !_state.value.speaking) return
        halt()
        _state.value = _state.value.copy(speaking = false)
    }

    fun resume() {
        val now = _state.value
        if (!now.active || now.speaking) return
        _state.value = now.copy(speaking = true)
        // Paused while the page was still loading: nothing is loaded yet, so load it again.
        if (sentences.isEmpty()) go(now.page, now.sentence, onNothing = ::finish) else speakCurrent()
    }

    fun next() {
        val now = _state.value
        if (!now.active) return
        halt()
        go(now.page, now.sentence + 1, onNothing = ::finish)
    }

    fun previous() {
        val now = _state.value
        if (!now.active) return
        halt()
        // Before the very first sentence there is nothing earlier, so it starts again.
        go(now.page, now.sentence - 1, onNothing = { go(now.page, now.sentence, onNothing = ::finish) })
    }

    fun stop() {
        halt()
        _state.value = ReadAloudState()
    }

    fun shutdown() {
        stop()
        speaker.shutdown()
    }

    /** The "not available" message has been shown, so it is not shown again after a rotation. */
    fun acknowledgeUnavailable() {
        if (_state.value.unavailable) _state.value = _state.value.copy(unavailable = false)
    }

    override fun onDone(id: String) {
        scope.launch { if (id == generation.toString() && _state.value.speaking) next() }
    }

    override fun onError(id: String?) {
        scope.launch {
            if (id == null || id == generation.toString()) {
                stop()
                _state.value = ReadAloudState(unavailable = true)
            }
        }
    }

    override fun onInterrupted() {
        scope.launch { pause() }
    }

    private fun halt() {
        generation++
        job?.cancel()
        speaker.stop()
    }

    private fun finish() {
        _state.value = ReadAloudState()
    }

    /** Moves to the sentence asked for, on later or earlier pages if it is past the end or before the start. */
    private fun go(page: Int, sentence: Int, onNothing: () -> Unit) {
        job = scope.launch {
            if (moveTo(page, sentence)) {
                if (_state.value.speaking) speakCurrent()
            } else {
                onNothing()
            }
        }
    }

    private suspend fun moveTo(page: Int, sentence: Int): Boolean {
        if (sentence >= 0) {
            var p = page
            var i = sentence
            while (p in 0 until pageCount()) {
                val found = loadSentences(p)
                if (i < found.size) return arrive(p, i, found)
                p++
                i = 0
            }
        } else {
            var p = page - 1
            while (p >= 0) {
                val found = loadSentences(p)
                if (found.isNotEmpty()) return arrive(p, found.lastIndex, found)
                p--
            }
        }
        return false
    }

    private fun arrive(page: Int, sentence: Int, found: List<String>): Boolean {
        sentences = found
        _state.value = _state.value.copy(page = page, sentence = sentence, text = found[sentence])
        return true
    }

    private fun speakCurrent() {
        val now = _state.value
        val text = sentences.getOrNull(now.sentence) ?: return
        generation++
        speaker.speak(text, generation.toString())
    }
}
