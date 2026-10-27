package com.vega.llm

/**
 * One line of Vega's command format:
 *
 *   line := <t>tool key=value…</t> | <say>text</say> | <ask>question</ask> | <esc/>
 */
sealed interface VegaLine {
    fun toDsl(): String

    data class Tool(val name: String, val args: Map<String, String> = emptyMap()) : VegaLine {
        fun arg(key: String): String? = args[key]?.trim()?.takeIf { it.isNotEmpty() }
        fun int(key: String): Int? = arg(key)?.toIntOrNull()
        fun bool(key: String): Boolean? = when (arg(key)?.lowercase()) {
            "true", "on", "1", "yes" -> true
            "false", "off", "0", "no" -> false
            else -> null
        }

        override fun toDsl(): String = buildString {
            append("<t>").append(name)
            for ((key, raw) in args) {
                val value = raw.replace('"', '\'')
                append(' ').append(key).append('=')
                if (value.isEmpty() || value.any { it.isWhitespace() }) append('"').append(value).append('"') else append(value)
            }
            append("</t>")
        }
    }

    data class Say(val text: String) : VegaLine {
        override fun toDsl() = "<say>$text</say>"
    }

    data class Ask(val text: String) : VegaLine {
        override fun toDsl() = "<ask>$text</ask>"
    }

    /** The model can't handle this; later this goes to the online backend. */
    data object Escalate : VegaLine {
        override fun toDsl() = "<esc/>"
    }
}
