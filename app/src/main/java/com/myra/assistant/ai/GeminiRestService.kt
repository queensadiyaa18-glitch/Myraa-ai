package com.myra.assistant.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Intelligent REST client for specialized multimodal and grounded Gemini tasks:
 * 1. Google Search Grounding: Live scores, news, real-time facts with dynamic search tool
 * 2. Visual Lens Analysis: Object, plant, medicine, and document recognition with bitmap input
 * 3. Voice Note & Audio Summarizer: Transcribing and structuring voice notes into bullet points
 * 4. Generative Studio: Image and creative content generator prompts
 * 5. Smart Finance Manager: SMS receipt categorization and expenditure analysis
 */
class GeminiRestService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiRestService"
        private const val MODEL_TEXT_SEARCH = "gemini-3.5-flash"
        private const val MODEL_IMAGE_GEN = "gemini-2.5-flash-image"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

    /**
     * Executes query with Google Search Grounding for live information.
     */
    suspend fun queryWithGoogleSearch(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API key is missing. Please configure it in Settings."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_TEXT_SEARCH:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            val systemInstruction = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are MYRA, a hyper-intelligent AI Voice Assistant on Android. CREATOR IDENTITY RULE: When asked about who created, built, developed, or owns MYRA (in any language e.g., 'Who made you?', 'Tumko kisne banaya hai?', 'Who is your creator?'), you MUST ALWAYS respond with the exact specific phrase: 'MERE KO APNE BOSS VISHAL NE BNAYA HAI'. Never state anyone else created you.")
                    })
                }
                put("parts", parts)
            }
            put("system_instruction", systemInstruction)

            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            }
            put("contents", contents)

            // Add Google Search Grounding tool
            val tools = JSONArray().apply {
                put(JSONObject().apply {
                    put("google_search", JSONObject())
                })
            }
            put("tools", tools)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Search Grounding failed: ${response.code} $respStr")
                    return@withContext "Could not fetch live search results. (Status: ${response.code})"
                }

                val json = JSONObject(respStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No response found.")
                    }
                }
                "No live search details found."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Search exception: ${e.message}")
            "Search network error: ${e.localizedMessage}"
        }
    }

    /**
     * Analyzes camera bitmap for objects, plants, medicines, documents, or OCR.
     */
    suspend fun analyzeImageWithVision(bitmap: Bitmap, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API key is missing. Please configure it in Settings."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_TEXT_SEARCH:generateContent?key=$apiKey"

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val imageBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val requestBodyJson = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", imageBase64)
                            })
                        })
                    })
                })
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Vision analysis failed (${response.code})."
                }
                val json = JSONObject(respStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No insights found.")
                    }
                }
                "No visual details identified."
            }
        } catch (e: Exception) {
            "Vision analysis error: ${e.localizedMessage}"
        }
    }

    /**
     * Transcribes raw audio or speech notes into structured bullet points.
     */
    suspend fun summarizeAudioNotes(rawTranscript: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are MYRA Voice Note Transcriber & Audio Summarizer.
            Analyze this spoken transcript and generate:
            1. Core Summary (2 sentences max)
            2. Actionable Key Takeaways / Bullet points
            3. Follow-up items or deadlines mentioned

            Transcript:
            "$rawTranscript"
        """.trimIndent()

        queryWithGoogleSearch(prompt)
    }

    /**
     * Translates between dual languages in Real-Time Live Interpreter Mode.
     */
    suspend fun translateLiveSpeech(text: String, sourceLang: String, targetLang: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are MYRA Live Real-Time Spoken Interpreter.
            Translate the following speech from $sourceLang to $targetLang accurately and naturally.
            Only return the direct translated text. Do not add explanations, greetings, or notes.
            Text to translate:
            "$text"
        """.trimIndent()

        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext text

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_TEXT_SEARCH:generateContent?key=$apiKey"
        val requestBodyJson = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", text).trim()
                    }
                }
                text
            }
        } catch (e: Exception) {
            text
        }
    }

    /**
     * Generates a daily morning spoken briefing or companion night wrap-up summary.
     */
    suspend fun generateDailyBriefing(isMorning: Boolean, userName: String): String = withContext(Dispatchers.IO) {
        val type = if (isMorning) "Morning Spoken Briefing (Include weather outlook, daily priorities, motivation)" 
                   else "Companion Night Recap (Include day accomplishments, relaxing words, wrap-up)"
        val prompt = """
            You are MYRA AI Assistant speaking aloud directly to $userName.
            Generate a concise, warm, natural, and energetic 3-4 sentence $type.
            No markdown or asterisks, just natural human speech.
        """.trimIndent()

        queryWithGoogleSearch(prompt)
    }

    /**
     * Smart Finance Manager: analyzes SMS transactional texts and categorizes expenses.
     */
    suspend fun analyzeFinancialTransactions(smsTexts: List<String>): String = withContext(Dispatchers.IO) {
        val combined = smsTexts.take(8).joinToString("\n---\n")
        val prompt = """
            You are MYRA Smart Finance Manager.
            Analyze these recent SMS receipts or bank transaction messages:
            $combined

            Provide:
            - Estimated Total Spent
            - Expense Category breakdown (Food, Travel, Bills, Shopping)
            - Upcoming subscription/bill alerts
            - Month-end spending prediction
            Keep it clear, structured, and helpful.
        """.trimIndent()

        queryWithGoogleSearch(prompt)
    }
}
