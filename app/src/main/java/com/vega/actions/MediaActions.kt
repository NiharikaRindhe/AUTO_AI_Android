package com.vega.actions

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import com.vega.brain.ChoiceKind
import com.vega.brain.DialogContext
import com.vega.brain.Replies
import com.vega.data.VegaSettings
import com.vega.llm.VegaLine

class MediaActions(private val context: Context, private val settings: VegaSettings) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun handle(tool: VegaLine.Tool, ctx: DialogContext): ActionResult = when (tool.name) {
        "media.search" -> search(tool.arg("app"), tool.arg("query"), ctx)
        "media.ctrl" -> control(tool.arg("action") ?: "toggle")
        // Needs the AccessibilityService that draws numbered badges over YouTube results.
        "media.select" -> ActionResult(false, Replies.SELECT_NOT_READY)
        // Needs the NotificationListenerService for MediaSessionManager access.
        "media.info" -> ActionResult(false, Replies.MEDIA_INFO_NOT_READY)
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private fun search(appArg: String?, query: String?, ctx: DialogContext): ActionResult {
        if (query == null) return ActionResult(false, Replies.ASK_WHAT_TO_PLAY)
        val app = appArg?.takeUnless { it == "auto" } ?: settings.defaultMediaApp
        ctx.mediaApp = app

        if (app == "spotify") {
            // Spotify plays the top match directly, so there is nothing to choose.
            context.launch(
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .setPackage(Packages.SPOTIFY)
                    .putExtra(SearchManager.QUERY, query)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*"),
            )
            return ActionResult(true, Replies.playingOn(query, "Spotify"))
        }

        val intent = if (context.isInstalled(Packages.YOUTUBE)) {
            Intent(Intent.ACTION_SEARCH).setPackage(Packages.YOUTUBE).putExtra(SearchManager.QUERY, query)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
        }
        context.launch(intent)
        ctx.choosing = ChoiceKind.MEDIA
        return ActionResult(true, Replies.mediaResults(query, "YouTube"), followUp = true)
    }

    private fun control(action: String): ActionResult {
        val code = when (action) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev", "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return ActionResult(true, toast = Replies.mediaToast(action))
    }
}
