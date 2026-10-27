package com.vega.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Stand-in until sherpa-onnx: Android's recognizer, on-device first (offline), falling back to the
 * default recognizer. Many recognizers have no English (India) pack, so the language falls back
 * through [LANGUAGES]; whichever recognizer and language worked is kept for the next turn.
 */
class AndroidSpeechToText(private val context: Context) : SpeechToText {
    private val hasOnDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var onDevice = hasOnDevice
    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechToText.Listener? = null
    private var hotwords: List<String> = emptyList()
    private var language = 0
    private var retried = false
    private var packMissing = false

    override fun start(hotwords: List<String>, listener: SpeechToText.Listener) {
        this.listener = listener
        this.hotwords = hotwords
        retried = false
        // A pack may have been downloaded since the last failure, so start from the preferred setup again.
        if (packMissing) {
            language = 0
            if (hasOnDevice && !onDevice) {
                onDevice = true
                recognizer?.destroy()
                recognizer = null
            }
        }
        packMissing = false
        begin()
    }

    private fun begin() {
        val r = recognizer ?: create().also { recognizer = it }
        r.startListening(request())
    }

    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGES[language])
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // No EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS: on-device recognizers count it from the
        // start, so a short value closes the mic before the driver begins speaking.
        if (hotwords.isNotEmpty()) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(hotwords))
    }

    private fun create(): SpeechRecognizer =
        (if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context))
            .apply { setRecognitionListener(callbacks) }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        listener = null
        recognizer?.cancel()
    }

    override fun release() {
        listener = null
        recognizer?.destroy()
        recognizer = null
    }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            listener?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onEndOfSpeech() {
            listener?.onEndOfSpeech()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) listener?.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            val l = listener ?: return
            listener = null
            l.onFinal(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
        }

        override fun onError(error: Int) {
            if (error in NO_LANGUAGE_PACK && language < LANGUAGES.lastIndex) {
                language++
                begin()
                return
            }
            if (onDevice && !retried && error in ON_DEVICE_UNSUPPORTED) {
                retried = true
                onDevice = false
                language = 0
                recognizer?.destroy()
                recognizer = null
                begin()
                return
            }
            packMissing = error in NO_LANGUAGE_PACK
            val l = listener ?: return
            listener = null
            l.onError(
                noSpeech = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                message = describe(error),
            )
        }
    }

    private companion object {
        val LANGUAGES = listOf("en-IN", "en-GB", "en-US")

        val NO_LANGUAGE_PACK = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        )

        val ON_DEVICE_UNSUPPORTED = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
            SpeechRecognizer.ERROR_SERVER,
        )

        fun describe(error: Int) = when (error) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet for speech recognition."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Vega needs microphone permission."
            SpeechRecognizer.ERROR_AUDIO -> "Microphone problem."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "No English speech pack on this device. Download one in Settings → Speech."
            else -> "Speech recognition failed ($error)."
        }
    }
}
