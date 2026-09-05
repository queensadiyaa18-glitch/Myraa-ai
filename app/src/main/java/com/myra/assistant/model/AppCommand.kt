package com.myra.assistant.model

/**
 * Parsed application command from voice input.
 */
data class AppCommand(
    val type: String,
    val params: Map<String, String> = emptyMap()
) {
    companion object {
        const val TYPE_OPEN_APP = "OPEN_APP"
        const val TYPE_CLOSE_APP = "CLOSE_APP"
        const val TYPE_CALL = "CALL"
        const val TYPE_SMS = "SMS"
        const val TYPE_WHATSAPP_MSG = "WHATSAPP_MSG"
        const val TYPE_WHATSAPP_CALL = "WHATSAPP_CALL"
        const val TYPE_PRIME_CALL = "PRIME_CALL"
        const val TYPE_PRIME_MSG = "PRIME_MSG"
        const val TYPE_VOLUME_UP = "VOLUME_UP"
        const val TYPE_VOLUME_DOWN = "VOLUME_DOWN"
        const val TYPE_FLASHLIGHT_ON = "FLASHLIGHT_ON"
        const val TYPE_FLASHLIGHT_OFF = "FLASHLIGHT_OFF"
        const val TYPE_WIFI_ON = "WIFI_ON"
        const val TYPE_WIFI_OFF = "WIFI_OFF"
        const val TYPE_BLUETOOTH_ON = "BLUETOOTH_ON"
        const val TYPE_BLUETOOTH_OFF = "BLUETOOTH_OFF"
        const val TYPE_ANALYZE_STORAGE = "ANALYZE_STORAGE"
        const val TYPE_CLEAN_CACHE = "CLEAN_CACHE"
        const val TYPE_READ_NOTIFICATIONS = "READ_NOTIFICATIONS"
        const val TYPE_START_MISSION = "START_MISSION"
        const val TYPE_SET_SMART_MODE = "SET_SMART_MODE"
        const val TYPE_EMERGENCY_SOS = "EMERGENCY_SOS"
        const val TYPE_FOCUS_MODE = "FOCUS_MODE"
        const val TYPE_SMART_HOME = "SMART_HOME"
        const val TYPE_DAILY_BRIEFING = "DAILY_BRIEFING"
        const val TYPE_NIGHT_RECAP = "NIGHT_RECAP"
        const val TYPE_FINANCE_SUMMARY = "FINANCE_SUMMARY"
        const val TYPE_SEARCH_GROUNDING = "SEARCH_GROUNDING"
        const val TYPE_CREATOR_INFO = "CREATOR_INFO"
    }
}

/**
 * Prime contact data structure stored in SharedPreferences.
 */
data class PrimeContact(
    val name: String,
    val number: String
)
