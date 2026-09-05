package com.myra.assistant.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Transparent Glassmorphism Floating Orb view with:
 * - 30-40% frosted glass translucent body (content behind is visible aar-paar)
 * - Thin outer rotating neon gradient border (Electric Cyan, Ice Blue, Magenta, Spark White)
 * - Concentric subtle glass refraction inner ring
 * - Center "MYRA" branding with glowing outline
 * - Dynamic microphone indicator dot / status ring (Muted: Amber/Red, Active: Cyan/Green)
 */
class GlassmorphicOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isMicMuted: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var rotationAngle = 0f
    private var pulseScale = 1.0f
    private var rotationAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val orbBounds = RectF()

    // Glass frosted background paint (approx 35% alpha: ~90 out of 255)
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(85, 10, 22, 35) // Deep translucent cyber teal-black
    }

    // Inner glass refraction highlight ring
    private val innerGlassRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(90, 255, 255, 255) // Refractive white gloss
    }

    // Outer thin rotating neon glowing border
    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }

    // Outer soft glow halo
    private val outerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    // Center "MYRA" Text Paint
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.18f
        isFakeBoldText = true
    }

    // Center "MYRA" Text Glow / Stroke Outline
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#00E5FF")
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.18f
        isFakeBoldText = true
    }

    // Status subtitle ("LISTEN" / "MUTED")
    private val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
        textSize = 20f
    }

    private val neonColors = intArrayOf(
        Color.parseColor("#00E5FF"), // Electric Cyan
        Color.parseColor("#00B0FF"), // Ice Blue
        Color.parseColor("#E040FB"), // Neon Magenta
        Color.parseColor("#FFFFFF"), // Spark Glint
        Color.parseColor("#00E5FF")  // Loop
    )

    private val mutedColors = intArrayOf(
        Color.parseColor("#FF5252"), // Red
        Color.parseColor("#FF9100"), // Amber
        Color.parseColor("#FF1744"), // Crimson
        Color.parseColor("#FF5252")  // Loop
    )

    init {
        startAnimations()
    }

    private fun startAnimations() {
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.96f, 1.04f).apply {
            duration = 1800L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 2f) - 14f
        if (baseRadius <= 0) return

        val currentRadius = baseRadius * pulseScale

        canvas.save()

        // 1. Draw Glassmorphism Frosted Translucent Disc (aar-paar visible)
        orbBounds.set(cx - currentRadius, cy - currentRadius, cx + currentRadius, cy + currentRadius)
        canvas.drawCircle(cx, cy, currentRadius, glassBgPaint)

        // 2. Draw Subtle Inner Glass Highlight Ring
        canvas.drawCircle(cx, cy, currentRadius * 0.85f, innerGlassRingPaint)

        // 3. Rotating Outer Neon Glowing Border
        val activeColors = if (isMicMuted) mutedColors else neonColors
        val sweepGradient = SweepGradient(cx, cy, activeColors, null)

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        outerBorderPaint.shader = sweepGradient
        outerHaloPaint.shader = sweepGradient
        outerHaloPaint.alpha = if (isMicMuted) 70 else 110

        // Glow halo
        canvas.drawCircle(cx, cy, currentRadius, outerHaloPaint)
        // Sharp neon border
        canvas.drawCircle(cx, cy, currentRadius, outerBorderPaint)
        canvas.restore()

        // 4. Center Typography: "MYRA" with glow outline
        val fontSize = currentRadius * 0.38f
        textPaint.textSize = fontSize
        textStrokePaint.textSize = fontSize
        textStrokePaint.color = if (isMicMuted) Color.parseColor("#FF5252") else Color.parseColor("#00E5FF")

        val fontMetrics = textPaint.fontMetrics
        val textBaseline = cy - (fontMetrics.descent + fontMetrics.ascent) / 2f - (currentRadius * 0.10f)

        // Draw outline / glow stroke then fill
        canvas.drawText("MYRA", cx, textBaseline, textStrokePaint)
        canvas.drawText("MYRA", cx, textBaseline, textPaint)

        // 5. Status Text: "MUTED" or "ON AIR"
        val status = if (isMicMuted) "MUTED 🔇" else "LIVE 🎙️"
        statusTextPaint.textSize = currentRadius * 0.16f
        statusTextPaint.color = if (isMicMuted) Color.parseColor("#FF8A80") else Color.parseColor("#80D8FF")
        val statusBaseline = textBaseline + (currentRadius * 0.32f)
        canvas.drawText(status, cx, statusBaseline, statusTextPaint)

        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (rotationAnimator?.isRunning != true) {
            startAnimations()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
        pulseAnimator?.cancel()
    }
}
