package com.vega.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vega.brain.Assistant
import com.vega.brain.VegaState
import com.vega.ui.MainActivity
import com.vega.ui.theme.VegaMotion
import com.vega.ui.theme.VegaNight
import com.vega.ui.theme.VegaSize
import com.vega.ui.theme.VegaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Orb and caption strip drawn over other apps, bottom-right (nearest the driver in right-hand-drive cars).
 * Tap the orb to talk, tap again to stop, long-press to open the Vega app.
 */
class OrbOverlay(private val context: Context, private val assistant: Assistant) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var scope: CoroutineScope? = null

    fun show() {
        if (root != null || !Settings.canDrawOverlays(context)) return
        val density = context.resources.displayMetrics.density
        fun px(value: Dp) = (value.value * density).toInt()
        val palette = VegaNight

        val orb = OrbView(context).apply {
            setOnClickListener { assistant.toggleListening() }
            setOnLongClickListener {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }
        val caption = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, VegaType.Caption.fontSize.value)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = px(VegaSize.CardMaxWidth)
            setPadding(px(VegaSize.CardPadding), px(12.dp), px(VegaSize.CardPadding), px(12.dp))
            background = GradientDrawable().apply {
                cornerRadius = 999f * density
                setColor(palette.surface.toArgb())
                setStroke(maxOf(1, density.toInt()), palette.outline.toArgb())
            }
            visibility = View.GONE
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(caption, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = px(VegaSize.Gap) })
            addView(orb, LinearLayout.LayoutParams(px(VegaSize.OrbActive), px(VegaSize.OrbActive)))
        }
        val params = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = px(VegaSize.EdgeMargin)
            y = px(VegaSize.EdgeMargin)
        }
        windowManager.addView(row, params)
        root = row

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch { assistant.state.collect { orb.state = it } }
        s.launch { assistant.level.collect { orb.level = it } }
        s.launch {
            var hideJob: Job? = null
            combine(assistant.caption, assistant.state) { c, st -> c to st }.collect { (c, st) ->
                hideJob?.cancel()
                val text = c.vega.ifEmpty { c.user }
                if (text.isEmpty()) {
                    caption.visibility = View.GONE
                    return@collect
                }
                caption.text = if (c.vega.isEmpty()) "“$text”" else text
                val partial = c.vega.isEmpty() && !c.userFinal
                caption.setTextColor((if (partial) palette.textSecondary else palette.text).toArgb())
                caption.visibility = View.VISIBLE
                if (st == VegaState.IDLE) {
                    hideJob = launch {
                        delay(VegaMotion.CaptionHideMs.toLong())
                        caption.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun hide() {
        scope?.cancel()
        scope = null
        root?.let { windowManager.removeView(it) }
        root = null
    }
}
