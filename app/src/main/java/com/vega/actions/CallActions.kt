package com.vega.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import com.vega.brain.ChoiceKind
import com.vega.brain.ContactMatch
import com.vega.brain.ContactResolver
import com.vega.brain.DialogContext
import com.vega.brain.PendingContact
import com.vega.brain.Replies
import com.vega.llm.VegaLine

class CallActions(private val context: Context, private val contacts: ContactResolver) {
    private val telecom = context.getSystemService(TelecomManager::class.java)

    suspend fun handle(tool: VegaLine.Tool, ctx: DialogContext): ActionResult = when (tool.name) {
        "call.place" -> place(tool.arg("to"), ctx)
        "call.dial" -> tool.arg("number")?.let { dial(it, it) } ?: ActionResult(false, Replies.ASK_NUMBER)
        "call.answer" -> answer()
        "call.reject", "call.end" -> hangUp()
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private suspend fun place(to: String?, ctx: DialogContext): ActionResult {
        if (to == null) return ActionResult(false, Replies.ASK_NUMBER)
        return when (val match = contacts.resolve(to)) {
            is ContactMatch.One -> dial(match.contact.number, match.contact.name)
            is ContactMatch.Many -> {
                ctx.pendingContact = PendingContact(PendingContact.Kind.CALL, match.options)
                ctx.choosing = ChoiceKind.CONTACT
                ActionResult(true, Replies.whichContact(match.options.map { it.name }), followUp = true)
            }
            ContactMatch.None -> ActionResult(false, Replies.contactNotFound(to))
            ContactMatch.NoPermission -> ActionResult(false, Replies.MISSING_PERMISSION)
        }
    }

    fun dial(number: String, label: String): ActionResult {
        if (!context.has(Manifest.permission.CALL_PHONE)) return ActionResult(false, Replies.MISSING_PERMISSION)
        context.launch(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        return ActionResult(true, Replies.calling(label))
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // still the only public API for this on Android 14
    private fun answer(): ActionResult {
        if (!context.has(Manifest.permission.ANSWER_PHONE_CALLS)) return ActionResult(false, Replies.MISSING_PERMISSION)
        telecom.acceptRingingCall()
        return ActionResult(true, toast = "Answered")
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun hangUp(): ActionResult {
        if (!context.has(Manifest.permission.ANSWER_PHONE_CALLS)) return ActionResult(false, Replies.MISSING_PERMISSION)
        telecom.endCall()
        return ActionResult(true, toast = "Call ended")
    }
}
