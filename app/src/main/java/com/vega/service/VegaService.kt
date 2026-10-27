package com.vega.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log
import com.vega.R
import com.vega.VegaApp
import com.vega.ui.MainActivity
import com.vega.ui.overlay.OrbOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps Vega alive in the background with the orb on screen.
 *
 * Test from a PC:
 *   adb shell am start-foreground-service -n com.vega/.service.VegaService -a com.vega.action.TEXT --es text "play hanuman chalisa on youtube"
 *   adb shell am start-foreground-service -n com.vega/.service.VegaService -a com.vega.action.TALK
 */
class VegaService : Service() {
    private val assistant get() = (application as VegaApp).assistant
    private var overlay: OrbOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!startInForeground()) {
            stopSelf()
            return
        }
        overlay = OrbOverlay(this, assistant).also { it.show() }
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        overlay?.show() // no-op if already showing; picks up an overlay permission granted after start
        when (intent?.action) {
            ACTION_TALK -> assistant.toggleListening()
            ACTION_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let(assistant::submitText)
            ACTION_GREET -> assistant.greet()
            ACTION_GOODBYE -> assistant.goodbye()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        _running.value = false
        super.onDestroy()
    }

    private fun startInForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val talk = PendingIntent.getService(
            this, 1, Intent(this, VegaService::class.java).setAction(ACTION_TALK), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vega)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_vega), getString(R.string.talk), talk).build())
            .build()
        return try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            true
        } catch (e: Exception) {
            // Android 14 refuses a microphone service without RECORD_AUDIO, or when started from the background.
            Log.e("Vega", "Could not start in the foreground", e)
            false
        }
    }

    companion object {
        const val ACTION_TALK = "com.vega.action.TALK"
        const val ACTION_TEXT = "com.vega.action.TEXT"
        const val ACTION_GREET = "com.vega.action.GREET"
        const val ACTION_GOODBYE = "com.vega.action.GOODBYE"
        const val ACTION_STOP = "com.vega.action.STOP"
        const val EXTRA_TEXT = "text"

        private const val CHANNEL_ID = "vega"
        private const val NOTIFICATION_ID = 1

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
