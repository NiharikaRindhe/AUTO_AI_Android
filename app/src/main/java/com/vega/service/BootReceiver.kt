package com.vega.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts Vega and greets the driver when the head unit boots. Many head units don't reboot on ignition;
 * the vendor's ACC on/off broadcasts (found on day 1) should also be routed to GREET and GOODBYE.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val start = Intent(context, VegaService::class.java).setAction(VegaService.ACTION_GREET)
        try {
            context.startForegroundService(start)
        } catch (e: Exception) {
            Log.w("Vega", "Start at boot was blocked", e)
        }
    }
}
