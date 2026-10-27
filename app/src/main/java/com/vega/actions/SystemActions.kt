package com.vega.actions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.vega.brain.Replies
import com.vega.llm.VegaLine

class SystemActions(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun handle(tool: VegaLine.Tool): ActionResult = when (tool.name) {
        "volume.set" -> volume(tool)
        "set.page" -> page(tool.arg("page") ?: "settings")
        "set.brightness" -> brightness(tool)
        "nav.ui" -> {
            context.launch(homeIntent())
            ActionResult(true, toast = "Home")
        }
        "weather.get" -> ActionResult(false, Replies.WEATHER_LATER)
        "help" -> ActionResult(true, Replies.HELP)
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private fun volume(tool: VegaLine.Tool): ActionResult {
        val stream = AudioManager.STREAM_MUSIC
        val maxIndex = audio.getStreamMaxVolume(stream)
        val current = audio.getStreamVolume(stream)
        val step = maxOf(1, maxIndex / 10)
        val level = tool.int("level")
        val target = when {
            level != null -> level.coerceIn(0, 100) * maxIndex / 100
            tool.arg("dir") == "up" -> current + step
            tool.arg("dir") == "down" -> current - step
            else -> return ActionResult(false, Replies.CANT_YET)
        }.coerceIn(0, maxIndex)
        audio.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
        return ActionResult(true, toast = Replies.volume(target * 100 / maxIndex))
    }

    private fun page(page: String): ActionResult {
        val (action, label) = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS to "Wi-Fi"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth"
            "display" -> Settings.ACTION_DISPLAY_SETTINGS to "display"
            "sound" -> Settings.ACTION_SOUND_SETTINGS to "sound"
            "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS to "hotspot"
            else -> Settings.ACTION_SETTINGS to "system"
        }
        context.launch(Intent(action))
        return ActionResult(true, Replies.openingSettings(label))
    }

    private fun brightness(tool: VegaLine.Tool): ActionResult {
        if (!Settings.System.canWrite(context)) {
            context.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return ActionResult(false, Replies.NEED_WRITE_SETTINGS)
        }
        val resolver = context.contentResolver
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val level = tool.int("level")
        val target = when {
            level != null -> level.coerceIn(0, 100) * 255 / 100
            tool.arg("dir") == "up" -> current + 40
            tool.arg("dir") == "down" -> current - 40
            else -> return ActionResult(false, Replies.CANT_YET)
        }.coerceIn(10, 255)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
        return ActionResult(true, toast = Replies.brightness(target * 100 / 255))
    }
}
