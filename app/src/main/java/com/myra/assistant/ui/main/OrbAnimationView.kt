package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * MYRA — HIGH-IMPACT CRIMSON CYBERPUNK ORB ANIMATION
 * Features:
 * - Pulsing glowing Crimson core (#FF1744) with neon magenta-purple 3D specular highlight (#D500F9)
 * - Radial cyberpunk energy glow (#FF1744 -> #880E4F -> transparent)
 * - High-contrast neon purple tilted orbital ellipse (#D500F9) rotating continuously
 * - High-contrast counter-rotating dashed crimson orbital ring (#FF1744)
 * - 4 vibrant neon purple orbiting energy particles (#E040FB) tracking along the orbital path
 * - Holographic HUD tick marks and cybernetic radar sweep
 * - Real-time amplitude & assistant state reactivity (IDLE, LISTENING, SPEAKING, THINKING, ACTIVE)
 */
class OrbAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE,
        LISTENING,
        SPEAKING,
        THINKING,
        ACTIVE
    }

    var state: State = State.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private var amplitude: Float = 0f

    private var rotationAngle = 0f
    private var pulseScale = 1.0f

    // Crimson Cyberpunk Color Palette
    private val colorCrimson = Color.parseColor("#FF1744")
    private val colorNeonPurple = Color.parseColor("#D500F9")
    private val colorGlowDeep = Color.parseColor("#880E4F")
    private val colorDarkBg = Color.parseColor("#050505")
    private val colorParticle = Color.parseColor("#E040FB")
    private val colorCyanAccent = Color.parseColor("#00E5FF")

    // Paints
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        color = colorNeonPurple
    }

    private val ringGlowPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.argb(80, 0xD5, 0x00, 0xF9)
    }

    private val ringPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.0f
        color = colorCrimson
        pathEffect = DashPathEffect(floatArrayOf(36f, 18f), 0f)
    }

    private val ringGlowPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = Color.argb(70, 0xFF, 0x17, 0x44)
        pathEffect = DashPathEffect(floatArrayOf(36f, 18f), 0f)
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorParticle
    }

    private val particleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 0xE0, 0x40, 0xFB)
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(130, 0xD5, 0x00, 0xF9)
        pathEffect = DashPathEffect(floatArrayOf(6f, 12f), 0f)
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Geometry caches
    private val oval1 = RectF()
    private val oval2 = RectF()

    private var rotationAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        // Continuous Rotation Animator for Outer Rings & Orbiting Particles (Continuous loop)
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Pulse Animator for Core Orb Breathing Effect (0.92 -> 1.08 -> 0.92)
        pulseAnimator = ValueAnimator.ofFloat(0.92f, 1.08f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setAmplitude(rms: Float) {
        amplitude = rms.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        if (centerX <= 0f || centerY <= 0f) return

        // Scale tuned to ensure orbital rings stay well within bounds
        val baseRadius = minOf(centerX, centerY) * 0.40f
        if (baseRadius <= 0f) return

        val dynamicScale = when (state) {
            State.SPEAKING, State.LISTENING, State.ACTIVE -> pulseScale + (amplitude * 0.22f)
            State.THINKING -> pulseScale * 1.06f
            State.IDLE -> pulseScale
        }
        val animatedRadius = baseRadius * dynamicScale

        // -------------------------------------------------------------
        // LAYER 1: Cyberpunk Deep Radial Atmosphere Glow
        // -------------------------------------------------------------
        val glowRadius = animatedRadius * 2.2f * (1.0f + amplitude * 0.15f)
        glowPaint.shader = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(
                Color.argb(200, 0xFF, 0x17, 0x44), // Crimson
                Color.argb(120, 0x88, 0x0E, 0x4F), // Deep wine glow
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // -------------------------------------------------------------
        // LAYER 2: Outer Cybernetic HUD Radar Ring (Dotted)
        // -------------------------------------------------------------
        val hudRadius = animatedRadius * 1.85f
        canvas.drawCircle(centerX, centerY, hudRadius, hudPaint)

        // -------------------------------------------------------------
        // LAYER 3: Core 3D Sphere (Crimson Core with Neon Purple Highlights)
        // -------------------------------------------------------------
        val hlX = centerX - (animatedRadius * 0.32f)
        val hlY = centerY - (animatedRadius * 0.32f)

        corePaint.shader = RadialGradient(
            hlX, hlY, animatedRadius * 1.25f,
            intArrayOf(
                colorNeonPurple, // Neon Purple #D500F9
                colorCrimson,    // Crimson #FF1744
                Color.parseColor("#4A0012"), // Dark crimson shadow
                colorDarkBg     // Cyber black #050505
            ),
            floatArrayOf(0.0f, 0.45f, 0.82f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, animatedRadius, corePaint)

        // Specular 3D Gloss Sheen
        specularPaint.shader = RadialGradient(
            hlX, hlY, animatedRadius * 0.45f,
            intArrayOf(
                Color.argb(210, 0xFF, 0x80, 0xAB),
                Color.argb(60, 0xD5, 0x00, 0xF9),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(hlX, hlY, animatedRadius * 0.45f, specularPaint)

        // -------------------------------------------------------------
        // LAYER 4: Tilted Orbiting Ring 1 (Neon Purple Ellipse #D500F9)
        // -------------------------------------------------------------
        canvas.save()
        canvas.rotate(rotationAngle, centerX, centerY)
        val rx1 = animatedRadius * 1.70f + (amplitude * 14f)
        val ry1 = animatedRadius * 0.85f + (amplitude * 8f)
        oval1.set(centerX - rx1, centerY - ry1, centerX + rx1, centerY + ry1)

        // Soft outer glow behind ring
        ringGlowPaint1.strokeWidth = 14f + (amplitude * 4f)
        canvas.drawOval(oval1, ringGlowPaint1)

        // Sharp neon purple ring
        ringPaint1.strokeWidth = 6f + (amplitude * 3f)
        canvas.drawOval(oval1, ringPaint1)
        canvas.restore()

        // -------------------------------------------------------------
        // LAYER 5: Reverse Counter-Rotating Ring 2 (Dashed Crimson Ellipse #FF1744)
        // -------------------------------------------------------------
        canvas.save()
        canvas.rotate(-rotationAngle * 1.25f, centerX, centerY)
        val rx2 = animatedRadius * 0.88f + (amplitude * 8f)
        val ry2 = animatedRadius * 1.75f + (amplitude * 14f)
        oval2.set(centerX - rx2, centerY - ry2, centerX + rx2, centerY + ry2)

        ringPaint2.strokeWidth = 4.5f + (amplitude * 2.5f)
        ringPaint2.pathEffect = DashPathEffect(floatArrayOf(34f, 16f), (-rotationAngle * 1.2f) % 50f)
        ringGlowPaint2.pathEffect = ringPaint2.pathEffect
        ringGlowPaint2.strokeWidth = 12f + (amplitude * 3f)

        // Soft outer glow behind ring
        canvas.drawOval(oval2, ringGlowPaint2)
        // Sharp dashed crimson ring
        canvas.drawOval(oval2, ringPaint2)
        canvas.restore()

        // -------------------------------------------------------------
        // LAYER 6: 4 Orbiting Glow Particles (Tracking Orbiting Path)
        // -------------------------------------------------------------
        val particleAngles = floatArrayOf(0f, 90f, 180f, 270f)
        val baseParticleRadius = 10f + (amplitude * 4f)
        val orbitRadAngle = Math.toRadians(rotationAngle.toDouble())

        for (i in particleAngles.indices) {
            val localAngle = Math.toRadians((particleAngles[i]).toDouble())
            // Point on unrotated ellipse
            val unrotatedX = rx1 * cos(localAngle).toFloat()
            val unrotatedY = ry1 * sin(localAngle).toFloat()

            // Rotate point by rotationAngle
            val cosRot = cos(orbitRadAngle).toFloat()
            val sinRot = sin(orbitRadAngle).toFloat()
            val px = centerX + (unrotatedX * cosRot - unrotatedY * sinRot)
            val py = centerY + (unrotatedX * sinRot + unrotatedY * cosRot)

            // Outer soft particle glow
            canvas.drawCircle(px, py, baseParticleRadius * 2.2f, particleGlowPaint)
            // Vibrant solid particle core
            canvas.drawCircle(px, py, baseParticleRadius, particlePaint)
            // Tiny bright white-cyan center dot
            val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(px, py, baseParticleRadius * 0.45f, centerDotPaint)
        }

        // -------------------------------------------------------------
        // LAYER 7: State-Specific Energy Surges (Thinking / Speaking)
        // -------------------------------------------------------------
        if (state == State.THINKING) {
            val thinkingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = colorCyanAccent
                strokeCap = Paint.Cap.ROUND
            }
            val thinkArcRect = RectF(
                centerX - animatedRadius * 1.35f,
                centerY - animatedRadius * 1.35f,
                centerX + animatedRadius * 1.35f,
                centerY + animatedRadius * 1.35f
            )
            canvas.drawArc(thinkArcRect, rotationAngle * 2.5f, 80f, false, thinkingPaint)
            thinkingPaint.color = colorNeonPurple
            canvas.drawArc(thinkArcRect, (rotationAngle * 2.5f) + 180f, 60f, false, thinkingPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
        pulseAnimator?.cancel()
    }
}
