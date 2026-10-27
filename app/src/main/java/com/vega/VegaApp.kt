package com.vega

import android.app.Application
import com.vega.brain.Assistant
import com.vega.data.TraceLog

class VegaApp : Application() {
    lateinit var assistant: Assistant
        private set

    override fun onCreate() {
        super.onCreate()
        TraceLog.init(this)
        assistant = Assistant(this)
    }
}
