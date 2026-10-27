package com.vega.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Every command gets a trace ID and per-stage timings (safety rule: every action is logged).
 * Pull the file with: adb pull /sdcard/Android/data/com.vega/files/vega-trace.log
 */
object TraceLog {
    data class Entry(val id: String, val text: String, val source: String, val stages: String, val totalMs: Long)

    private const val KEEP = 50
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private var file: File? = null
    private val writer = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        file = File(context.getExternalFilesDir(null) ?: context.filesDir, "vega-trace.log")
    }

    fun begin(text: String, source: String, startedAt: Long = SystemClock.elapsedRealtime()) =
        Trace(UUID.randomUUID().toString().take(6), text, source, startedAt)

    internal fun add(entry: Entry) {
        _entries.update { (listOf(entry) + it).take(KEEP) }
        val target = file ?: return
        val line = "${LocalDateTime.now()} ${entry.id} \"${entry.text}\" [${entry.source}] ${entry.stages} total=${entry.totalMs}ms\n"
        writer.execute { runCatching { target.appendText(line) } }
    }
}

class Trace internal constructor(
    val id: String,
    private val text: String,
    private val source: String,
    private val startedAt: Long,
) {
    private val stages = mutableListOf<String>()

    fun mark(stage: String, detail: String = "") {
        val ms = SystemClock.elapsedRealtime() - startedAt
        val entry = if (detail.isEmpty()) "$stage@${ms}ms" else "$stage@${ms}ms($detail)"
        stages += entry
        Log.d("Vega", "[$id] $entry")
    }

    fun finish() {
        TraceLog.add(TraceLog.Entry(id, text, source, stages.joinToString(" → "), SystemClock.elapsedRealtime() - startedAt))
    }
}
