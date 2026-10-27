package com.vega.brain

import android.Manifest
import android.content.Context
import android.os.SystemClock
import com.vega.actions.ActionExecutor
import com.vega.actions.CarSimActions
import com.vega.actions.has
import com.vega.audio.AudioFocus
import com.vega.data.Trace
import com.vega.data.TraceLog
import com.vega.data.VegaSettings
import com.vega.llm.LlmEngine
import com.vega.llm.PatternLlm
import com.vega.llm.VegaLine
import com.vega.speech.AndroidSpeechToText
import com.vega.speech.AndroidTts
import com.vega.speech.SpeechToText
import com.vega.speech.TextToSpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** What the caption strip shows: the user's words (grey while partial) and Vega's reply. */
data class Caption(val user: String = "", val userFinal: Boolean = true, val vega: String = "")

/**
 * The assistant state machine: listen → decide → act → reply. One instance for the app's lifetime.
 * All public functions must be called on the main thread.
 */
class Assistant(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = VegaSettings(app)
    val car = CarSimActions()
    private val contacts = ContactResolver(app)
    private val dialog = DialogContext()
    private val llm: LlmEngine = PatternLlm() // replaced by llama.cpp + Qwen on day 8
    private val brain = Brain(llm)
    private val executor = ActionExecutor(app, settings, contacts, car)
    private val tts: TextToSpeechEngine = AndroidTts(app)
    private val focus = AudioFocus(app)
    private var stt: SpeechToText? = null

    @Volatile private var hotwords: List<String> = emptyList()
    private var listening = false
    private var turn: Job? = null
    private var flashJob: Job? = null

    private val _state = MutableStateFlow(VegaState.IDLE)
    val state: StateFlow<VegaState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _caption = MutableStateFlow(Caption())
    val caption: StateFlow<Caption> = _caption.asStateFlow()

    private val _draft = MutableStateFlow<MessageDraft?>(null)
    val draft: StateFlow<MessageDraft?> = _draft.asStateFlow()

    init {
        tts.setVoice(settings.voice)
        scope.launch { state.collect { if (it.holdsAudioFocus) focus.acquire() else focus.release() } }
        refreshHotwords()
    }

    /** A typed command (setup screen or adb) goes through exactly the same path as speech. */
    fun submitText(text: String) {
        if (text.isBlank()) return
        startTurn(text.trim(), TraceLog.begin(text.trim(), "typed"))
    }

    /** Orb tap or steering button: start listening, or finish listening if already on. Interrupts Vega's speech. */
    fun toggleListening() {
        if (listening) {
            stt?.stop()
            return
        }
        turn?.cancel()
        startListening(followUp = false)
    }

    fun greet() = sayAlone(Replies.greeting(settings.driverName, LocalTime.now().hour))

    fun goodbye() = sayAlone(Replies.goodbye(settings.driverName))

    fun setVoice(gender: String) {
        settings.voice = gender
        tts.setVoice(gender)
    }

    private fun startListening(followUp: Boolean) {
        if (!app.has(Manifest.permission.RECORD_AUDIO)) {
            showError(Replies.MIC_PERMISSION)
            return
        }
        flashJob?.cancel()
        tts.stop()
        val recognizer = stt ?: AndroidSpeechToText(app).also { stt = it }
        listening = true
        // Follow-ups keep CONFIRMING / CHOOSING on the orb so the user knows what Vega is waiting for.
        if (!followUp) _state.value = VegaState.LISTENING
        _caption.value = if (followUp) _caption.value.copy(user = "", userFinal = false) else Caption(userFinal = false)

        var speechEndAt = 0L
        recognizer.start(hotwords, object : SpeechToText.Listener {
            override fun onPartial(text: String) {
                _caption.value = Caption(user = text, userFinal = false)
            }

            override fun onLevel(level: Float) {
                _level.value = level
            }

            override fun onEndOfSpeech() {
                speechEndAt = SystemClock.elapsedRealtime()
            }

            override fun onFinal(text: String) {
                listening = false
                _level.value = 0f
                if (text.isBlank()) {
                    nothingHeard(followUp)
                    return
                }
                val trace = TraceLog.begin(text, "voice", if (speechEndAt > 0) speechEndAt else SystemClock.elapsedRealtime())
                trace.mark("stt")
                startTurn(text, trace)
            }

            override fun onError(noSpeech: Boolean, message: String) {
                listening = false
                _level.value = 0f
                if (noSpeech) nothingHeard(followUp) else showError(message)
            }
        })
        refreshHotwords()
    }

    private fun nothingHeard(followUp: Boolean) {
        // After a question, silence just closes the mic; the draft or choices stay open for the next tap.
        if (followUp) _state.value = VegaState.IDLE else showError(Replies.DIDNT_CATCH)
    }

    private fun startTurn(text: String, trace: Trace) {
        turn?.cancel()
        if (listening) {
            stt?.cancel()
            listening = false
        }
        tts.stop()
        flashJob?.cancel()
        turn = scope.launch { runTurn(text, trace) }
    }

    private suspend fun runTurn(text: String, trace: Trace) {
        _caption.value = Caption(user = text)
        _state.value = VegaState.THINKING
        dialog.driverName = settings.driverName
        dialog.privacy = settings.privacy

        val decision = withContext(Dispatchers.Default) { brain.decide(text, dialog) }
        trace.mark("decide", "${decision.source.name.lowercase()} ${decision.raw}")

        // A new request closes any old list of choices, unless this *is* the choice.
        if (decision.lines.none { it is VegaLine.Tool && it.name.endsWith(".select") }) {
            dialog.choosing = null
            dialog.pendingContact = null
        }

        val speech = mutableListOf<String>()
        val toasts = mutableListOf<String>()
        var asked = false
        var failed = false
        for (line in decision.lines) {
            when (line) {
                is VegaLine.Tool -> {
                    val result = executor.execute(line, dialog)
                    trace.mark(line.name, if (result.ok) "ok" else "failed")
                    result.speech?.let(speech::add)
                    result.toast?.let(toasts::add)
                    asked = asked || result.followUp
                    failed = failed || !result.ok
                }
                is VegaLine.Say -> speech += line.text
                is VegaLine.Ask -> {
                    speech += line.text
                    asked = true
                }
                VegaLine.Escalate -> {
                    speech += Replies.CANT_YET
                    failed = true
                }
            }
        }
        _draft.value = dialog.draft

        val reply = speech.joinToString(" ")
        _caption.value = _caption.value.copy(vega = reply.ifEmpty { toasts.joinToString(" · ") })
        if (reply.isNotEmpty()) {
            _state.value = VegaState.SPEAKING
            tts.speak(reply)
            trace.mark("spoken")
        }
        trace.finish()

        when {
            asked -> {
                _state.value = when {
                    dialog.draft != null -> VegaState.CONFIRMING
                    dialog.choosing != null -> VegaState.CHOOSING
                    else -> VegaState.LISTENING
                }
                startListening(followUp = true)
            }
            failed -> showError(null)
            else -> flash(VegaState.DONE, SUCCESS_FLASH_MS)
        }
    }

    private fun sayAlone(text: String) {
        turn?.cancel()
        turn = scope.launch {
            _caption.value = Caption(vega = text)
            _state.value = VegaState.SPEAKING
            tts.speak(text)
            _state.value = VegaState.IDLE
        }
    }

    private fun showError(message: String?) {
        if (message != null) _caption.value = Caption(vega = message)
        flash(VegaState.ERROR, ERROR_FLASH_MS)
    }

    private fun flash(state: VegaState, ms: Long) {
        flashJob?.cancel()
        _state.value = state
        flashJob = scope.launch {
            delay(ms)
            if (_state.value == state) _state.value = VegaState.IDLE
        }
    }

    private fun refreshHotwords() {
        scope.launch(Dispatchers.IO) { hotwords = contacts.hotwords() }
    }

    private companion object {
        const val SUCCESS_FLASH_MS = 300L
        const val ERROR_FLASH_MS = 1200L
    }
}
