package com.vega

import com.vega.brain.Brain
import com.vega.brain.ChoiceKind
import com.vega.brain.Contact
import com.vega.brain.ContactMatch
import com.vega.brain.DialogContext
import com.vega.brain.MessageDraft
import com.vega.brain.NameMatcher
import com.vega.llm.CommandParser
import com.vega.llm.PatternLlm
import com.vega.llm.VegaLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BrainTest {
    private val brain = Brain(PatternLlm())

    private fun decide(text: String, ctx: DialogContext = DialogContext()) =
        runBlocking { brain.decide(text, ctx, LocalTime.of(18, 40)) }

    private fun tool(name: String, vararg args: Pair<String, String>) = VegaLine.Tool(name, mapOf(*args))

    @Test
    fun parsesTheGuideExamples() {
        assertEquals(
            listOf(tool("win.set", "pos" to "driver", "pct" to "50"), tool("hvac.power", "on" to "false")),
            CommandParser.parse("<t>win.set pos=driver pct=50</t><t>hvac.power on=false</t>"),
        )
        assertEquals(
            tool("media.search", "app" to "youtube", "query" to "hanuman chalisa"),
            CommandParser.parse("""<t>media.search app=youtube query="hanuman chalisa"</t>""").single(),
        )
        assertEquals(VegaLine.Say("I'm great, Niharika!"), CommandParser.parse("<say>I'm great, Niharika!</say>").single())
        assertEquals(VegaLine.Escalate, CommandParser.parse("<esc/>").single())
    }

    @Test
    fun dslRoundTrips() {
        val t = tool("msg.draft", "to" to "rohan", "body" to "I'm coming late")
        assertEquals(t, CommandParser.parse(t.toDsl()).single())
    }

    @Test
    fun contextHeaderMatchesTheGuide() {
        assertEquals(
            "[ctx t=18:40 spd=0 drv=Niharika priv=ask net=0 media=none draft=none]",
            DialogContext().toCtxLine(LocalTime.of(18, 40)),
        )
    }

    @Test
    fun fixedPhrasesSkipTheLlm() {
        val d = decide("Pause!")
        assertEquals(Brain.Source.RULES, d.source)
        assertEquals(tool("media.ctrl", "action" to "pause"), d.lines.single())
    }

    @Test
    fun youtubeSearch() {
        assertEquals(
            tool("media.search", "app" to "youtube", "query" to "Hanuman Chalisa"),
            decide("Hey Vega, play Hanuman Chalisa on YouTube").lines.single(),
        )
    }

    @Test
    fun navigation() {
        assertEquals(tool("nav.to", "dest" to "pune railway station"), decide("take me to pune railway station").lines.single())
        assertEquals(tool("nav.nearby", "what" to "petrol pump"), decide("find the nearest petrol pump").lines.single())
        assertEquals(tool("nav.saved", "name" to "home"), decide("take me home").lines.single())
    }

    @Test
    fun compoundCommand() {
        assertEquals(
            listOf(tool("win.set", "pos" to "driver", "pct" to "50"), tool("hvac.power", "on" to "false")),
            decide("open my window halfway and switch off the AC").lines,
        )
    }

    @Test
    fun itsTooHotTurnsOnTheAc() {
        assertEquals(tool("hvac.temp", "val" to "22"), decide("it's too hot").lines.single())
    }

    @Test
    fun numberPicksFromOpenResults() {
        val ctx = DialogContext().apply { choosing = ChoiceKind.NAV }
        assertEquals(tool("nav.select", "index" to "2"), decide("the second one", ctx).lines.single())
    }

    @Test
    fun sendNeedsAnOpenDraft() {
        assertTrue(decide("send it").lines.none { it is VegaLine.Tool && it.name == "msg.send" })
        val ctx = DialogContext().apply { draft = MessageDraft("sms", "Rohan", "9876543210", "I'm coming late") }
        assertEquals(tool("msg.send"), decide("send it", ctx).lines.single())
    }

    @Test
    fun draftFlow() {
        assertEquals(
            tool("msg.draft", "to" to "rohan", "body" to "I'm coming late", "app" to "sms"),
            decide("message rohan I'm coming late").lines.single(),
        )
        val ctx = DialogContext().apply { draft = MessageDraft("sms", "Rohan", "9876543210", "I'm coming late") }
        assertEquals(
            tool("msg.edit", "op" to "append", "text" to "I'll reach by 7"),
            decide("and add that I'll reach by 7", ctx).lines.single(),
        )
    }

    @Test
    fun tellMeAJokeIsNotAMessage() {
        assertTrue(decide("tell me a joke").lines.single() is VegaLine.Say)
    }

    @Test
    fun indianNameSpellings() {
        val contacts = listOf(
            Contact("Gargee Joshi", "1"), Contact("Rohan Patil", "2"), Contact("Rohan Shah", "3"), Contact("Mummy", "4"),
        )
        assertEquals(ContactMatch.One(contacts[0]), NameMatcher.match("gargi", contacts))
        assertEquals(ContactMatch.One(contacts[1]), NameMatcher.match("rohan patil", contacts))
        assertEquals(ContactMatch.One(contacts[3]), NameMatcher.match("mom", contacts))
        assertTrue(NameMatcher.match("rohan", contacts) is ContactMatch.Many)
        assertEquals(ContactMatch.None, NameMatcher.match("priya", contacts))
    }
}
