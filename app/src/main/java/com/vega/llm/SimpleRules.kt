package com.vega.llm

import com.vega.brain.ChoiceKind
import com.vega.brain.DialogContext
import com.vega.brain.MessageDraft
import com.vega.brain.NameMatcher

/**
 * Fixed phrases that never reach the LLM (design rule 1). They answer in well under 100 ms.
 * Context-dependent phrases ("send it", "number two") only match when a draft or a choice is open.
 */
object SimpleRules {

    fun match(text: String, ctx: DialogContext): List<VegaLine>? {
        val cleaned = TextNorm.clean(text)
        val key = TextNorm.key(text)
        if (key.isEmpty()) return null

        when (ctx.choosing) {
            ChoiceKind.CONTACT -> {
                val options = ctx.pendingContact?.options.orEmpty()
                val index = pickIndex(key)
                    ?: options.indexOfFirst { NameMatcher.closeness(key, it.name) >= 0.85 }.takeIf { it >= 0 }?.plus(1)
                if (index != null) return listOf(tool("contact.select", "index" to "$index"))
            }
            ChoiceKind.MEDIA -> pickIndex(key)?.let { return listOf(tool("media.select", "index" to "$it")) }
            ChoiceKind.NAV -> pickIndex(key)?.let { return listOf(tool("nav.select", "index" to "$it")) }
            null -> Unit
        }

        ctx.draft?.let { draft -> draftRule(cleaned, key, draft)?.let { return it } }

        return FIXED[key]
    }

    private fun draftRule(cleaned: String, key: String, draft: MessageDraft): List<VegaLine>? {
        if (key in SEND) return listOf(tool("msg.send"))
        if (key in CANCEL) return listOf(tool("msg.cancel"))
        if (key in READ_BACK) return listOf(tool("msg.read", "target" to "draft"))
        if (key in EDIT_ONLY) return listOf(VegaLine.Ask("What should I add or change?"))
        APPEND.matchEntire(cleaned)?.let { return listOf(tool("msg.edit", "op" to "append", "text" to it.groupValues[1])) }
        REPLACE.matchEntire(cleaned)?.let { return listOf(tool("msg.edit", "op" to "replace", "text" to it.groupValues[1])) }
        // Vega asked "What should the message say?" — the whole answer is the body.
        if (draft.body.isBlank()) return listOf(tool("msg.edit", "op" to "replace", "text" to cleaned))
        return null
    }

    private fun pickIndex(key: String): Int? = PICK.matchEntire(key)?.groupValues?.get(1)?.let { ORDINALS[it] }

    private fun tool(name: String, vararg args: Pair<String, String>) = VegaLine.Tool(name, mapOf(*args))

    private val PICK = Regex("""(?:number |option |the )?(one|two|three|four|five|first|second|third|fourth|fifth|[1-5])(?: one| option)?""")
    private val ORDINALS = mapOf(
        "one" to 1, "first" to 1, "1" to 1,
        "two" to 2, "second" to 2, "2" to 2,
        "three" to 3, "third" to 3, "3" to 3,
        "four" to 4, "fourth" to 4, "4" to 4,
        "five" to 5, "fifth" to 5, "5" to 5,
    )

    private val SEND = setOf(
        "send", "send it", "send it now", "send the message", "send message", "yes", "yes send", "yes send it",
        "yes please", "ok send it", "okay send it", "go ahead", "haan", "haan bhejo", "bhejo",
    )
    private val CANCEL = setOf(
        "cancel", "cancel it", "cancel the message", "dont send", "dont send it", "no", "no dont send it",
        "delete it", "discard", "discard it", "forget it",
    )
    private val READ_BACK = setOf("read it", "read it back", "read it again", "what does it say")
    private val EDIT_ONLY = setOf("edit", "edit it", "change it", "change the message", "edit the message")
    private val APPEND = Regex("""(?:and\s+)?(?:also\s+)?(?:add|append|include|also say)\s+(?:that\s+)?(.+)""", RegexOption.IGNORE_CASE)
    private val REPLACE = Regex("""(?:change|replace)\s+(?:it|the message|that)\s+(?:to|with)\s+(.+)""", RegexOption.IGNORE_CASE)

    private val FIXED: Map<String, List<VegaLine>> = buildMap {
        fun add(line: VegaLine, vararg phrases: String) = phrases.forEach { put(it, listOf(line)) }

        add(tool("media.ctrl", "action" to "pause"), "pause", "pause it", "pause music", "pause the music", "pause the song", "pause the video", "stop music", "stop the music")
        add(tool("media.ctrl", "action" to "play"), "play", "resume", "play music", "continue", "continue playing", "resume music")
        add(tool("media.ctrl", "action" to "next"), "next", "next song", "next video", "next track", "skip", "skip this", "skip song", "play next")
        add(tool("media.ctrl", "action" to "prev"), "previous", "previous song", "previous video", "previous track", "last song", "play previous")

        add(tool("volume.set", "dir" to "up"), "volume up", "louder", "increase volume", "increase the volume", "turn it up", "turn up the volume")
        add(tool("volume.set", "dir" to "down"), "volume down", "quieter", "softer", "decrease volume", "decrease the volume", "turn it down", "lower the volume", "turn down the volume")
        add(tool("volume.set", "level" to "0"), "mute", "mute volume", "mute the volume")
        add(tool("volume.set", "level" to "40"), "unmute")
        add(tool("volume.set", "level" to "100"), "max volume", "full volume", "maximum volume")

        add(tool("nav.ui", "page" to "home"), "home screen", "go to home screen", "go to the home screen", "show home screen")
        add(tool("nav.stop"), "stop navigation", "end navigation", "cancel navigation", "exit navigation", "stop the navigation")

        add(tool("call.answer"), "answer", "answer it", "answer the call", "answer call", "pick up", "pick up the call", "accept the call", "accept call")
        add(tool("call.reject"), "reject", "reject it", "reject the call", "reject call", "decline", "decline the call")
        add(tool("call.end"), "hang up", "end call", "end the call", "cut the call", "cut the phone", "disconnect")

        add(tool("msg.read"), "read my messages", "read messages", "read my last message", "read the message")
        add(tool("privacy.set", "mode" to "on"), "privacy on", "privacy mode on", "turn on privacy", "turn on privacy mode", "enable privacy")
        add(tool("privacy.set", "mode" to "off"), "privacy off", "privacy mode off", "turn off privacy", "turn off privacy mode", "disable privacy")
        add(tool("privacy.set", "mode" to "ask"), "privacy ask", "ask before reading messages")

        add(tool("hvac.power", "on" to "true"), "ac on", "turn on the ac", "turn on ac", "switch on the ac", "switch on ac", "start the ac")
        add(tool("hvac.power", "on" to "false"), "ac off", "turn off the ac", "turn off ac", "switch off the ac", "switch off ac", "stop the ac")

        add(tool("help"), "help", "what can you do", "what can i say")
        add(VegaLine.Say("You're welcome!"), "thank you", "thanks", "thank you vega", "thanks vega")
        add(VegaLine.Say("Okay."), "stop", "cancel", "never mind", "nevermind", "forget it", "nothing")
    }
}
