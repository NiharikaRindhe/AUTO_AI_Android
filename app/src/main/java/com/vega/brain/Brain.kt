package com.vega.brain

import com.vega.llm.CommandParser
import com.vega.llm.LlmEngine
import com.vega.llm.SimpleRules
import com.vega.llm.VegaLine
import java.time.LocalTime

/** Simple rules first, then the LLM (design rule 1). Pure Kotlin so it runs in unit tests. */
class Brain(private val llm: LlmEngine) {

    enum class Source { RULES, LLM }

    data class Decision(val lines: List<VegaLine>, val source: Source, val raw: String, val ms: Long)

    suspend fun decide(text: String, ctx: DialogContext, now: LocalTime = LocalTime.now()): Decision {
        val start = System.nanoTime()
        SimpleRules.match(text, ctx)?.let { lines ->
            return Decision(lines, Source.RULES, lines.joinToString("") { it.toDsl() }, elapsedMs(start))
        }
        val raw = llm.generate(ctx.toCtxLine(now), text)
        val lines = CommandParser.parse(raw)
            // Safety: sending and picking a contact only ever come from an explicit rule match.
            .filterNot { it is VegaLine.Tool && it.name in RULES_ONLY }
            .ifEmpty { listOf(VegaLine.Escalate) }
        return Decision(lines, Source.LLM, raw, elapsedMs(start))
    }

    private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000

    companion object {
        val RULES_ONLY = setOf("msg.send", "contact.select")
    }
}
