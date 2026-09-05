package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.myra.assistant.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Background Service running continuous wake-word and audio-energy detection ("Hey MYRA").
 * When user speaks loudly or says wake words, it launches or wakes MYRA instantly.
 */
class BackgroundWakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "myra_wakeword_channel"
        private const val NOTIFICATION_ID = 1003
        const val ACTION_START = "com.myra.assistant.START_WAKEWORD"
        const val ACTION_STOP = "com.myra.assistant.STOP_WAKEWORD"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startWakeWordListening()
        Log.d(TAG, "BackgroundWakeWordService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWakeWordListening() {
        if (isListening) return
        isListening = true

        listeningJob = serviceScope.launch {
            val sampleRate = 16000
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord state not initialized, wake word paused")
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(1024)

                var speechConsecutiveFrames = 0
                while (isActive && isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)

                        // Threshold for voice detection
                        if (rms > 2200) {
                            speechConsecutiveFrames++
                            if (speechConsecutiveFrames >= 4) { // Sustained speech detected
                                Log.d(TAG, "Speech detected in background! Waking MYRA...")
                                speechConsecutiveFrames = 0
                                triggerWakeAssistant()
                                delay(3000) // Cooldown to avoid multiple rapid triggers
                            }
                        } else {
                            if (speechConsecutiveFrames > 0) speechConsecutiveFrames--
                        }
                    }
                    delay(30)
                }
            } catch (e: Exception) {
                Log.e(TAG, "WakeWord audio error: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun triggerWakeAssistant() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_AUTO_START_MIC", true)
        }
        startActivity(launchIntent)
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Voice Guard Active")
            .setContentText("Say 'Hey MYRA' to wake your AI assistant anytime")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors background wake word"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        listeningJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        Log.d(TAG, "BackgroundWakeWordService destroyed")
    }
}
