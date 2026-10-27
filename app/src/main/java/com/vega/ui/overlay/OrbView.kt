package com.vega.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.ui.graphics.toArgb
import com.vega.brain.VegaState
import com.vega.ui.theme.VegaMotion
import com.vega.ui.theme.VegaNight
import com.vega.ui.theme.VegaPalette
import com.vega.ui.theme.VegaSize
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** The orb. Every state changes shape or glyph as well as colour (colour never works alone). */
class OrbView(context: Context) : View(context) {
    var palette: VegaPalette = VegaNight

    var state: VegaState = VegaState.IDLE
        set(value) {
            if (field == value) return
            field = value
            changedAt = SystemClock.uptimeMillis()
            contentDescription = "Vega ${value.name.lowercase()}"
            if (isStill(value)) animator.cancel() else if (isAttachedToWindow && !animator.isStarted) animator.start()
            invalidate()
        }

    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private val density = resources.displayMetrics.density
    private var changedAt = 0L
    private var shownLevel = 0f
    private var sweep: SweepGradient? = null
    private val rotation = Matrix()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6 * density
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 44 * density
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = VegaMotion.ThinkingTurnMs.toLong()
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    init {
        contentDescription = "Vega idle"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isStill(state)) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val start = palette.thinkingStart.toArgb()
        sweep = SweepGradient(w / 2f, h / 2f, intArrayOf(start, palette.thinkingEnd.toArgb(), start), null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val full = min(width, height) / 2f - ring.strokeWidth
        val idleR = VegaSize.OrbIdle.value * density / 2f
        val since = SystemClock.uptimeMillis() - changedAt
        val t = min(1f, since / VegaMotion.WakeMs.toFloat())
        val r = idleR + (full - idleR) * (1f - (1f - t) * (1f - t)) // 56 → 120 dp, ease-out
        val phase = animator.animatedValue as Float
        shownLevel += (level - shownLevel) * 0.35f

        // Amber shake for 300 ms on error.
        val shake = if (state == VegaState.ERROR && since < 300) sin(since / 300f * 6 * PI).toFloat() * 8 * density else 0f
        val cx = width / 2f + shake

        when (state) {
            VegaState.IDLE -> {
                fill.color = palette.listening.copy(alpha = 0.35f).toArgb()
                canvas.drawCircle(cx, cy, idleR * 0.6f, fill)
            }
            VegaState.LISTENING -> {
                fill.color = palette.surface.toArgb()
                canvas.drawCircle(cx, cy, r, fill)
                ring.color = palette.listening.toArgb()
                canvas.drawCircle(cx, cy, r * (0.65f + 0.35f * shownLevel), ring)
            }
            VegaState.THINKING -> {
                fill.color = palette.surface.toArgb()
                canvas.drawCircle(cx, cy, r, fill)
                rotation.setRotate(360f * phase, cx, cy)
                sweep?.setLocalMatrix(rotation)
                ring.shader = sweep
                ring.strokeWidth = 10 * density
                canvas.drawCircle(cx, cy, r * 0.8f, ring)
                ring.shader = null
                ring.strokeWidth = 6 * density
            }
            VegaState.SPEAKING -> {
                fill.color = palette.speaking.copy(alpha = 0.25f).toArgb()
                canvas.drawCircle(cx, cy, r, fill)
                fill.color = palette.speaking.toArgb()
                val pulse = 0.55f + 0.15f * sin(phase * 2 * PI * 4).toFloat()
                canvas.drawCircle(cx, cy, r * pulse, fill)
            }
            VegaState.CONFIRMING, VegaState.CHOOSING -> {
                fill.color = palette.surface.toArgb()
                canvas.drawCircle(cx, cy, r, fill)
                ring.color = palette.listening.toArgb()
                canvas.drawCircle(cx, cy, r * 0.85f, ring)
                drawGlyph(canvas, "?", cx, cy, palette.listening.toArgb())
            }
            VegaState.IN_CALL -> {
                fill.color = palette.surface.toArgb()
                canvas.drawCircle(cx, cy, r, fill)
                ring.color = palette.success.toArgb()
                canvas.drawCircle(cx, cy, r * 0.85f, ring)
            }
            VegaState.DONE -> {
                fill.color = palette.success.toArgb()
                canvas.drawCircle(cx, cy, r * 0.8f, fill)
                drawGlyph(canvas, "✓", cx, cy, palette.text.toArgb())
            }
            VegaState.ERROR -> {
                fill.color = palette.warning.toArgb()
                canvas.drawCircle(cx, cy, r * 0.8f, fill)
                drawGlyph(canvas, "!", cx, cy, palette.surface.toArgb())
            }
            VegaState.MUTED -> {
                fill.color = palette.danger.toArgb()
                canvas.drawCircle(cx, cy, idleR, fill)
                drawGlyph(canvas, "×", cx, cy, palette.text.toArgb())
            }
        }
    }

    private fun drawGlyph(canvas: Canvas, text: String, cx: Float, cy: Float, color: Int) {
        glyph.color = color
        canvas.drawText(text, cx, cy - (glyph.descent() + glyph.ascent()) / 2, glyph)
    }

    private fun isStill(s: VegaState) = s == VegaState.IDLE || s == VegaState.MUTED
}
