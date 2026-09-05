package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * 20-bar vertical waveform reactive to microphone and speaker amplitude.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 20
        private const val LERP_FACTOR = 0.3f
    }

    private val barHeights = FloatArray(BAR_COUNT) { 4f }
    private val targetHeights = FloatArray(BAR_COUNT) { 4f }
    private var currentAmplitude: Float = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barRect = RectF()
    private var animator: ValueAnimator? = null
    private var timeStep = 0f

    init {
        startAnimation()
    }

    fun startAnimation() {
        if (animator != null && animator?.isRunning == true) return

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 50
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                timeStep += 0.25f
                updateBars()
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
        for (i in 0 until BAR_COUNT) {
            targetHeights[i] = 4f
            barHeights[i] = 4f
        }
        invalidate()
    }

    fun setAmplitude(rms: Float) {
        currentAmplitude = rms.coerceIn(0f, 1f)
    }

    private fun updateBars() {
        val maxBarHeight = height.toFloat() * 0.9f
        val minBarHeight = 4f

        for (i in 0 until BAR_COUNT) {
            if (currentAmplitude > 0.02f) {
                // Harmonic wave variation + amplitude
                val wave = (sin((timeStep + i * 0.45).toDouble()).toFloat() + 1f) / 2f
                val noise = Random.nextFloat() * 0.2f
                val target = minBarHeight + (maxBarHeight - minBarHeight) * (currentAmplitude * 0.8f + wave * 0.15f + noise * 0.05f)
                targetHeights[i] = target.coerceIn(minBarHeight, maxBarHeight)
            } else {
                targetHeights[i] = minBarHeight + (sin((timeStep * 0.5f + i * 0.3f).toDouble()).toFloat() + 1f) * 2f
            }

            // Lerp towards target height
            barHeights[i] += (targetHeights[i] - barHeights[i]) * LERP_FACTOR
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val totalWidth = width.toFloat()
        val spacing = totalWidth / (BAR_COUNT * 2)
        val barWidth = spacing
        val cy = height / 2f

        for (i in 0 until BAR_COUNT) {
            val barH = barHeights[i]
            val left = i * (barWidth + spacing) + spacing / 2f
            val right = left + barWidth
            val top = cy - (barH / 2f)
            val bottom = cy + (barH / 2f)

            barRect.set(left, top, right, bottom)

            // Dynamic alpha based on height
            val alphaNorm = (barH / height.toFloat()).coerceIn(0f, 1f)
            val alpha = (150 + alphaNorm * 105).toInt().coerceIn(150, 255)

            barPaint.color = Color.argb(alpha, 0xFF, 0x17, 0x44) // Crimson Cyberpunk #FF1744
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, barPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
