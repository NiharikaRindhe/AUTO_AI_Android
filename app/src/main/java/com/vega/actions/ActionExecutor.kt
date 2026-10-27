package com.vega.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.util.Log
import com.vega.brain.ContactResolver
import com.vega.brain.DialogContext
import com.vega.brain.PendingContact
import com.vega.brain.Replies
import com.vega.data.VegaSettings
import com.vega.llm.VegaLine

/** Routes each tool from the brain to the code that performs it. */
class ActionExecutor(
    context: Context,
    settings: VegaSettings,
    contacts: ContactResolver,
    private val car: CarSimActions,
) {
    private val apps = AppActions(context)
    private val system = SystemActions(context)
    private val media = MediaActions(context, settings)
    private val nav = NavActions(context, settings)
    private val calls = CallActions(context, contacts)
    private val messages = MessageActions(context, settings, contacts)

    suspend fun execute(tool: VegaLine.Tool, ctx: DialogContext): ActionResult = try {
        when (tool.name.substringBefore('.')) {
            "app" -> apps.handle(tool)
            "media" -> media.handle(tool, ctx)
            "nav" -> if (tool.name == "nav.ui") system.handle(tool) else nav.handle(tool, ctx)
            "volume", "set", "weather", "help" -> system.handle(tool)
            "call" -> calls.handle(tool, ctx)
            "msg", "privacy" -> messages.handle(tool, ctx)
            "contact" -> selectContact(tool, ctx)
            "win", "hvac", "light" -> car.handle(tool)
            else -> ActionResult(false, Replies.CANT_YET)
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "${tool.name}: missing permission", e)
        ActionResult(false, Replies.MISSING_PERMISSION)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "${tool.name}: no app for intent", e)
        ActionResult(false, Replies.APP_MISSING)
    }

    private fun selectContact(tool: VegaLine.Tool, ctx: DialogContext): ActionResult {
        val pending = ctx.pendingContact ?: return ActionResult(false, Replies.NOTHING_TO_CHOOSE)
        val contact = pending.options.getOrNull((tool.int("index") ?: 0) - 1)
            ?: return ActionResult(false, Replies.noSuchOption(pending.options.size), followUp = true)
        ctx.pendingContact = null
        return when (pending.kind) {
            PendingContact.Kind.CALL -> calls.dial(contact.number, contact.name)
            PendingContact.Kind.MESSAGE -> messages.startDraft(contact, pending.body, pending.channel, ctx)
        }
    }

    private companion object {
        const val TAG = "Vega"
    }
}
