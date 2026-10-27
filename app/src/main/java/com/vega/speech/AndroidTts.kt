package com.vega.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Stand-in until Piper voices: the system TextToSpeech engine in English (India). */
class AndroidTts(context: Context) : TextToSpeechEngine {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    private var configured = false
    private var gender = "female"

    init {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
        })
    }

    override suspend fun speak(text: String) {
        if (text.isBlank() || !ready.await()) return
        if (!configured) configure()
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine<Unit> { cont ->
            pending[id] = cont
            cont.invokeOnCancellation { if (pending.remove(id) != null) tts.stop() }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
        }
    }

    override fun stop() {
        tts.stop()
        pending.keys.toList().forEach(::finish)
    }

    override fun setVoice(gender: String) {
        this.gender = gender
        if (configured) applyVoice()
    }

    override fun release() {
        stop()
        tts.shutdown()
    }

    private fun configure() {
        configured = true
        val indian = Locale.Builder().setLanguage("en").setRegion("IN").build()
        if (tts.setLanguage(indian) < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.US)
        applyVoice()
    }

    // The system engine doesn't label voice gender, so this is a best effort. Piper has real male and female models.
    private fun applyVoice() {
        val voices = tts.voices.orEmpty()
            .filter { it.locale.language == "en" && it.locale.country == "IN" && !it.isNetworkConnectionRequired }
            .sortedBy { it.name }
        val pick = if (gender == "male") voices.lastOrNull() else voices.firstOrNull()
        if (pick != null) tts.setVoice(pick)
        tts.setPitch(if (gender == "male") 0.9f else 1.0f)
    }

    private fun finish(id: String?) {
        if (id != null) pending.remove(id)?.resume(Unit)
    }
}
