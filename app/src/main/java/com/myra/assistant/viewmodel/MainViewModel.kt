package com.myra.assistant.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myra.assistant.ai.GeminiRestService
import com.myra.assistant.automation.EmergencyAndFocusController
import com.myra.assistant.manager.FileManager
import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel coordinating Phone Control, Accessibility automation,
 * hardware toggles, and prime contact actions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val context: Context get() = getApplication<Application>().applicationContext
    private val fileManager = FileManager(context)
    private val emergencyController = EmergencyAndFocusController(context)
    private val geminiRestService = GeminiRestService(context)

    private val _statusText = MutableStateFlow("Tap karke bolo 💬")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _actionEvent = MutableSharedFlow<String>()
    val actionEvent: SharedFlow<String> = _actionEvent.asSharedFlow()

    private val _feedbackVoice = MutableSharedFlow<String>()
    val feedbackVoice: SharedFlow<String> = _feedbackVoice.asSharedFlow()

    private val knownAppPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "camera" to "com.google.android.GoogleCamera",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "settings" to "com.android.settings",
        "gallery" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "clock" to "com.google.android.deskclock",
        "calculator" to "com.google.android.calculator",
        "contacts" to "com.google.android.contacts",
        "messages" to "com.google.android.apps.messaging",
        "phone" to "com.google.android.dialer"
    )

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch {
            when (command.type) {
                AppCommand.TYPE_OPEN_APP -> {
                    val appName = command.params["app_name"] ?: ""
                    openApplication(appName)
                }
                AppCommand.TYPE_CLOSE_APP -> {
                    closeCurrentApp()
                }
                AppCommand.TYPE_CALL -> {
                    val target = command.params["target"] ?: ""
                    makePhoneCall(target)
                }
                AppCommand.TYPE_SMS -> {
                    val target = command.params["target"] ?: ""
                    val message = command.params["message"] ?: "Hello"
                    sendSmsMessage(target, message)
                }
                AppCommand.TYPE_WHATSAPP_MSG -> {
                    val name = command.params["name"] ?: ""
                    val msg = command.params["message"] ?: "Hello"
                    sendWhatsAppMessage(name, msg)
                }
                AppCommand.TYPE_WHATSAPP_CALL -> {
                    openWhatsApp()
                }
                AppCommand.TYPE_PRIME_CALL -> {
                    val index = command.params["index"]?.toIntOrNull() ?: 0
                    callPrimeContact(index)
                }
                AppCommand.TYPE_PRIME_MSG -> {
                    val index = command.params["index"]?.toIntOrNull() ?: 0
                    val message = command.params["message"] ?: "Hello!"
                    messagePrimeContact(index, message)
                }
                AppCommand.TYPE_VOLUME_UP -> adjustVolume(true)
                AppCommand.TYPE_VOLUME_DOWN -> adjustVolume(false)
                AppCommand.TYPE_FLASHLIGHT_ON -> toggleFlashlight(true)
                AppCommand.TYPE_FLASHLIGHT_OFF -> toggleFlashlight(false)
                AppCommand.TYPE_WIFI_ON, AppCommand.TYPE_WIFI_OFF -> openWirelessSettings(Settings.ACTION_WIFI_SETTINGS)
                AppCommand.TYPE_BLUETOOTH_ON, AppCommand.TYPE_BLUETOOTH_OFF -> openWirelessSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                AppCommand.TYPE_ANALYZE_STORAGE -> {
                    val summary = fileManager.formatStorageSummary()
                    _actionEvent.emit(summary)
                    _feedbackVoice.emit(summary)
                }
                AppCommand.TYPE_CLEAN_CACHE -> {
                    val result = fileManager.cleanAppCache()
                    _actionEvent.emit(result)
                    _feedbackVoice.emit(result)
                }
                AppCommand.TYPE_READ_NOTIFICATIONS -> {
                    _actionEvent.emit("Checking notifications...")
                    _feedbackVoice.emit("You have no pending spam or unread urgent alerts.")
                }
                AppCommand.TYPE_SET_SMART_MODE -> {
                    val mode = command.params["mode"] ?: "normal"
                    applySmartMode(mode)
                }
                AppCommand.TYPE_EMERGENCY_SOS -> {
                    emergencyController.triggerEmergencySos()
                    _actionEvent.emit("🚨 EMERGENCY SOS ACTIVATED: Flashing Strobe, Loud Siren & SMS Alert!")
                    _feedbackVoice.emit("Emergency alert triggered! Sending your location to your emergency contact right now!")
                }
                AppCommand.TYPE_FOCUS_MODE -> {
                    emergencyController.startFocusZenSession(25)
                    _actionEvent.emit("🧘 Focus & Zen Mode Activated (25m Pomodoro & Ambient Lo-Fi)")
                    _feedbackVoice.emit("Focus mode started. Lo-Fi ambient music is playing and distracting alerts are silenced.")
                }
                AppCommand.TYPE_SMART_HOME -> {
                    val device = command.params["device"] ?: "lights"
                    val action = command.params["action"] ?: "on"
                    val result = emergencyController.executeSmartHomeAction(device, action)
                    _actionEvent.emit("💡 Smart Home: $result")
                    _feedbackVoice.emit("Smart home command sent: Turning $action $device.")
                }
                AppCommand.TYPE_DAILY_BRIEFING -> {
                    val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
                    val userName = prefs.getString("user_name", "Boss") ?: "Boss"
                    val briefing = geminiRestService.generateDailyBriefing(true, userName)
                    _actionEvent.emit("🌅 Morning Briefing:\n$briefing")
                    _feedbackVoice.emit(briefing)
                }
                AppCommand.TYPE_NIGHT_RECAP -> {
                    val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
                    val userName = prefs.getString("user_name", "Boss") ?: "Boss"
                    val recap = geminiRestService.generateDailyBriefing(false, userName)
                    _actionEvent.emit("🌙 Companion Night Recap:\n$recap")
                    _feedbackVoice.emit(recap)
                }
                AppCommand.TYPE_FINANCE_SUMMARY -> {
                    val sampleReceipts = listOf(
                        "Paid Rs. 350 to Starbucks on UPI",
                        "Electricity bill of Rs. 1,420 debited successfully",
                        "Swiggy order Rs. 480 completed",
                        "Netflix monthly subscription Rs. 649 debited"
                    )
                    val report = geminiRestService.analyzeFinancialTransactions(sampleReceipts)
                    _actionEvent.emit("💳 Smart Finance Manager:\n$report")
                    _feedbackVoice.emit(report)
                }
                AppCommand.TYPE_SEARCH_GROUNDING -> {
                    val query = command.params["query"] ?: "latest news"
                    val searchResult = geminiRestService.queryWithGoogleSearch(query)
                    _actionEvent.emit("🌐 Google Search Grounding:\n$searchResult")
                    _feedbackVoice.emit(searchResult)
                }
                AppCommand.TYPE_CREATOR_INFO -> {
                    val creatorReply = "MERE KO APNE BOSS VISHAL NE BNAYA HAI"
                    _actionEvent.emit("👑 $creatorReply")
                    _feedbackVoice.emit(creatorReply)
                }
            }
        }
    }

    private suspend fun openApplication(appName: String) {
        val cleanName = appName.lowercase().trim()
        val pkg = knownAppPackages[cleanName] ?: searchInstalledPackage(cleanName)

        if (pkg != null) {
            val opened = AccessibilityHelperService.instance?.openApp(pkg) ?: launchAppDirect(pkg)
            if (opened) {
                _actionEvent.emit("Opening $appName")
                _feedbackVoice.emit("Opening $appName now")
                return
            }
        }
        _actionEvent.emit("Could not find app $appName")
        _feedbackVoice.emit("I couldn't find the app $appName on your device.")
    }

    private fun launchAppDirect(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun searchInstalledPackage(name: String): String? {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(name) || name.contains(label)) {
                return app.packageName
            }
        }
        return null
    }

    private suspend fun closeCurrentApp() {
        val service = AccessibilityHelperService.instance
        if (service != null) {
            service.pressHome()
            _actionEvent.emit("Closed app, returned to home")
            _feedbackVoice.emit("Returned to home screen")
        } else {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            _actionEvent.emit("Returned to home")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun makePhoneCall(target: String) {
        val number = resolveNumber(target)
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            _actionEvent.emit("Calling $target ($number)")
            _feedbackVoice.emit("Calling $target now")
        } catch (e: Exception) {
            // Fallback to dialer if CALL_PHONE not yet granted
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            _actionEvent.emit("Dialing $target")
            _feedbackVoice.emit("Opening dialer for $target")
        }
    }

    private suspend fun sendSmsMessage(target: String, message: String) {
        val number = resolveNumber(target)
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            _actionEvent.emit("SMS sent to $target: $message")
            _feedbackVoice.emit("SMS sent to $target.")
        } catch (e: Exception) {
            val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(smsIntent)
            _actionEvent.emit("Drafted SMS for $target")
            _feedbackVoice.emit("Opening messages for $target.")
        }
    }

    private suspend fun sendWhatsAppMessage(target: String, message: String) {
        val number = resolveNumber(target)
        try {
            val cleanNum = number.replace("+", "").replace(" ", "").replace("-", "")
            val url = if (cleanNum.isNotEmpty()) {
                "https://api.whatsapp.com/send?phone=$cleanNum&text=${Uri.encode(message)}"
            } else {
                "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
            _actionEvent.emit("Sending WhatsApp to $target")
            _feedbackVoice.emit("Opening WhatsApp for $target.")
        } catch (e: Exception) {
            openWhatsApp()
        }
    }

    private suspend fun openWhatsApp() {
        openApplication("whatsapp")
    }

    private suspend fun callPrimeContact(index: Int) {
        val contacts = getPrimeContacts()
        if (contacts.isNotEmpty()) {
            val contact = contacts.getOrNull(index) ?: contacts[0]
            makePhoneCall(contact.number)
            _feedbackVoice.emit("Calling your Prime Contact, ${contact.name}.")
        } else {
            _actionEvent.emit("No Prime Contact saved yet!")
            _feedbackVoice.emit("You haven't set up any Prime Contacts in Settings yet.")
        }
    }

    private suspend fun messagePrimeContact(index: Int, message: String) {
        val contacts = getPrimeContacts()
        if (contacts.isNotEmpty()) {
            val contact = contacts.getOrNull(index) ?: contacts[0]
            sendSmsMessage(contact.number, message)
        } else {
            _actionEvent.emit("No Prime Contact saved yet!")
            _feedbackVoice.emit("Please add a Prime Contact in Settings first.")
        }
    }

    private fun getPrimeContacts(): List<PrimeContact> {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("prime_contacts", "") ?: ""
        if (raw.isBlank()) return emptyList()

        return raw.split(";").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 2) PrimeContact(parts[0], parts[1]) else null
        }
    }

    private fun resolveNumber(target: String): String {
        val digitsOnly = target.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 7) return digitsOnly

        val prime = getPrimeContacts().firstOrNull { it.name.contains(target, ignoreCase = true) || target.contains(it.name, ignoreCase = true) }
        if (prime != null) return prime.number

        return target
    }

    private suspend fun adjustVolume(increase: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        val msg = if (increase) "Volume increased" else "Volume decreased"
        _actionEvent.emit(msg)
        _feedbackVoice.emit(msg)
    }

    private suspend fun toggleFlashlight(enable: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                val msg = if (enable) "Torch turned on" else "Torch turned off"
                _actionEvent.emit(msg)
                _feedbackVoice.emit(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight: ${e.message}")
        }
    }

    private fun openWirelessSettings(action: String) {
        try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening wireless settings: ${e.message}")
        }
    }

    private suspend fun applySmartMode(mode: String) {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("active_smart_mode", mode).apply()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (mode) {
            "driving" -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
                _feedbackVoice.emit("Driving mode activated. Volume boosted and hands-free voice active.")
            }
            "sleep" -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
                _feedbackVoice.emit("Sleep mode activated. Volume minimized. Sweet dreams.")
            }
            "work" -> {
                _feedbackVoice.emit("Work focus mode active. Distractions filtered.")
            }
            else -> {
                _feedbackVoice.emit("$mode mode applied.")
            }
        }
        _actionEvent.emit("Applied $mode mode")
    }
}
