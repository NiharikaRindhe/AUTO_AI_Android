package com.vega.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.vega.brain.ChoiceKind
import com.vega.brain.Contact
import com.vega.brain.ContactMatch
import com.vega.brain.ContactResolver
import com.vega.brain.DialogContext
import com.vega.brain.MessageDraft
import com.vega.brain.PendingContact
import com.vega.brain.PrivacyMode
import com.vega.brain.Replies
import com.vega.data.VegaSettings
import com.vega.llm.VegaLine

/** Draft → review → edit → send. Nothing is sent until the user says "send" (safety rule). */
class MessageActions(
    private val context: Context,
    private val settings: VegaSettings,
    private val contacts: ContactResolver,
) {

    suspend fun handle(tool: VegaLine.Tool, ctx: DialogContext): ActionResult = when (tool.name) {
        "msg.draft" -> draft(tool, ctx)
        "msg.edit" -> edit(tool, ctx)
        "msg.send" -> send(ctx)
        "msg.cancel" -> {
            ctx.draft = null
            ActionResult(true, Replies.DRAFT_CANCELLED)
        }
        "msg.read" -> read(tool, ctx)
        "privacy.set" -> privacy(tool, ctx)
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private suspend fun draft(tool: VegaLine.Tool, ctx: DialogContext): ActionResult {
        val to = tool.arg("to") ?: return ActionResult(false, Replies.ASK_RECIPIENT)
        val body = tool.arg("body").orEmpty()
        val channel = if (tool.arg("app") == "whatsapp") "whatsapp" else "sms"
        return when (val match = contacts.resolve(to)) {
            is ContactMatch.One -> startDraft(match.contact, body, channel, ctx)
            is ContactMatch.Many -> {
                ctx.pendingContact = PendingContact(PendingContact.Kind.MESSAGE, match.options, body, channel)
                ctx.choosing = ChoiceKind.CONTACT
                ActionResult(true, Replies.whichContact(match.options.map { it.name }), followUp = true)
            }
            ContactMatch.None -> ActionResult(false, Replies.contactNotFound(to))
            ContactMatch.NoPermission -> ActionResult(false, Replies.MISSING_PERMISSION)
        }
    }

    fun startDraft(contact: Contact, body: String, channel: String, ctx: DialogContext): ActionResult {
        val draft = MessageDraft(channel, contact.name, contact.number, tidy(body))
        ctx.draft = draft
        val speech = if (draft.body.isEmpty()) Replies.askBody(contact.name) else Replies.draftReady(contact.name, draft.body)
        return ActionResult(true, speech, followUp = true)
    }

    private fun edit(tool: VegaLine.Tool, ctx: DialogContext): ActionResult {
        val draft = ctx.draft ?: return ActionResult(false, Replies.NO_DRAFT)
        val text = tool.arg("text")?.let { tidy(it) } ?: return ActionResult(true, Replies.ASK_EDIT, followUp = true)
        val body = if (tool.arg("op") == "replace" || draft.body.isEmpty()) text else join(draft.body, text)
        ctx.draft = draft.copy(body = body, lastAdded = text)
        return ActionResult(true, Replies.draftUpdated(body), followUp = true)
    }

    @SuppressLint("MissingPermission")
    private fun send(ctx: DialogContext): ActionResult {
        val draft = ctx.draft ?: return ActionResult(false, Replies.NO_DRAFT)
        if (draft.body.isBlank()) return ActionResult(true, Replies.askBody(draft.contactName), followUp = true)

        if (draft.channel == "whatsapp") {
            // Plan B from the risk table: pre-fill the chat, the user taps send.
            val uri = Uri.parse("https://wa.me/${whatsappNumber(draft.number)}?text=" + Uri.encode(draft.body))
            context.launch(Intent(Intent.ACTION_VIEW, uri).setPackage(Packages.WHATSAPP))
            ctx.draft = null
            return ActionResult(true, Replies.whatsappReady(draft.contactName))
        }

        if (!context.has(Manifest.permission.SEND_SMS)) return ActionResult(false, Replies.MISSING_PERMISSION)
        val sms = context.getSystemService(SmsManager::class.java)
        sms.sendMultipartTextMessage(draft.number, null, sms.divideMessage(draft.body), null, null)
        ctx.draft = null
        return ActionResult(true, Replies.sending(draft.contactName))
    }

    private fun read(tool: VegaLine.Tool, ctx: DialogContext): ActionResult {
        val draft = ctx.draft
        if (tool.arg("target") == "draft" && draft != null) {
            return ActionResult(true, Replies.readDraft(draft.contactName, draft.body), followUp = true)
        }
        // Reading incoming messages needs the NotificationListenerService.
        return ActionResult(false, Replies.READ_MESSAGES_LATER)
    }

    private fun privacy(tool: VegaLine.Tool, ctx: DialogContext): ActionResult {
        val mode = PrivacyMode.entries.firstOrNull { it.key == tool.arg("mode") } ?: PrivacyMode.ASK
        settings.privacy = mode
        ctx.privacy = mode
        return ActionResult(true, Replies.privacy(mode))
    }

    private companion object {
        val LONE_I = Regex("""\bi\b""")

        /** "i'm coming late" → "I'm coming late" (speech-to-text often returns lowercase). */
        fun tidy(text: String): String =
            text.trim().replace(LONE_I, "I").replaceFirstChar { it.uppercase() }

        fun join(body: String, addition: String): String {
            val base = body.trimEnd()
            val separator = if (base.lastOrNull() in listOf('.', '!', '?')) " " else ". "
            return base + separator + addition
        }

        /** wa.me needs the number with country code and digits only. */
        fun whatsappNumber(number: String): String {
            val digits = number.filter { it.isDigit() }
            return if (digits.length == 10) "91$digits" else digits.removePrefix("0")
        }
    }
}
