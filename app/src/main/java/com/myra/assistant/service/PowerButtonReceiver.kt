package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.myra.assistant.ui.main.MainActivity

/**
 * BroadcastReceiver detecting double screen/power button toggles to quickly summon MYRA.
 */
class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PowerButtonReceiver"
        private const val DOUBLE_PRESS_INTERVAL_MS = 600L
        private var lastToggleTime: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
            val now = System.currentTimeMillis()
            if (now - lastToggleTime <= DOUBLE_PRESS_INTERVAL_MS) {
                Log.d(TAG, "Double power toggle detected! Summoning MYRA...")
                lastToggleTime = 0L
                triggerMyra(context)
            } else {
                lastToggleTime = now
            }
        }
    }

    private fun triggerMyra(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            val overlayIntent = Intent(context, MyraOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(overlayIntent)
            } else {
                context.startService(overlayIntent)
            }
        } else {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(mainIntent)
        }
    }
}
