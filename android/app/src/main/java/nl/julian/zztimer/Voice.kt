package nl.julian.zztimer

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Nederlandse spraak via de spraakengine van de telefoon. */
class Voice(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private val counter = AtomicLong()

    var ready by mutableStateOf(false)
        private set
    var dutchAvailable by mutableStateOf(true)
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            dutchAvailable = false
            return
        }
        val result = tts.setLanguage(Locale.forLanguageTag("nl-NL"))
        dutchAvailable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setSpeechRate(1.05f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
    }

    /** Onderbreekt een lopende zin, zodat het aftellen nooit achterloopt. */
    fun say(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zz-${counter.incrementAndGet()}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
