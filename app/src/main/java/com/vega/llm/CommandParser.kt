package com.vega.llm

/** Parses LLM output in Vega's command format. Anything outside the tags is ignored. */
object CommandParser {
    private val LINE = Regex("""<t>(.*?)</t>|<say>(.*?)</say>|<ask>(.*?)</ask>|<esc\s*/>""", RegexOption.DOT_MATCHES_ALL)
    private val NAME = Regex("""^[a-z]+(?:\.[a-z_]+)?""")
    private val ARG = Regex("""([A-Za-z_][\w.]*)=(?:"([^"]*)"|(\S+))""")

    fun parse(output: String): List<VegaLine> = LINE.findAll(output).mapNotNull { m ->
        val groups = m.groups
        when {
            groups[1] != null -> parseTool(groups[1]!!.value)
            groups[2] != null -> VegaLine.Say(groups[2]!!.value.trim())
            groups[3] != null -> VegaLine.Ask(groups[3]!!.value.trim())
            else -> VegaLine.Escalate
        }
    }.toList()

    fun parseTool(body: String): VegaLine.Tool? {
        val trimmed = body.trim()
        val name = NAME.find(trimmed)?.value ?: return null
        val args = ARG.findAll(trimmed.substring(name.length)).associate { m ->
            m.groupValues[1] to (m.groups[2]?.value ?: m.groupValues[3])
        }
        return VegaLine.Tool(name, args)
    }
}
