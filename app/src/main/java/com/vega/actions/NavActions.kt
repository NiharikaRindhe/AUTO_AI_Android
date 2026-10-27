package com.vega.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vega.brain.ChoiceKind
import com.vega.brain.DialogContext
import com.vega.brain.Replies
import com.vega.data.VegaSettings
import com.vega.llm.VegaLine

class NavActions(private val context: Context, private val settings: VegaSettings) {

    fun handle(tool: VegaLine.Tool, ctx: DialogContext): ActionResult = when (tool.name) {
        "nav.to" -> tool.arg("dest")?.let { navigate(it, it) } ?: ActionResult(false, Replies.ASK_DEST)
        "nav.saved" -> saved(tool.arg("name") ?: "home")
        "nav.nearby" -> nearby(tool.arg("what"), ctx)
        // Picking a result in Maps needs the AccessibilityService; there is no public intent for it.
        "nav.select" -> ActionResult(false, Replies.SELECT_NOT_READY)
        "nav.stop" -> ActionResult(false, Replies.NAV_STOP_NOT_READY)
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private fun navigate(destination: String, label: String): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
        if (context.isInstalled(Packages.MAPS)) intent.setPackage(Packages.MAPS)
        context.launch(intent)
        return ActionResult(true, Replies.navigatingTo(label))
    }

    private fun saved(name: String): ActionResult {
        val (address, label) = when (name) {
            "office", "work" -> settings.officeAddress to "the office"
            else -> settings.homeAddress to "home"
        }
        return if (address.isBlank()) ActionResult(false, Replies.placeNotSaved(if (label == "home") "home" else "office"))
        else navigate(address, label)
    }

    private fun nearby(what: String?, ctx: DialogContext): ActionResult {
        if (what == null) return ActionResult(false, Replies.ASK_DEST)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(what)))
        if (context.isInstalled(Packages.MAPS)) intent.setPackage(Packages.MAPS)
        context.launch(intent)
        ctx.choosing = ChoiceKind.NAV
        return ActionResult(true, Replies.nearby(what), followUp = true)
    }
}
