package com.vega.speech

/** Implemented by Android's recognizer now and by sherpa-onnx streaming Zipformer later. Main thread only. */
interface SpeechToText {
    fun start(hotwords: List<String>, listener: Listener)

    /** Stop recording and deliver the final text. */
    fun stop()

    /** Stop and drop the result. */
    fun cancel()

    fun release()

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onLevel(level: Float)
        fun onEndOfSpeech()
        fun onError(noSpeech: Boolean, message: String)
    }
}

/** Implemented by Android TextToSpeech now and by Piper (sherpa-onnx) later. */
interface TextToSpeechEngine {
    /** Suspends until the text has been spoken or [stop] is called. */
    suspend fun speak(text: String)
    fun stop()
    fun setVoice(gender: String)
    fun release()
}
