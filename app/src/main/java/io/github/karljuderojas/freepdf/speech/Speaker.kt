package io.github.karljuderojas.freepdf.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Something that speaks one piece of text at a time and says when it is done. */
interface Speaker {

    interface Listener {
        fun onDone(id: String)
        fun onError(id: String?)

        /** Something else took the sound (a call, another player), so speaking stopped. */
        fun onInterrupted() = Unit
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
 * While it speaks it holds transient audio focus, so music ducks under the voice and a call
 * interrupts it. [speechRate] is asked for each sentence, so a change in Settings applies at once.
 */
class AndroidSpeaker(
    private val context: Context,
    private val speechRate: () -> Float = { 1f },
) : Speaker {

    override var listener: Speaker.Listener? = null

    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: Pair<String, String>? = null

    private val audioManager by lazy { context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focus: AudioFocusRequest? = null

    private fun create() {
        // When no engine is installed the init callback fires inside the constructor, before the
        // engine is assigned, so its status is held until the constructor returns.
        var created: TextToSpeech? = null
        var early: Int? = null
        created = TextToSpeech(context.applicationContext) { status ->
            val tts = created
            if (tts == null) early = status else initialised(tts, status)
        }
        engine = created
        val made = created ?: return
        early?.let { initialised(made, it) }
    }

    private fun initialised(tts: TextToSpeech, status: Int) {
        if (tts !== engine) return // shut down while starting
        if (status == TextToSpeech.SUCCESS && setUpLanguage(tts)) {
            tts.setAudioAttributes(ATTRIBUTES)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
            ready = true
            waiting?.let { (text, id) -> speak(text, id) }
            waiting = null
        } else {
            // Let go of the broken engine so the next Read aloud tries again and reports again,
            // instead of waiting forever for an engine that never comes.
            val id = waiting?.second
            waiting = null
            engine = null
            ready = false
            runCatching { tts.shutdown() }
            listener?.onError(id)
        }
    }

    /** The phone's language, or English if the engine has no voice for it; false if it has neither. */
    private fun setUpLanguage(tts: TextToSpeech): Boolean {
        val wanted = Locale.getDefault()
        if (tts.setLanguage(wanted) >= TextToSpeech.LANG_AVAILABLE) return true
        return tts.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(text: String, id: String) {
        if (!ready) {
            waiting = text to id
            if (engine == null) create()
            return
        }
        val tts = engine ?: return
        requestFocus()
        tts.setSpeechRate(speechRate())
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) listener?.onError(id)
    }

    override fun stop() {
        waiting = null
        if (ready) engine?.stop()
        abandonFocus()
    }

    override fun shutdown() {
        waiting = null
        abandonFocus()
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    // Stop the voice now; the reader shows Play so it can go on afterwards.
                    if (ready) engine?.stop()
                    focus = null
                    listener?.onInterrupted()
                }
            }
            .build()
        focus = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private fun abandonFocus() {
        val request = focus ?: return
        focus = null
        runCatching { audioManager.abandonAudioFocusRequest(request) }
    }

    private companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
