package com.myra.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.ui.overlay.EdgeLightingView
import com.myra.assistant.ui.overlay.GlassmorphicOrbView
import kotlin.math.abs

/**
 * Advanced Transparent Floating Overlay & Edge Lighting Foreground Service.
 * - Displays an ambient flowing neon edge lighting border across screen edges when minimized.
 * - Displays a movable glassmorphism circular floating orb ("MYRA") with ~35% frosted translucency.
 * - Single tap toggles MYRA's microphone state (Muted / Live) with visual and haptic/toast feedback.
 * - Long drag relocates the orb anywhere across the screen.
 * - Dynamic size adaptation based on user preferences (Small, Medium, Large).
 */
class MyraOverlayService : Service() {

    companion object {
        private const val TAG = "MyraOverlayService"
        private const val CHANNEL_ID = "myra_overlay_channel"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_START_OVERLAY = "com.myra.assistant.service.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.myra.assistant.service.ACTION_STOP_OVERLAY"
        const val ACTION_TOGGLE_MIC = "com.myra.assistant.service.ACTION_TOGGLE_MIC"
        const val BROADCAST_MIC_STATE_CHANGED = "com.myra.assistant.action.MIC_STATE_CHANGED"
        const val EXTRA_IS_MUTED = "extra_is_muted"

        var isServiceRunning = false
            private set

        var isMicMutedGlobal = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var orbOverlayView: View? = null
    private var edgeLightingView: EdgeLightingView? = null
    private var glassOrbView: GlassmorphicOrbView? = null

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        setupEdgeLighting()
        createFloatingGlassOrb()
        Log.d(TAG, "MyraOverlayService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MIC -> {
                toggleMicrophoneState()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Edge Lighting animation view covering the borders of the entire screen.
     * Uses FLAG_NOT_TOUCHABLE so it does NOT block touch interactions beneath it.
     */
    private fun setupEdgeLighting() {
        val edgeLightingEnabled = prefs.getBoolean("overlay_edge_lighting_enabled", true)
        if (!edgeLightingEnabled) return

        try {
            edgeLightingView = EdgeLightingView(this)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val edgeParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            windowManager?.addView(edgeLightingView, edgeParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add EdgeLightingView: ${e.message}")
        }
    }

    /**
     * Movable Glassmorphism Circular Floating Orb with ~35% alpha.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingGlassOrb() {
        try {
            val inflater = LayoutInflater.from(this)
            orbOverlayView = inflater.inflate(R.layout.overlay_orb, null)
            glassOrbView = orbOverlayView?.findViewById(R.id.overlayGlassOrbView)

            // Adjust orb size from preferences (Small: 90dp, Medium: 110dp, Large: 135dp)
            val sizeLevel = prefs.getString("overlay_orb_size", "medium") ?: "medium"
            val orbDp = when (sizeLevel) {
                "small" -> 90
                "large" -> 135
                else -> 110
            }
            val density = resources.displayMetrics.density
            val orbPx = (orbDp * density).toInt()

            glassOrbView?.layoutParams?.width = orbPx
            glassOrbView?.layoutParams?.height = orbPx
            glassOrbView?.isMicMuted = isMicMutedGlobal

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                x = (screenWidth / 2) - (orbPx / 2)
                y = (screenHeight / 2) - (orbPx / 2)
            }

            // Expand to Main Activity
            orbOverlayView?.findViewById<ImageButton>(R.id.overlayExpandBtn)?.setOnClickListener {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(mainIntent)
            }

            // Close Overlay
            orbOverlayView?.findViewById<ImageButton>(R.id.overlayCloseBtn)?.setOnClickListener {
                stopSelf()
            }

            // Drag and Tap gesture handling
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var touchStartTime = 0L

            orbOverlayView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(orbOverlayView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val totalDx = abs(event.rawX - initialTouchX)
                        val totalDy = abs(event.rawY - initialTouchY)
                        val clickDuration = System.currentTimeMillis() - touchStartTime

                        // Distinguish single tap vs dragging
                        if (totalDx < 15 && totalDy < 15 && clickDuration < 350) {
                            toggleMicrophoneState()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(orbOverlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding glass orb overlay view: ${e.message}")
        }
    }

    private fun toggleMicrophoneState() {
        isMicMutedGlobal = !isMicMutedGlobal
        glassOrbView?.isMicMuted = isMicMutedGlobal

        val msg = if (isMicMutedGlobal) "MYRA Mic Muted 🔇" else "MYRA Mic Listening 🎙️"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        // Notify MainActivity or running audio engines
        val broadcast = Intent(BROADCAST_MIC_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_MUTED, isMicMutedGlobal)
            setPackage(packageName)
        }
        sendBroadcast(broadcast)

        // Update ongoing foreground notification
        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Floating Glass Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating transparent glass orb and border edge lighting"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val toggleIntent = Intent(this, MyraOverlayService::class.java).apply {
            action = ACTION_TOGGLE_MIC
        }
        val pendingToggle = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val micStatus = if (isMicMutedGlobal) "Mic Muted 🔇 (Tap to Unmute)" else "Listening Live 🎙️ (Tap to Mute)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentTitle("MYRA Floating Assistant")
            .setContentText(micStatus)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.ic_mic_on, if (isMicMutedGlobal) "Unmute" else "Mute", pendingToggle)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false

        edgeLightingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing edgeLightingView: ${e.message}")
            }
        }
        edgeLightingView = null

        orbOverlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing orbOverlayView: ${e.message}")
            }
        }
        orbOverlayView = null
    }
}
