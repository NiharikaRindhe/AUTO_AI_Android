package com.vega.data

import android.content.Context
import com.vega.brain.PrivacyMode

/** Small synchronous settings store. Move to DataStore once settings start changing from several places. */
class VegaSettings(context: Context) {
    private val prefs = context.getSharedPreferences("vega", Context.MODE_PRIVATE)

    var driverName: String
        get() = prefs.getString("driver", "Niharika") ?: "Niharika"
        set(value) = prefs.edit().putString("driver", value.trim()).apply()

    var homeAddress: String
        get() = prefs.getString("home", "") ?: ""
        set(value) = prefs.edit().putString("home", value.trim()).apply()

    var officeAddress: String
        get() = prefs.getString("office", "") ?: ""
        set(value) = prefs.edit().putString("office", value.trim()).apply()

    /** "youtube" or "spotify", used when the user says just "play …". */
    var defaultMediaApp: String
        get() = prefs.getString("media_app", "youtube") ?: "youtube"
        set(value) = prefs.edit().putString("media_app", value).apply()

    /** "female" or "male". */
    var voice: String
        get() = prefs.getString("voice", "female") ?: "female"
        set(value) = prefs.edit().putString("voice", value).apply()

    var privacy: PrivacyMode
        get() = PrivacyMode.entries.firstOrNull { it.key == prefs.getString("privacy", null) } ?: PrivacyMode.ASK
        set(value) = prefs.edit().putString("privacy", value.key).apply()
}
