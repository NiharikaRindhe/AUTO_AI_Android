package com.vega.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.vega.brain.NameMatcher
import com.vega.brain.Replies
import com.vega.llm.VegaLine

class AppActions(private val context: Context) {
    private val pm = context.packageManager

    fun handle(tool: VegaLine.Tool): ActionResult = when (tool.name) {
        "app.open" -> open(tool.arg("name"))
        "app.close" -> close(tool.arg("name"))
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private fun open(name: String?): ActionResult {
        if (name == null) return ActionResult(false, Replies.ASK_APP)
        val app = find(name) ?: return ActionResult(false, Replies.appNotFound(name))
        val intent = pm.getLaunchIntentForPackage(app.packageName) ?: return ActionResult(false, Replies.appNotFound(name))
        context.launch(intent)
        return ActionResult(true, Replies.opening(app.label))
    }

    // A normal app can't close other apps. Going home is the closest we get without system rights (risk table, day 1).
    private fun close(name: String?): ActionResult {
        context.launch(homeIntent())
        return ActionResult(true, Replies.closing(name?.let { find(it)?.label ?: it } ?: "the app"))
    }

    private data class Launchable(val label: String, val packageName: String)

    private fun find(name: String): Launchable? {
        val key = name.lowercase().trim()
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.ResolveInfoFlags.of(0),
        ).map { Launchable(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.packageName }

        KNOWN[key]?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
        return apps
            .map { it to NameMatcher.closeness(key, it.label) }
            .filter { it.second >= 0.8 }
            .maxByOrNull { it.second }
            ?.first
    }

    private companion object {
        val KNOWN = mapOf(
            "youtube" to Packages.YOUTUBE,
            "you tube" to Packages.YOUTUBE,
            "spotify" to Packages.SPOTIFY,
            "maps" to Packages.MAPS,
            "map" to Packages.MAPS,
            "google maps" to Packages.MAPS,
            "whatsapp" to Packages.WHATSAPP,
            "chrome" to "com.android.chrome",
            "settings" to "com.android.settings",
            "play store" to "com.android.vending",
            "youtube music" to "com.google.android.apps.youtube.music",
            "jiosaavn" to "com.jio.media.jiobeats",
            "gaana" to "com.gaana",
        )
    }
}
