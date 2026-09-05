package com.myra.assistant.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Notification Listener providing notification intelligence, spam filtering, and auto-OTP detection.
 */
class MyraNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        const val ACTION_NOTIFICATION_RECEIVED = "com.myra.assistant.ACTION_NOTIFICATION_RECEIVED"
        const val ACTION_OTP_DETECTED = "com.myra.assistant.ACTION_OTP_DETECTED"
        const val EXTRA_NOTIF_TITLE = "extra_notif_title"
        const val EXTRA_NOTIF_TEXT = "extra_notif_text"
        const val EXTRA_NOTIF_PACKAGE = "extra_notif_package"
        const val EXTRA_OTP_CODE = "extra_otp_code"

        private val SPAM_KEYWORDS = listOf(
            "discount", "cashback", "sale", "offer", "voucher", "deal", "limited time",
            "flat 50%", "loan", "promo", "promo code", "recharge", "credit card"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        // Ignore self notifications
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val combined = "$title $text"
        if (combined.isBlank()) return

        val prefs = applicationContext.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val otpReaderEnabled = prefs.getBoolean("otp_reader_enabled", true)
        val notifReaderEnabled = prefs.getBoolean("notif_reader_enabled", true)

        // 1. Auto OTP Detection
        if (otpReaderEnabled && (combined.contains("OTP", ignoreCase = true) || combined.contains("verification code", ignoreCase = true))) {
            val otpPattern = Regex("\\b\\d{4,8}\\b")
            val match = otpPattern.find(combined)
            if (match != null) {
                val otp = match.value
                Log.d(TAG, "Auto-detected OTP: $otp from $packageName")
                val otpIntent = Intent(ACTION_OTP_DETECTED).apply {
                    setPackage(applicationContext.packageName)
                    putExtra(EXTRA_OTP_CODE, otp)
                    putExtra(EXTRA_NOTIF_TITLE, title)
                }
                sendBroadcast(otpIntent)
                return
            }
        }

        // 2. Notification Intelligence & Spam Filter
        if (!notifReaderEnabled) return

        // Skip spam notifications
        val isSpam = SPAM_KEYWORDS.any { combined.contains(it, ignoreCase = true) }
        if (isSpam) {
            Log.d(TAG, "Filtered spam notification from $packageName: $title")
            return
        }

        // Forward important notification
        val notifIntent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
            setPackage(applicationContext.packageName)
            putExtra(EXTRA_NOTIF_PACKAGE, packageName)
            putExtra(EXTRA_NOTIF_TITLE, title)
            putExtra(EXTRA_NOTIF_TEXT, text)
        }
        sendBroadcast(notifIntent)
    }
}
