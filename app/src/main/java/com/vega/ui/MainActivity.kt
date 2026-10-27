package com.vega.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vega.VegaApp
import com.vega.service.VegaService
import com.vega.ui.theme.VegaAppTheme

/** Setup and test screen: grant permissions, start Vega, type commands, read the trace log. */
class MainActivity : ComponentActivity() {
    private val assistant by lazy { (application as VegaApp).assistant }
    private var refresh by mutableIntStateOf(0)

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tick = refresh
            val missing = remember(tick) { missingPermissions() }
            val canOverlay = remember(tick) { Settings.canDrawOverlays(this) }
            VegaAppTheme {
                SetupScreen(
                    assistant = assistant,
                    missingPermissions = missing,
                    canDrawOverlays = canOverlay,
                    onGrantPermissions = { permissionRequest.launch(RUNTIME_PERMISSIONS.toTypedArray()) },
                    onGrantOverlay = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    },
                    onStartService = ::startVega,
                    onStopService = { stopService(Intent(this, VegaService::class.java)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++ // overlay permission is granted on a separate settings screen
    }

    private fun startVega() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow the microphone first", Toast.LENGTH_SHORT).show()
            permissionRequest.launch(RUNTIME_PERMISSIONS.toTypedArray())
            return
        }
        startForegroundService(Intent(this, VegaService::class.java).setAction(VegaService.ACTION_GREET))
    }

    private fun missingPermissions() =
        RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private companion object {
        val RUNTIME_PERMISSIONS = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
