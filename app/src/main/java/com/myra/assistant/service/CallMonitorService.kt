package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.myra.assistant.ui.main.MainActivity

/**
 * Foreground Service monitoring incoming phone calls and notifying MYRA's AI voice engine.
 */
class CallMonitorService : Service() {

    companion object {
        private const val TAG = "CallMonitorService"
        private const val CHANNEL_ID = "myra_call_monitor_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_INCOMING_CALL = "com.myra.assistant.ACTION_INCOMING_CALL"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
    }

    private var callReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        registerCallReceiver()
        Log.d(TAG, "CallMonitorService started in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerCallReceiver() {
        callReceiver = object : BroadcastReceiver() {
            private var lastState = TelephonyManager.EXTRA_STATE_IDLE

            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                    if (state == lastState) return
                    lastState = state

                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"
                        val callerName = resolveContactName(context, incomingNumber)

                        Log.d(TAG, "Incoming call detected: $callerName ($incomingNumber)")

                        // Broadcast to MainActivity to prompt Gemini Live voice announcement
                        val announceIntent = Intent(ACTION_INCOMING_CALL).apply {
                            setPackage(context.packageName)
                            putExtra(EXTRA_CALLER_NAME, callerName)
                            putExtra(EXTRA_CALLER_NUMBER, incomingNumber)
                        }
                        context.sendBroadcast(announceIntent)
                    }
                }
            }
        }

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callReceiver, filter)
    }

    private fun resolveContactName(context: Context, number: String): String {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val primeContactsRaw = prefs.getString("prime_contacts", "") ?: ""

        // Check against saved prime contacts
        if (primeContactsRaw.isNotEmpty()) {
            val lines = primeContactsRaw.split(";")
            for (line in lines) {
                val parts = line.split("|")
                if (parts.size == 2) {
                    val pName = parts[0]
                    val pNum = parts[1].replace("\\s+".toRegex(), "")
                    val targetNum = number.replace("\\s+".toRegex(), "")
                    if (pNum.isNotEmpty() && targetNum.contains(pNum)) {
                        return "$pName (Prime Contact)"
                    }
                }
            }
        }

        return if (number.isNotBlank() && number != "Unknown") "Number $number" else "Unknown caller"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors background triggers and call events for MYRA"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentTitle("MYRA Active")
            .setContentText("Call monitor & voice assistant standing by")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        callReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callReceiver: ${e.message}")
            }
        }
        callReceiver = null
    }
}
