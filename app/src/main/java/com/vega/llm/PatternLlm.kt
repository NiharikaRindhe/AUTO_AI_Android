package com.vega.llm

/**
 * Stand-in for Qwen until the llama.cpp engine lands. It emits the same command format the
 * fine-tuned model will, so the parser, brain and actions run end to end from day 1, and the
 * same sentences become test cases for the real model later.
 */
class PatternLlm : LlmEngine {

    override suspend fun generate(ctxLine: String, userText: String): String {
        val driver = DRIVER.find(ctxLine)?.groupValues?.get(1) ?: "there"
        val text = TextNorm.clean(userText)

        // "open my window halfway and switch off the AC" → one command per clause,
        // but only when every clause is understood ("play tom and jerry" stays whole).
        val clauses = text.split(AND)
        if (clauses.size > 1) {
            val each = clauses.map { translate(it.trim(), driver) }
            if (each.all { it != null }) return each.joinToString("")
        }
        return translate(text, driver) ?: VegaLine.Escalate.toDsl()
    }

    private fun translate(text: String, driver: String): String? {
        for (rule in RULES) {
            val m = rule.regex.matchEntire(text) ?: continue
            rule.build(m.groupValues, driver)?.let { return it }
        }
        return null
    }

    private class Rule(pattern: String, val build: (g: List<String>, driver: String) -> String?) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private companion object {
        val DRIVER = Regex("""drv=(\S+)""")
        val AND = Regex("""\s+and\s+(?:then\s+)?""", RegexOption.IGNORE_CASE)
        val PERCENT = Regex("""(\d{1,3})\s*(?:%|percent|per cent)?""")

        const val POS = """(driver|passenger|rear left|rear right|back left|back right|front|rear|back|all)"""
        const val AC = """(?:the\s+)?(?:ac|a\.c\.?|air\s*con(?:ditioner|ditioning)?|climate)"""
        const val GO = """(?:take me|navigate|drive|go|head|directions|route|get me|let'?s go|start navigation)"""

        fun t(name: String, vararg args: Pair<String, Any?>): String =
            VegaLine.Tool(name, args.mapNotNull { (k, v) -> v?.toString()?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()).toDsl()

        fun say(text: String) = VegaLine.Say(text).toDsl()

        fun window(posRaw: String, noun: String, amountRaw: String, opening: Boolean): String? {
            val pos = when (posRaw.lowercase()) {
                "" -> if (noun.lowercase() == "windows") "all" else "driver"
                "rear left", "back left" -> "rear_left"
                "rear right", "back right" -> "rear_right"
                "back" -> "rear"
                else -> posRaw.lowercase()
            }
            val amount = amountRaw.lowercase().removePrefix("to ").trim()
            val pct = when (amount) {
                "", "fully", "completely", "all the way", "full" -> if (opening) 100 else 0
                "halfway", "half way", "half", "half open" -> 50
                "a bit", "a little", "a little bit", "slightly" ->
                    return t("win.adjust", "pos" to pos, "delta" to if (opening) 20 else -20)
                else -> PERCENT.matchEntire(amount)?.groupValues?.get(1)?.toInt() ?: return null
            }
            return t("win.set", "pos" to pos, "pct" to pct)
        }

        fun mediaApp(name: String) = if (name.lowercase().startsWith("spot")) "spotify" else "youtube"
        fun msgApp(word: String) = if (word.equals("whatsapp", ignoreCase = true)) "whatsapp" else "sms"

        // Order matters: specific patterns first, "open <anything>" near the end.
        val RULES = listOf(
            // Small talk
            Rule("""how\s+are\s+you(?:\s+doing)?(?:\s+today)?""") { _, d -> say("I'm great, $d! Where are we heading?") },
            Rule("""(?:hi|hello|hey|namaste)(?:\s+there)?""") { _, d -> say("Hi $d! How can I help?") },
            Rule("""who\s+are\s+you|what(?:'s|\s+is)\s+your\s+name""") { _, _ -> say("I'm Vega, your driving assistant.") },
            Rule("""good\s+(morning|afternoon|evening|night)""") { g, d -> say("Good ${g[1].lowercase()}, $d!") },
            Rule("""tell\s+me\s+a\s+joke""") { _, _ -> say("Why did the car get a puncture? There was a fork in the road!") },

            // Car (simulated until the CAN-box SDK)
            Rule("""(?:open|lower|roll down|put down)\s+(?:the\s+|my\s+)?(?:$POS\s+)?(?:side\s+)?(windows?)(?:\s+(.+))?""") { g, _ -> window(g[1], g[2], g[3], opening = true) },
            Rule("""(?:close|raise|roll up|put up)\s+(?:the\s+|my\s+)?(?:$POS\s+)?(?:side\s+)?(windows?)(?:\s+(.+))?""") { g, _ -> window(g[1], g[2], g[3], opening = false) },
            Rule("""(?:turn|switch)\s+(on|off)\s+$AC""") { g, _ -> t("hvac.power", "on" to g[1].equals("on", true)) },
            Rule("""(?:turn|switch)\s+$AC\s+(on|off)""") { g, _ -> t("hvac.power", "on" to g[1].equals("on", true)) },
            Rule("""(?:set\s+)?(?:the\s+)?(?:ac|temperature|temp)(?:\s+temperature)?\s+(?:to\s+)?(\d{2})(?:\s*(?:degrees?|°)(?:\s*c(?:elsius)?)?)?""") { g, _ -> t("hvac.temp", "val" to g[1]) },
            Rule("""(?:it'?s|it is|i'?m|i am|feeling)\s+(?:very\s+|too\s+|so\s+|really\s+|a bit\s+)?hot(?:\s+in here)?""") { _, _ -> t("hvac.temp", "val" to 22) },
            Rule("""(?:it'?s|it is|i'?m|i am|feeling)\s+(?:very\s+|too\s+|so\s+|really\s+|a bit\s+)?cold(?:\s+in here)?""") { _, _ -> t("hvac.temp", "val" to 26) },
            Rule("""(?:increase|raise|turn up)\s+(?:the\s+)?fan(?:\s+speed)?|fan\s+up""") { _, _ -> t("hvac.fan", "dir" to "up") },
            Rule("""(?:decrease|lower|reduce|turn down)\s+(?:the\s+)?fan(?:\s+speed)?|fan\s+down""") { _, _ -> t("hvac.fan", "dir" to "down") },
            Rule("""(?:set\s+)?(?:the\s+)?fan(?:\s+speed)?\s+(?:to\s+)?([0-5])""") { g, _ -> t("hvac.fan", "level" to g[1]) },
            Rule("""(?:set|change|make)?\s*(?:the\s+)?ambient\s+(?:light(?:s|ing)?\s+)?(?:colou?r\s+)?(?:to\s+)?([a-z]+)""") { g, _ -> t("light.ambient", "color" to g[1].lowercase()) },

            // System
            Rule("""(?:open\s+|show\s+|go to\s+)?(?:the\s+)?(wi-?fi|bluetooth|display|sound|hotspot)(?:\s+settings?)?(?:\s+page)?""") { g, _ -> t("set.page", "page" to g[1].lowercase().replace("-", "")) },
            Rule("""(?:set\s+|change\s+)?(?:the\s+)?volume\s+(?:to\s+)?(\d{1,3})\s*(?:%|percent)?""") { g, _ -> t("volume.set", "level" to g[1]) },
            Rule("""(?:set\s+|change\s+)?(?:the\s+)?brightness\s+(?:to\s+)?(\d{1,3})\s*(?:%|percent)?""") { g, _ -> t("set.brightness", "level" to g[1]) },
            Rule("""(?:increase|raise|turn up)\s+(?:the\s+)?brightness|brightness\s+up""") { _, _ -> t("set.brightness", "dir" to "up") },
            Rule("""(?:decrease|lower|reduce|turn down|dim)\s+(?:the\s+)?(?:brightness|screen)|brightness\s+down""") { _, _ -> t("set.brightness", "dir" to "down") },

            // Media
            Rule("""(?:play|search(?:\s+for)?|find|put on|show(?:\s+me)?)\s+(.+?)\s+(?:on|in|from|using)\s+(youtube|you tube|spotify)""") { g, _ -> t("media.search", "app" to mediaApp(g[2]), "query" to g[1]) },
            Rule("""play\s+(?:some\s+)?(.+)""") { g, _ -> t("media.search", "app" to "auto", "query" to g[1]) },

            // Navigation
            Rule("""$GO\s+(?:back\s+)?(?:to\s+)?(?:my\s+|the\s+)?(home|office|work)""") { g, _ -> t("nav.saved", "name" to g[1].lowercase().let { if (it == "work") "office" else it }) },
            Rule("""$GO\s+to\s+(.+)""") { g, _ -> t("nav.to", "dest" to g[1]) },
            Rule("""navigate\s+(.+)""") { g, _ -> t("nav.to", "dest" to g[1]) },
            Rule("""(?:find|show(?:\s+me)?|search(?:\s+for)?|where(?:'s|\s+is)|locate)\s+(?:the\s+|a\s+|an\s+|me\s+)?(?:nearest|nearby|closest)\s+(.+)""") { g, _ -> t("nav.nearby", "what" to g[1]) },
            Rule("""(?:nearest|closest)\s+(.+)""") { g, _ -> t("nav.nearby", "what" to g[1]) },
            Rule("""(?:find\s+|show\s+me\s+|search\s+for\s+)?(?:a\s+|an\s+|some\s+)?(.+?)\s+(?:near me|nearby|around here|close by)""") { g, _ -> t("nav.nearby", "what" to g[1]) },

            // Calls
            Rule("""(?:call|dial|phone)\s+([+\d][\d\s-]{5,})""") { g, _ -> t("call.dial", "number" to g[1].filter { it.isDigit() || it == '+' }) },
            Rule("""(?:call|phone|ring)\s+(?:up\s+)?(.+?)(?:\s+(?:now|please))?""") { g, _ -> t("call.place", "to" to g[1]) },

            // Messages
            Rule("""(?:send\s+)?(?:an?\s+)?(sms|text|message|msg|whatsapp)(?:\s+message)?\s+(?:to\s+)?(\w+)(?:\s+(?:that|saying|say))?\s+(.+)""") { g, _ -> t("msg.draft", "to" to g[2], "body" to g[3], "app" to msgApp(g[1])) },
            Rule("""send\s+(\w+)\s+an?\s+(sms|text|message|whatsapp)(?:\s+message)?(?:\s+(?:that|saying))?\s+(.+)""") { g, _ -> t("msg.draft", "to" to g[1], "body" to g[3], "app" to msgApp(g[2])) },
            Rule("""tell\s+(?!me\b)(\w+)\s+(?:that\s+)?(.+)""") { g, _ -> t("msg.draft", "to" to g[1], "body" to g[2], "app" to "sms") },
            Rule("""(?:send\s+)?(?:an?\s+)?(sms|text|message|whatsapp)(?:\s+message)?\s+(?:to\s+)?(\w+)""") { g, _ -> t("msg.draft", "to" to g[2], "app" to msgApp(g[1])) },
            Rule("""read\s+(?:out\s+)?(?:my\s+)?(?:last\s+|new\s+|latest\s+)?(?:messages?|texts?|sms)""") { _, _ -> t("msg.read") },

            // Apps
            Rule("""(?:open|launch|start)\s+(?:the\s+)?(.+?)(?:\s+app)?""") { g, _ -> t("app.open", "name" to g[1]) },
            Rule("""(?:close|exit|quit)\s+(?:the\s+)?(.+?)(?:\s+app)?""") { g, _ -> t("app.close", "name" to g[1]) },

            // Online (after day 15)
            Rule(""".*\b(?:weather|raining|rain today|forecast)\b.*""") { _, _ -> t("weather.get") },
        )
    }
}
