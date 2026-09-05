package com.myra.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.myra.assistant.ui.main.MainActivity
import kotlin.math.sqrt

/**
 * Background Service managing specialized hardware sensors and automated triggers:
 * 1. Shake to Summon / Raise-to-Talk (Accelerometer / Proximity)
 * 2. Flip to Mute / DND (Phone face-down detection)
 * 3. Anti-Theft Unplug Alarm (Detects sudden charger disconnect when phone is armed)
 */
class HardwareTriggerService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "HardwareTriggerService"
        private const val CHANNEL_ID = "myra_hardware_triggers"
        private const val NOTIFICATION_ID = 2001
        private const val SHAKE_THRESHOLD = 14.5f // m/s^2 change
        private const val SHAKE_COOLDOWN_MS = 2500L
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null

    private var lastShakeTime = 0L
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f

    private var isFaceDown = false
    private var alarmMediaPlayer: MediaPlayer? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
            val antiTheftEnabled = prefs.getBoolean("anti_theft_unplug", false)

            if (intent.action == Intent.ACTION_POWER_DISCONNECTED && antiTheftEnabled) {
                triggerAntiTheftSiren()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(powerReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(powerReceiver)
        } catch (e: Exception) {}
        stopSiren()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // 1. Shake to Summon
                val shakeEnabled = prefs.getBoolean("shake_to_summon", true)
                if (shakeEnabled) {
                    val deltaX = x - lastAccelX
                    val deltaY = y - lastAccelY
                    val deltaZ = z - lastAccelZ
                    val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                    val now = System.currentTimeMillis()
                    if (speed > SHAKE_THRESHOLD && now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                        lastShakeTime = now
                        launchMyraForVoice()
                    }
                }

                // 2. Flip to Mute / Face-Down detection (z < -8.5 means screen facing flat down)
                val flipDndEnabled = prefs.getBoolean("flip_to_mute", true)
                if (flipDndEnabled) {
                    if (z < -8.5f && !isFaceDown) {
                        isFaceDown = true
                        enableDoNotDisturb()
                    } else if (z > -4.0f && isFaceDown) {
                        isFaceDown = false
                    }
                }

                lastAccelX = x
                lastAccelY = y
                lastAccelZ = z
            }

            Sensor.TYPE_PROXIMITY -> {
                // Raise to Talk: when phone is lifted directly to the ear (proximity < 2cm)
                val raiseToTalkEnabled = prefs.getBoolean("raise_to_talk", false)
                val distance = event.values[0]
                if (raiseToTalkEnabled && distance < 2.0f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                        lastShakeTime = now
                        launchMyraForVoice()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun launchMyraForVoice() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_AUTO_START_MIC", true)
        }
        startActivity(intent)
    }

    private fun enableDoNotDisturb() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (e: Exception) {
            Log.e(TAG, "Cannot toggle silent mode: ${e.message}")
        }
    }

    private fun triggerAntiTheftSiren() {
        try {
            stopSiren()
            val sirenUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            alarmMediaPlayer = MediaPlayer().apply {
                setDataSource(this@HardwareTriggerService, sirenUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            // Launch Activity with Warning
            val warningIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("EXTRA_THEFT_TRIGGERED", true)
            }
            startActivity(warningIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing siren: ${e.message}")
        }
    }

    fun stopSiren() {
        try {
            alarmMediaPlayer?.stop()
            alarmMediaPlayer?.release()
            alarmMediaPlayer = null
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Smart Hardware Triggers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors gestures, phone position, and anti-theft sensors"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MYRA Hardware & Sensor Guard")
            .setContentText("Active • Shake to Summon & Anti-Theft Protection")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
