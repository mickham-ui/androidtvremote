package com.out.androidtvremote.ui.frags.remote

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.sin

/**
 * A GPU-accelerated animated volume bars visualizer.
 *
 * Features:
 * - 5 animated vertical bars that scale with the current volume level (0–15).
 * - Smooth 60fps ValueAnimator-driven height transitions.
 * - Glow effect via BlurMaskFilter on a hardware layer.
 * - Spring/overshoot pulse animation triggered on each button press.
 * - Idle sine-wave breathe animation when no button is pressed.
 */
class VolumeVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 5
        private const val MAX_LEVEL = 15
        private const val IDLE_AMPLITUDE = 0.08f   // ±8% idle breathe
        private const val IDLE_PERIOD_MS = 2200L
        private const val IDLE_PHASE_SHIFT = 0.4f  // radians between bars
        private const val PULSE_OVERSHOOT = 0.22f  // overshoot fraction on pulse
        private const val ANIMATE_DURATION = 380L
    }

    // Relative heights of each bar at max volume (0f–1f), creating a wave shape
    private val barProfiles = floatArrayOf(0.55f, 0.75f, 1.0f, 0.75f, 0.55f)

    // Currently animated height fractions (driven by ValueAnimator)
    private val barHeights = FloatArray(BAR_COUNT) { 0.35f }

    // Target height fractions (recalculated on level change)
    private var targetHeights = FloatArray(BAR_COUNT) { 0.35f }

    private var currentLevel: Int = 7  // start at mid-range

    // Resolved accent color (set from Fragment after inflate)
    var accentColor: Int = Color.parseColor("#7B61FF")
        set(value) { field = value; updatePaints(); invalidate() }

    // ── Paints ───────────────────────────────────────────────────────────────
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 80
    }

    private val barRect = RectF()

    // ── Animators ────────────────────────────────────────────────────────────
    private val heightAnimator = ValueAnimator().apply {
        duration = ANIMATE_DURATION
        interpolator = OvershootInterpolator(1.8f)
        addUpdateListener { anim ->
            val frac = anim.animatedFraction
            for (i in 0 until BAR_COUNT) {
                barHeights[i] = lastHeights[i] + (targetHeights[i] - lastHeights[i]) * frac
            }
            invalidate()
        }
    }

    private var lastHeights = FloatArray(BAR_COUNT) { 0.35f }

    // Idle breathe — a simple Runnable that uses sin(time) to oscillate
    private var idleStartTime = System.currentTimeMillis()
    private val breatheRunnable = object : Runnable {
        override fun run() {
            if (!heightAnimator.isRunning) {
                val elapsed = (System.currentTimeMillis() - idleStartTime).toFloat()
                for (i in 0 until BAR_COUNT) {
                    val phase = i * IDLE_PHASE_SHIFT
                    val sine = sin((elapsed / IDLE_PERIOD_MS) * 2 * Math.PI + phase).toFloat()
                    barHeights[i] = targetHeights[i] + sine * IDLE_AMPLITUDE
                }
                invalidate()
            }
            postDelayed(this, 16L) // ~60fps
        }
    }

    init {
        // Hardware layer enables BlurMaskFilter glow
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaints()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(breatheRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(breatheRunnable)
        heightAnimator.cancel()
        super.onDetachedFromWindow()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Set the target volume level (0–MAX_LEVEL) and animate bars toward it. */
    fun setLevel(level: Int) {
        currentLevel = level.coerceIn(0, MAX_LEVEL)
        recalculateTargets(overshoot = false)
        animateBars()
    }

    /** Trigger a brief overshoot pulse (call when button is physically pressed). */
    fun pulse() {
        recalculateTargets(overshoot = true)
        animateBars()
        // After the overshoot settle, settle to the true target
        postDelayed({
            recalculateTargets(overshoot = false)
            animateBars()
        }, ANIMATE_DURATION + 40)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun recalculateTargets(overshoot: Boolean) {
        val levelFrac = currentLevel.toFloat() / MAX_LEVEL
        val base = 0.12f + levelFrac * 0.78f  // maps 0–1 → 0.12–0.90
        val boost = if (overshoot) PULSE_OVERSHOOT else 0f
        for (i in 0 until BAR_COUNT) {
            targetHeights[i] = (base * barProfiles[i] + boost).coerceIn(0.08f, 1.0f)
        }
    }

    private fun animateBars() {
        lastHeights = barHeights.copyOf()
        heightAnimator.cancel()
        heightAnimator.setFloatValues(0f, 1f)
        heightAnimator.start()
    }

    private fun updatePaints() {
        // Glow paint: same hue, very transparent, larger blur
        val glowColor = ColorUtils.setAlphaComponent(accentColor, 100)
        glowPaint.color = glowColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rebuild gradient shader to fit new size
        barPaint.shader = LinearGradient(
            0f, h.toFloat(), 0f, 0f,
            intArrayOf(
                ColorUtils.blendARGB(accentColor, Color.WHITE, 0.0f),
                ColorUtils.blendARGB(accentColor, Color.WHITE, 0.45f)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val totalWidth = width.toFloat()
        val totalHeight = height.toFloat()

        val barGap = totalWidth * 0.05f
        val barWidth = (totalWidth - barGap * (BAR_COUNT + 1)) / BAR_COUNT
        val cornerRadius = barWidth / 2f

        for (i in 0 until BAR_COUNT) {
            val h = barHeights[i].coerceIn(0.0f, 1.0f) * totalHeight
            val left  = barGap + i * (barWidth + barGap)
            val top   = totalHeight - h
            val right = left + barWidth

            barRect.set(left, top, right, totalHeight)

            // Glow layer (slightly wider, more transparent)
            glowPaint.maskFilter = android.graphics.BlurMaskFilter(
                barWidth * 0.7f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, glowPaint)

            // Main bar
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
        }
    }
}
