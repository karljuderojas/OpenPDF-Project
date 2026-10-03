package io.github.karljuderojas.freepdf.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Something that speaks one piece of text at a time and says when it is done. */
interface Speaker {

    interface Listener {
        fun onDone(id: String)
        fun onError(id: String?)
    }

    var listener: Listener?

    /** Speaks [text], dropping anything still being spoken. [id] comes back to the [listener]. */
    fun speak(text: String, id: String)

    fun stop()

    fun shutdown()
}

/**
 * Android's built-in text-to-speech engine, which works on the phone with no network. It starts
 * up the first time something is spoken; text asked for before it is ready is spoken once it is.
 */
class AndroidSpeaker(private val context: Context) : Speaker {

    override var listener: Speaker.Listener? = null

    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: Pair<String, String>? = null

    private fun create() {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.apply {
                    // The phone's language, or whichever the engine falls back to if it has none for it.
                    setLanguage(Locale.getDefault())
                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            utteranceId?.let { listener?.onDone(it) }
                        }

                        @Deprecated("Replaced by the version with an error code")
                        override fun onError(utteranceId: String?) {
                            listener?.onError(utteranceId)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            listener?.onError(utteranceId)
                        }
                    })
                }
                ready = true
                waiting?.let { (text, id) -> speak(text, id) }
                waiting = null
            } else {
                listener?.onError(waiting?.second)
            }
        }
    }

    override fun speak(text: String, id: String) {
        if (!ready) {
            waiting = text to id
            if (engine == null) create()
            return
        }
        if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) listener?.onError(id)
    }

    override fun stop() {
        waiting = null
        if (ready) engine?.stop()
    }

    override fun shutdown() {
        waiting = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
