package com.out.androidtvremote.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.sin

class WaterVolumeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_LEVEL = 15
        private const val WAVE_SPEED_1 = 1800L
        private const val WAVE_SPEED_2 = 2600L
        private const val WAVE_AMPLITUDE_DP = 5f
        private const val FILL_DURATION = 600L
        private const val MIN_FILL = 0.04f
        private const val MAX_FILL = 0.93f

        // Sea-water palette
        private val COLOR_DEEP  = Color.parseColor("#0077B6")  // deep ocean
        private val COLOR_MID   = Color.parseColor("#00B4D8")  // mid water
        private val COLOR_LIGHT = Color.parseColor("#90E0EF")  // surface shimmer
    }

    var accentColor: Int = COLOR_MID
        set(value) { field = value; updateColors(); invalidate() }

    private var currentFill = 0.45f
    private var targetFill  = 0.45f

    private val fillAnimator = ValueAnimator().apply {
        duration = FILL_DURATION
        interpolator = DecelerateInterpolator(1.6f)
        addUpdateListener {
            currentFill = it.animatedValue as Float
            updateGradient()
            invalidate()
        }
    }

    private val fillPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val backWavePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val frontWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val wavePath = Path()
    private val waveAmplitudePx get() = WAVE_AMPLITUDE_DP * resources.displayMetrics.density

    private val startTime = System.currentTimeMillis()
    private val tickRunnable = object : Runnable {
        override fun run() { invalidate(); postDelayed(this, 16L) }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updateColors()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tickRunnable) }
    override fun onDetachedFromWindow() {
        removeCallbacks(tickRunnable); fillAnimator.cancel(); super.onDetachedFromWindow()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setLevel(level: Int) {
        val clamped = level.coerceIn(0, MAX_LEVEL)
        targetFill = MIN_FILL + (clamped.toFloat() / MAX_LEVEL) * (MAX_FILL - MIN_FILL)
        fillAnimator.cancel()
        fillAnimator.setFloatValues(currentFill, targetFill)
        fillAnimator.start()
    }

    // ── Colors ────────────────────────────────────────────────────────────────

    private fun updateColors() {
        fillPaint.color = ColorUtils.setAlphaComponent(COLOR_DEEP, 230)
        backWavePaint.color = ColorUtils.setAlphaComponent(COLOR_MID, 200)
        frontWavePaint.color = ColorUtils.setAlphaComponent(COLOR_LIGHT, 180)
    }

    private fun updateGradient() {
        if (width == 0 || height == 0) return
        val waterY = height * (1f - currentFill)
        fillPaint.shader = LinearGradient(
            0f, waterY, 0f, height.toFloat(),
            intArrayOf(COLOR_MID, COLOR_DEEP),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val elapsed = System.currentTimeMillis() - startTime
        val waterY  = height * (1f - currentFill)

        // 1. Solid gradient body
        canvas.drawRect(0f, waterY, width.toFloat(), height.toFloat(), fillPaint)

        // 2. Back wave — slower, deeper blue
        drawWave(canvas, backWavePaint, waterY + waveAmplitudePx,
            elapsed, WAVE_SPEED_2, Math.PI.toFloat())

        // 3. Front wave — faster, light shimmer
        drawWave(canvas, frontWavePaint, waterY,
            elapsed, WAVE_SPEED_1, 0f)
    }

    private fun drawWave(
        canvas: Canvas, paint: Paint, waterY: Float,
        elapsed: Long, period: Long, phaseOffset: Float
    ) {
        wavePath.reset()
        val w = width.toFloat()
        val h = height.toFloat()
        val phase = (elapsed.toFloat() / period) * 2f * Math.PI.toFloat() + phaseOffset

        wavePath.moveTo(0f, waterY)
        var x = 0f
        while (x <= w + 4f) {
            val y = waterY + sin((x / w) * 2f * Math.PI.toFloat() + phase) * waveAmplitudePx
            wavePath.lineTo(x, y)
            x += 4f
        }
        wavePath.lineTo(w, h)
        wavePath.lineTo(0f, h)
        wavePath.close()
        canvas.drawPath(wavePath, paint)
    }
}