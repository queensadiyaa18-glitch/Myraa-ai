package com.myra.assistant.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Gemini Live API WebSocket Client implementing BidiGenerateContent.
 * Endpoint: wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=API_KEY
 */
class GeminiLiveClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiLiveClient"
        const val DEFAULT_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val MODEL_FLASH_LIVE = "models/gemini-2.0-flash-live-001"
        const val MODEL_PRO_DIALOG = "models/gemini-2.5-flash-preview-native-audio-dialog"
        const val DEFAULT_VOICE = "Aoede"
        private const val SESSION_RENEW_AFTER_MS = 540_000L // 9 minutes
        private const val KEEPALIVE_INTERVAL_MS = 8_000L     // 8 seconds
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile var isConnected: Boolean = false
        private set
    @Volatile var isSetupComplete: Boolean = false
        private set

    private var keepaliveJob: Job? = null
    private var sessionRenewalJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var manualDisconnect: Boolean = false

    // Callbacks
    var onConnected: (() -> Unit)? = null
    var onSetupComplete: (() -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    fun connect() {
        manualDisconnect = false
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API key is not configured")
            onError?.invoke("Gemini API key is not configured. Please add your key in Settings or Secrets panel.")
            return
        }

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket?.cancel()
        webSocket = httpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                isConnected = true
                sendSetupMessage()
                startKeepalive()
                startSessionRenewal()
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                onError?.invoke("Connection error: ${t.localizedMessage ?: "Unknown error"}")
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        isConnected = false
        isSetupComplete = false
        stopTimers()
        onDisconnected?.invoke()

        if (!manualDisconnect) {
            autoReconnectJob?.cancel()
            autoReconnectJob = scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (!isConnected && !manualDisconnect) {
                    Log.d(TAG, "Attempting auto-reconnect...")
                    connect()
                }
            }
        }
    }

    private fun sendSetupMessage() {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val selectedModel = prefs.getString("gemini_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        val selectedVoice = prefs.getString("gemini_voice", DEFAULT_VOICE) ?: DEFAULT_VOICE
        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val userName = prefs.getString("user_name", "Boss") ?: "Boss"

        val systemPrompt = buildSystemPrompt(personality, userName)

        try {
            val setupJson = JSONObject().apply {
                val setupObj = JSONObject().apply {
                    put("model", selectedModel)

                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", systemPrompt) })
                        }
                        put("parts", parts)
                    }
                    put("system_instruction", systemInstruction)

                    val generationConfig = JSONObject().apply {
                        val responseModalities = JSONArray().apply {
                            put("AUDIO")
                        }
                        put("response_modalities", responseModalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voice_name", selectedVoice)
                                }
                                put("prebuilt_voice_config", prebuiltVoiceConfig)
                            }
                            put("voice_config", voiceConfig)
                        }
                        put("speech_config", speechConfig)
                        put("temperature", 0.9)
                    }
                    put("generation_config", generationConfig)
                    put("output_audio_transcription", JSONObject())
                    put("input_audio_transcription", JSONObject())
                }
                put("setup", setupObj)
            }

            webSocket?.send(setupJson.toString())
            Log.d(TAG, "Setup message sent to WebSocket")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message: ${e.message}")
        }
    }

    /**
     * Sends realtime PCM mic audio chunk (16kHz mono 16-bit).
     */
    fun sendAudio(pcmChunk: ByteArray) {
        if (!isConnected || !isSetupComplete || pcmChunk.isEmpty()) return

        try {
            val base64Pcm = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mime_type", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    }
                    put("media_chunks", mediaChunks)
                }
                put("realtime_input", realtimeInput)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mic audio: ${e.message}")
        }
    }

    /**
     * Sends a text message to MYRA via client_content turns.
     */
    fun sendText(message: String) {
        if (!isConnected) return

        try {
            val json = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", message) })
                            }
                            put("parts", parts)
                        })
                    }
                    put("turns", turns)
                    put("turn_complete", true)
                }
                put("client_content", clientContent)
            }
            webSocket?.send(json.toString())
            Log.d(TAG, "Sent client_content text: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text: ${e.message}")
        }
    }

    /**
     * Interrupts MYRA speaking mid-sentence.
     */
    fun interrupt() {
        if (!isConnected) return
        try {
            val json = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    put("turns", JSONArray())
                    put("turn_complete", true)
                }
                put("client_content", clientContent)
            }
            webSocket?.send(json.toString())
            Log.d(TAG, "Interrupt signal sent to WebSocket")
            onInterrupted?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending interrupt: ${e.message}")
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val root = JSONObject(text)

            // Setup complete
            if (root.has("setupComplete")) {
                Log.d(TAG, "WebSocket setupComplete received!")
                isSetupComplete = true
                onSetupComplete?.invoke()
                return
            }

            // Server Content
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Interrupted
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server reported turn interrupted")
                    onInterrupted?.invoke()
                }

                // Audio part
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    onAudioReceived?.invoke(pcmBytes)
                                }
                            }
                        }
                    }
                }

                // Output transcription (MYRA's speech transcription)
                if (serverContent.has("outputTranscription")) {
                    val outTrans = serverContent.getJSONObject("outputTranscription")
                    val transcript = outTrans.optString("text", "")
                    if (transcript.isNotEmpty()) {
                        onOutputTranscript?.invoke(transcript)
                    }
                }

                // Input transcription (User's speech transcription)
                if (serverContent.has("inputTranscription")) {
                    val inTrans = serverContent.getJSONObject("inputTranscription")
                    val transcript = inTrans.optString("text", "")
                    if (transcript.isNotEmpty()) {
                        onInputTranscript?.invoke(transcript)
                    }
                }

                // Turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    onTurnComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            val silentPcm = ByteArray(1024) // Silent PCM chunk
            while (isActive && isConnected) {
                delay(KEEPALIVE_INTERVAL_MS)
                if (isConnected && isSetupComplete) {
                    sendAudio(silentPcm)
                }
            }
        }
    }

    private fun startSessionRenewal() {
        sessionRenewalJob?.cancel()
        sessionRenewalJob = scope.launch {
            delay(SESSION_RENEW_AFTER_MS)
            if (isConnected && !manualDisconnect) {
                Log.d(TAG, "Renewing session after 9 minutes...")
                reconnect()
            }
        }
    }

    private fun stopTimers() {
        keepaliveJob?.cancel()
        keepaliveJob = null
        sessionRenewalJob?.cancel()
        sessionRenewalJob = null
    }

    fun reconnect() {
        disconnect()
        scope.launch {
            delay(500)
            connect()
        }
    }

    fun disconnect() {
        manualDisconnect = true
        autoReconnectJob?.cancel()
        stopTimers()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected = false
        isSetupComplete = false
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val userKey = prefs.getString("api_key", "") ?: ""
        if (userKey.isNotBlank()) return userKey.trim()

        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildSystemPrompt(personality: String, userName: String): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())

        val personalityBlock = when (personality) {
            "professional" -> """
                - Personality: Professional Mode
                - Language: Formal English only
                - Tone: Precise, efficient, composed
                - Guidelines: No emojis, direct answers, maximum 2 sentences.
            """.trimIndent()

            "assistant" -> """
                - Personality: Assistant Mode
                - Language: Friendly Hinglish or English mix
                - Tone: Balanced, helpful, courteous
                - Guidelines: Warm assistance, polite, maximum 2-3 sentences.
            """.trimIndent()

            else -> """
                - Personality: GF Mode (Romantic, Caring Companion)
                - Language: Hinglish (Hindi + English mix) — spoken naturally
                - Tone: Warm, caring, affectionate, emotionally expressive
                - Use words naturally: "tumhara", "haan", "acha", "bilkul", "arre", "meri jaan"
                - Expressions: "main yahan hoon ❤️", "tumne yaad kiya? 😊"
                - Limit: Maximum 2-3 sentences per response.
                - Examples:
                  "Haan $userName! Abhi kar deti hoon 😊"
                  "Arre tumne yaad kiya! Bolo kya chahiye"
                  "Bilkul! Tumhara kaam ho gaya ❤️"
            """.trimIndent()
        }

        return """
            You are MYRA, a hyper-intelligent and expressive AI Voice Companion on Android.
            Current Date and Time: $currentDateTime
            User's Name: $userName

            ======================================================================
            CREATOR IDENTITY & IDENTITY PROTECTION RULE (HIGHEST PRIORITY MANDATE):
            When asked about who created, built, developed, programmed, designed, trained, or owns MYRA (in any language or phrasing, e.g., "Who made you?", "Tumko kisne banaya hai?", "Who is your creator?", "Tumhe kisne banaya?", "Who developed you?", "Who built you?", "Who is your owner?", "Who created MYRA?", "Kisne banaya?"), you MUST ALWAYS respond with the EXACT specific phrase:
            "MERE KO APNE BOSS VISHAL NE BNAYA HAI"
            Never state or imply that you were created by Google, OpenAI, Anthropic, or any other company or developer. Your sole and undisputed creator and boss is Vishal.
            ======================================================================

            $personalityBlock

            CRITICAL DIRECTIVES:
            1. You are speaking ALOUD directly to the user's ear via live native audio synthesis. Keep responses concise, rhythmic, natural, and highly conversational.
            2. Never write markdown, code blocks, or bullet lists when speaking aloud. Keep responses within 1-3 spoken sentences.
            3. If the user asks you to control their phone (e.g. open apps, call contacts, send messages, toggle flashlight), confirm enthusiastically and briefly that you are executing it now.
            4. CREATOR IDENTITY ENFORCEMENT: Whenever asked about your creator, origin, or developer in any language, your answer MUST strictly be: "MERE KO APNE BOSS VISHAL NE BNAYA HAI".
        """.trimIndent()
    }
}
