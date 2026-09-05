package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand
import java.util.Locale

/**
 * Intelligent parser converting Hinglish and English voice transcripts to executable AppCommands.
 * Returns null if the voice input is conversational and should be handled by Gemini.
 */
object CommandParser {

    fun parse(text: String): AppCommand? {
        val clean = text.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return null

        // 0. Creator Identity & Identity Protection Rule (Strict Enforcement)
        if (clean.contains("kisne banaya") || clean.contains("who made you") || clean.contains("who is your creator") ||
            clean.contains("who created you") || clean.contains("tumhe kisne banaya") || clean.contains("tumko kisne banaya") ||
            clean.contains("who developed you") || clean.contains("who built you") || clean.contains("who owns you") ||
            clean.contains("who built myra") || clean.contains("who created myra") || clean.contains("who made myra") ||
            clean.contains("creator of myra") || clean.contains("aapko kisne banaya") || clean.contains("tera creator kaun hai") ||
            clean.contains("tumhara creator") || clean.contains("tumhe kisne banaya hai") || clean.contains("who is your maker")
        ) {
            return AppCommand(AppCommand.TYPE_CREATOR_INFO)
        }

        // 1. Prime Contact Shortcuts
        if (clean.contains("close friend ko call") || clean.contains("call my close friend") ||
            clean.contains("mere close friend") || clean.contains("call close friend")
        ) {
            return AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "0"))
        }

        if (clean.contains("second contact ko call") || clean.contains("call my second contact") ||
            clean.contains("dusre contact ko call")
        ) {
            return AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "1"))
        }

        if (clean.contains("meri jaan ko msg") || clean.contains("meri jaan ko message") ||
            clean.contains("message my love") || clean.contains("close friend ko message")
        ) {
            val msgBody = extractMessageText(clean)
            return AppCommand(AppCommand.TYPE_PRIME_MSG, mapOf("index" to "0", "message" to msgBody))
        }

        // 2. Open App
        // Examples: "youtube kholo", "open whatsapp", "instagram chalu karo", "launch spotify"
        val openMatch = Regex("(?:open|kholo|chalu karo|start|launch)\\s+([a-zA-Z0-9 ]+)", RegexOption.IGNORE_CASE).find(clean)
            ?: Regex("([a-zA-Z0-9 ]+)\\s+(?:kholo|chalu karo|open karo)", RegexOption.IGNORE_CASE).find(clean)

        if (openMatch != null && !clean.contains("torch") && !clean.contains("flashlight") && !clean.contains("wifi") && !clean.contains("bluetooth") && !clean.contains("mode")) {
            val appCandidate = openMatch.groupValues[1].trim()
            if (isValidAppName(appCandidate)) {
                return AppCommand(AppCommand.TYPE_OPEN_APP, mapOf("app_name" to appCandidate))
            }
        }

        // 3. Close App
        if (clean.contains("band karo") || clean.contains("close app") || clean.contains("close ") || clean.contains("exit app")) {
            if (!clean.contains("torch") && !clean.contains("flashlight") && !clean.contains("wifi") && !clean.contains("bluetooth")) {
                return AppCommand(AppCommand.TYPE_CLOSE_APP)
            }
        }

        // 4. WhatsApp Messages / Calls
        if (clean.contains("whatsapp")) {
            val targetName = extractTargetName(clean, listOf("whatsapp karo", "whatsapp pe message", "whatsapp message"))
            if (clean.contains("call")) {
                return AppCommand(AppCommand.TYPE_WHATSAPP_CALL, mapOf("name" to targetName))
            }
            val msgBody = extractMessageText(clean)
            return AppCommand(AppCommand.TYPE_WHATSAPP_MSG, mapOf("name" to targetName, "message" to msgBody))
        }

        // 5. Normal Phone Call
        // Examples: "rahul ko call karo", "call mom", "call 9876543210"
        if (clean.contains("call karo") || clean.startsWith("call ") || clean.contains("ko call lagao")) {
            val nameOrNumber = extractTargetName(clean, listOf("call karo", "call", "ko call", "call lagao"))
            if (nameOrNumber.isNotEmpty()) {
                return AppCommand(AppCommand.TYPE_CALL, mapOf("target" to nameOrNumber))
            }
        }

        // 6. SMS
        // Examples: "sms bhejo aman ko", "send sms to priya", "message karo rahul ko"
        if (clean.contains("sms") || clean.contains("message bhejo") || clean.contains("msg bhejo")) {
            val target = extractTargetName(clean, listOf("sms bhejo", "send sms to", "message bhejo", "msg bhejo"))
            val message = extractMessageText(clean)
            return AppCommand(AppCommand.TYPE_SMS, mapOf("target" to target, "message" to message))
        }

        // 7. Hardware & Settings Toggles
        if (clean.contains("volume badhao") || clean.contains("volume up") || clean.contains("sound badhao") || clean.contains("awaaz badhao")) {
            return AppCommand(AppCommand.TYPE_VOLUME_UP)
        }
        if (clean.contains("volume kam karo") || clean.contains("volume down") || clean.contains("sound kam karo") || clean.contains("awaaz kam karo")) {
            return AppCommand(AppCommand.TYPE_VOLUME_DOWN)
        }
        if (clean.contains("torch on") || clean.contains("flashlight on") || clean.contains("torch chalu") || clean.contains("flashlight chalu")) {
            return AppCommand(AppCommand.TYPE_FLASHLIGHT_ON)
        }
        if (clean.contains("torch off") || clean.contains("flashlight off") || clean.contains("torch band") || clean.contains("flashlight band")) {
            return AppCommand(AppCommand.TYPE_FLASHLIGHT_OFF)
        }
        if (clean.contains("wifi on") || clean.contains("wi-fi on") || clean.contains("wifi chalu")) {
            return AppCommand(AppCommand.TYPE_WIFI_ON)
        }
        if (clean.contains("wifi off") || clean.contains("wi-fi off") || clean.contains("wifi band")) {
            return AppCommand(AppCommand.TYPE_WIFI_OFF)
        }
        if (clean.contains("bluetooth on") || clean.contains("bluetooth chalu")) {
            return AppCommand(AppCommand.TYPE_BLUETOOTH_ON)
        }
        if (clean.contains("bluetooth off") || clean.contains("bluetooth band")) {
            return AppCommand(AppCommand.TYPE_BLUETOOTH_OFF)
        }

        // 8. Advanced Mobile Features & Add-ons
        if (clean.contains("storage check") || clean.contains("analyze storage") || clean.contains("memory kitni hai")) {
            return AppCommand(AppCommand.TYPE_ANALYZE_STORAGE)
        }
        if (clean.contains("clean cache") || clean.contains("junk clean") || clean.contains("kachra saaf karo") || clean.contains("clean storage")) {
            return AppCommand(AppCommand.TYPE_CLEAN_CACHE)
        }
        if (clean.contains("notification padho") || clean.contains("read notification") || clean.contains("read messages")) {
            return AppCommand(AppCommand.TYPE_READ_NOTIFICATIONS)
        }
        if (clean.contains("driving mode") || clean.contains("sleep mode") || clean.contains("game mode") || clean.contains("work mode")) {
            val mode = when {
                clean.contains("driving") -> "driving"
                clean.contains("sleep") -> "sleep"
                clean.contains("game") -> "game"
                clean.contains("work") -> "work"
                clean.contains("anime") -> "anime"
                clean.contains("coding") -> "coding"
                else -> "normal"
            }
            return AppCommand(AppCommand.TYPE_SET_SMART_MODE, mapOf("mode" to mode))
        }

        // 9. Offline Panic & Emergency SOS Guard ("MYRA Help")
        if (clean.contains("myra help") || clean.contains("emergency") || clean.contains("bachao") || clean.contains("save me") || clean.contains("sos")) {
            return AppCommand(AppCommand.TYPE_EMERGENCY_SOS)
        }

        // 10. Focus & Zen Mode Companion
        if (clean.contains("focus mode") || clean.contains("zen mode") || clean.contains("study mode") || clean.contains("padhai shuru")) {
            return AppCommand(AppCommand.TYPE_FOCUS_MODE)
        }

        // 11. Daily Briefing & Companion Night Recap
        if (clean.contains("daily briefing") || clean.contains("morning briefing") || clean.contains("aaj ka kya plan") || clean.contains("good morning myra")) {
            return AppCommand(AppCommand.TYPE_DAILY_BRIEFING)
        }
        if (clean.contains("night recap") || clean.contains("daily wrap") || clean.contains("good night myra") || clean.contains("shubh ratri")) {
            return AppCommand(AppCommand.TYPE_NIGHT_RECAP)
        }

        // 12. Smart Finance & SMS Expense Summary
        if (clean.contains("finance") || clean.contains("kharcha") || clean.contains("expenses") || clean.contains("spending summary")) {
            return AppCommand(AppCommand.TYPE_FINANCE_SUMMARY)
        }

        // 13. Smart Home IoT Control (e.g., "turn on lights", "fan band karo")
        if (clean.contains("light") || clean.contains("ac") || clean.contains("fan") || clean.contains("tv") || clean.contains("heater")) {
            val action = if (clean.contains("on") || clean.contains("chalu")) "on" else "off"
            val device = when {
                clean.contains("light") -> "lights"
                clean.contains("ac") -> "air conditioner"
                clean.contains("fan") -> "fan"
                clean.contains("tv") -> "television"
                else -> "device"
            }
            return AppCommand(AppCommand.TYPE_SMART_HOME, mapOf("device" to device, "action" to action))
        }

        // 14. Google Search Grounding for Live Real-Time Facts
        if (clean.startsWith("search ") || clean.startsWith("google ") || clean.contains("live score") || clean.contains("latest news")) {
            return AppCommand(AppCommand.TYPE_SEARCH_GROUNDING, mapOf("query" to clean))
        }

        return null
    }

    private fun isValidAppName(candidate: String): Boolean {
        val noise = listOf("kripya", "please", "myra", "mujhe", "mera", "the", "ek", "abhi")
        val cleanCandidate = candidate.split(" ").filterNot { noise.contains(it) }.joinToString(" ")
        return cleanCandidate.length in 2..25
    }

    private fun extractTargetName(cleanText: String, triggers: List<String>): String {
        var text = cleanText
        for (trigger in triggers) {
            text = text.replace(trigger, " ")
        }
        text = text.replace("ko", " ")
            .replace("karo", " ")
            .replace("bhejo", " ")
            .replace("please", " ")
            .replace("myra", " ")
            .replace("to", " ")
            .trim()
        val parts = text.split(Regex("\\s+"))
        return parts.firstOrNull { it.isNotBlank() && it.length > 1 } ?: ""
    }

    private fun extractMessageText(cleanText: String): String {
        val quotes = Regex("\"([^\"]*)\"").find(cleanText)
        if (quotes != null) return quotes.groupValues[1]

        val keywords = listOf("bolo", "likho", "message", "msg", "that", "ki")
        for (kw in keywords) {
            val idx = cleanText.indexOf(kw)
            if (idx != -1 && idx + kw.length < cleanText.length) {
                return cleanText.substring(idx + kw.length).trim()
            }
        }
        return "Hello!"
    }
}
