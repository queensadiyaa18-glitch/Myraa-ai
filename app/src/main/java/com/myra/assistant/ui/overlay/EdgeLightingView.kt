package com.myra.assistant.ui.overlay

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
import android.view.animation.LinearInterpolator

/**
 * Full-screen edge border lighting view for MYRA minimized floating mode.
 * Draws continuous, flowing neon electric blue (#00E5FF) and cyber cyan (#00B0FF)
 * glowing lines along the 4 edges of the screen with a rotating phase.
 */
class EdgeLightingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var phase = 0f
    private var animator: ValueAnimator? = null

    private val strokeWidthPx = 10f
    private val glowStrokeWidthPx = 22f

    private val borderRect = RectF()

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = glowStrokeWidthPx
    }

    private val colors = intArrayOf(
        Color.parseColor("#00E5FF"), // Electric Cyan
        Color.parseColor("#00B0FF"), // Electric Blue
        Color.parseColor("#7C4DFF"), // Neon Violet
        Color.parseColor("#00E5FF")  // Loop back
    )

    private val positions = floatArrayOf(0.0f, 0.4f, 0.8f, 1.0f)

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidthPx / 2f
        borderRect.set(inset, inset, w.toFloat() - inset, h.toFloat() - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Shift linear gradient coordinates according to animation phase
        val offsetX = (w * phase)
        val offsetY = (h * phase)

        val gradient = LinearGradient(
            offsetX % w,
            offsetY % h,
            (offsetX + w) % (w * 2),
            (offsetY + h) % (h * 2),
            colors,
            positions,
            Shader.TileMode.MIRROR
        )

        edgePaint.shader = gradient
        glowPaint.shader = gradient
        glowPaint.alpha = 110 // Soft neon glow aura

        // Draw soft glow underlayer with rounded corners
        canvas.drawRoundRect(borderRect, 28f, 28f, glowPaint)

        // Draw sharp high-intensity edge line
        canvas.drawRoundRect(borderRect, 28f, 28f, edgePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator?.isRunning != true) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
