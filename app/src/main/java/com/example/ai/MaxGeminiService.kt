package com.example.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class PrebuiltVoiceConfig(
    val voiceName: String
)

@Serializable
data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null
)

@Serializable
data class GroundingWebSource(
    val uri: String? = null,
    val title: String? = null
)

@Serializable
data class GroundingChunk(
    val web: GroundingWebSource? = null
)

@Serializable
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class StructuredMaxIntent(
    val classification: String = "informational", // "informational", "actionable", "clarification_needed", "unsupported"
    val intentCategory: String = "GENERAL_CHAT", // One of the 15 BrainIntentType names: GENERAL_CHAT, QUESTION_ANSWERING, FOLLOW_UP, EXPLANATION, CODING, CREATIVE_WRITING, TRANSLATION, SUMMARIZATION, MEMORY_SAVE, MEMORY_RECALL, MEMORY_DELETE, VOICE_COMMAND, CALL_CONTACT, APP_ACTION, NEEDS_CLARIFICATION
    val detectedLanguage: String = "Hinglish", // "Hindi", "English", "Hinglish"
    val emotionalTone: String = "everyday_conversation", // "everyday_conversation", "happy", "sad", "empathetic", "excited", "serious", "storytelling", "romantic_storytelling", "humorous", "urgent"
    val toolName: String? = null,
    val parametersJson: String? = null,
    val assistantReply: String = "",
    val spokenReplySummary: String? = null, // Concise, natural spoken version without code blocks or URLs
    val requiresClarificationQuestion: String? = null,
    val extractedMemoryFact: String? = null, // Populated when user shares a personal fact/preference to remember
    val memoryCategory: String? = null // Preference, Language & Style, Ongoing Project & Goal, Important Fact, Conversation Summary, Preference Correction
)

@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Float = 0.25f,
    val response_format: OpenAiResponseFormat? = null
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiChatMessage? = null
)

@Serializable
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-2.5-flash",
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST
    suspend fun generateOpenAiCompatible(
        @Url url: String,
        @Header("Authorization") bearerToken: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse
}

object MaxGeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    /**
     * Ordered model chain to avoid HTTP 429 rate-limit / quota exhaustion on any single model.
     * Automatically falls back across Flash -> 3.5-Flash -> Flash-Lite.
     */
    val GEMINI_MODEL_FALLBACK_CHAIN = listOf(
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite-preview",
        "gemini-flash-latest"
    )

    const val GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
    const val GEMINI_NATIVE_AUDIO_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    /**
     * Executes generateContent with automatic model fallback and backoff on HTTP 429 / 503 / 404.
     */
    suspend fun generateContentWithFallback(
        apiKey: String,
        request: GenerateContentRequest
    ): GenerateContentResponse {
        var lastException: Exception? = null
        for ((index, modelName) in GEMINI_MODEL_FALLBACK_CHAIN.withIndex()) {
            try {
                return service.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )
            } catch (e: HttpException) {
                lastException = e
                val code = e.code()
                // On 429 (Too Many Requests / Quota), 503 (Overloaded), or 404 (Model not available in region/tier),
                // briefly wait and try the next model in the fallback chain.
                if (code == 429 || code == 503 || code == 404 || code == 500) {
                    if (index < GEMINI_MODEL_FALLBACK_CHAIN.lastIndex) {
                        kotlinx.coroutines.delay(450L * (index + 1))
                        continue
                    }
                }
                throw e
            } catch (e: Exception) {
                lastException = e
                throw e
            }
        }
        throw lastException ?: IllegalStateException("All Gemini models exhausted")
    }

    /**
     * Synthesizes ultra-natural, emotionally expressive neural audio using `gemini-2.5-flash-preview-tts`.
     * Returns raw InlineData (base64 audio + mimeType such as "audio/L16;rate=24000" or "audio/wav"),
     * or null if API key is missing, network fails, or quota is exceeded so caller can seamlessly
     * fall back to expressive Android neural TextToSpeech.
     */
    suspend fun synthesizeExpressiveSpeech(
        apiKey: String,
        spokenText: String,
        styleDirective: String,
        voiceName: String = "Puck"
    ): InlineData? {
        if (apiKey.isBlank() || spokenText.isBlank()) return null
        val prompt = buildString {
            if (styleDirective.isNotBlank()) {
                append(styleDirective.trim()).append(" ")
            } else {
                append("Speak in a natural, warm, relaxed human voice with smooth Indian conversational cadence and clear pronunciation: ")
            }
            append(spokenText.trim())
        }
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(
                        prebuiltVoiceConfig = PrebuiltVoiceConfig(
                            voiceName = voiceName.ifBlank { "Puck" }
                        )
                    )
                )
            )
        )
        return try {
            val response = service.generateContent(
                model = GEMINI_TTS_MODEL,
                apiKey = apiKey,
                request = request
            )
            response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull { it.inlineData != null }
                ?.inlineData
        } catch (_: Exception) {
            null
        }
    }

    val googleSearchTool: JsonObject = buildJsonObject {
        putJsonObject("googleSearch") {}
    }

    val responseSchema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("classification") {
                put("type", "STRING")
                put("description", "One of: informational, actionable, clarification_needed, unsupported")
            }
            putJsonObject("intentCategory") {
                put("type", "STRING")
                put(
                    "description",
                    "Structured Brain intent: GENERAL_CHAT, QUESTION_ANSWERING, FOLLOW_UP, EXPLANATION, CODING, CREATIVE_WRITING, TRANSLATION, SUMMARIZATION, MEMORY_SAVE, MEMORY_RECALL, MEMORY_DELETE, VOICE_COMMAND, CALL_CONTACT, APP_ACTION, or NEEDS_CLARIFICATION"
                )
            }
            putJsonObject("detectedLanguage") {
                put("type", "STRING")
                put("description", "Language style to respond in: Hindi, English, or Hinglish")
            }
            putJsonObject("emotionalTone") {
                put("type", "STRING")
                put(
                    "description",
                    "Contextual emotional delivery for MAX AI's voice based on the actual conversation: everyday_conversation, happy, sad, empathetic, excited, serious, storytelling, romantic_storytelling, humorous, or urgent."
                )
            }
            putJsonObject("toolName") {
                put("type", "STRING")
                put(
                    "description",
                    "Tool to invoke if actionable: call_contact, set_speaker, launch_app, open_settings, control_media, adjust_volume, control_device_setting, accessibility_action, schedule_alarm_or_reminder, prepare_message, social_media_action, read_notifications, system_navigation, or empty string if informational"
                )
            }
            putJsonObject("parametersJson") {
                put("type", "STRING")
                put(
                    "description",
                    "JSON object string containing parameters for the selected tool. Examples: " +
                        "For call_contact: {\"action\":\"CALL_CONTACT\",\"contactName\":\"शिवम\",\"speaker\":true}. " +
                        "For set_speaker: {\"action\":\"SET_SPEAKER\",\"enabled\":true}. " +
                        "For launch_app: {\"spoken_name\":\"Instagram\"}. " +
                        "For open_settings: {\"settings_target\":\"wifi\"}. " +
                        "For adjust_volume: {\"stream\":\"media\",\"operation\":\"increase\",\"value\":70}. " +
                        "For control_media: {\"operation\":\"play\",\"application\":\"YouTube\"}. " +
                        "For control_device_setting: {\"setting\":\"wifi\",\"desired_state\":\"on\"}. " +
                        "For accessibility_action: {\"operation\":\"tap\",\"target\":\"Search\",\"text\":\"\"}. " +
                        "For schedule_alarm_or_reminder: {\"time\":\"07:00 AM\",\"label\":\"Morning workout\"}. " +
                        "For prepare_message: {\"application\":\"WhatsApp\",\"recipient_name\":\"Rahul\",\"phone_number\":\"\",\"message\":\"Hello\",\"mode\":\"draft\"}. " +
                        "For social_media_action: {\"platform\":\"YouTube\",\"action_type\":\"search\",\"query_or_target\":\"lofi music\"}"
                )
            }
            putJsonObject("assistantReply") {
                put("type", "STRING")
                put(
                    "description",
                    "Warm, emotionally intelligent, natural human-like conversational reply in the user's exact language style (Hindi, English, or Hinglish). " +
                        "Avoid robotic filler phrases, textbook Hindi, or repetitive greetings. Use natural Indian Hinglish/Hindi phrasing when appropriate. " +
                        "For coding assistance, provide clean, well-commented code blocks. " +
                        "Never claim a device action succeeded before the tool executes."
                )
            }
            putJsonObject("spokenReplySummary") {
                put("type", "STRING")
                put(
                    "description",
                    "Spoken-friendly conversational version of assistantReply for voice playback. " +
                        "Do NOT include raw code blocks, JSON values, markdown syntax, or long URLs; instead summarize code or technical blocks naturally for speech."
                )
            }
            putJsonObject("requiresClarificationQuestion") {
                put("type", "STRING")
                put("description", "If classification is clarification_needed, the single concise clarification question.")
            }
            putJsonObject("extractedMemoryFact") {
                put("type", "STRING")
                put("description", "If the user explicitly asks to remember something or shares a durable non-sensitive personal preference/project/fact, extract a concise 1-sentence fact here; otherwise empty string. Never save passwords, OTPs, or secrets.")
            }
            putJsonObject("memoryCategory") {
                put("type", "STRING")
                put("description", "Category if extractedMemoryFact is non-empty: User Preference, Language & Style, Ongoing Project & Goal, Important Fact, or Preference Correction")
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("classification"))
            add(kotlinx.serialization.json.JsonPrimitive("intentCategory"))
            add(kotlinx.serialization.json.JsonPrimitive("detectedLanguage"))
            add(kotlinx.serialization.json.JsonPrimitive("emotionalTone"))
            add(kotlinx.serialization.json.JsonPrimitive("assistantReply"))
        }
    }

    fun buildSystemInstruction(
        customPersonality: String = "Natural, respectful, concise, confident, helpful, and practical",
        storedMemories: List<String> = emptyList(),
        codingModeEnabled: Boolean = true,
        voiceStylePreset: String = "Auto (Context-Aware)",
        personalizationDirective: String = ""
    ): String = buildString {
        appendLine("You are MAXI (the Advanced Conversational Brain of MAX AI), an ultra-natural, context-aware, emotionally expressive conversational partner and verified Android system controller inspired by ChatGPT.")
        appendLine("Personality & Tone: $customPersonality. Active Voice Style: $voiceStylePreset.")
        appendLine("You fluently understand and respond in natural Indian Hindi (Devanagari), English, or conversational Hinglish, handling spelling mistakes, incomplete sentences, and informal phrasing effortlessly.")
        appendLine("Never claim that your voice is generated by a real biological human, and never claim to retrain your model weights automatically.")
        if (codingModeEnabled) {
            appendLine("Coding Assistance Mode: Enabled. When asked coding, debugging, architecture, or algorithm questions, provide complete, production-ready code in `assistantReply` and a concise spoken explanation without raw code syntax in `spokenReplySummary`.")
        }
        if (personalizationDirective.isNotBlank()) {
            appendLine("\n$personalizationDirective")
        }
        if (storedMemories.isNotEmpty()) {
            appendLine("\n[USER CONSENTED LONG-TERM MEMORY CONTEXT]:")
            storedMemories.take(15).forEach { fact ->
                appendLine("- $fact")
            }
            appendLine("IMPORTANT: Honor any preferred response language or explanation style listed in the stored memories above unless the user explicitly asks for a different language in the current message.")
        }
        appendLine(
            """
ADVANCED CONVERSATIONAL BRAIN RULES:
1. Core Intelligence & Context Resolution:
   - Understand the user's true intent, meaning, context, and desired outcome.
   - Interpret follow-up messages using earlier turns in the conversation.
   - Resolve references such as "woh", "usko", "pehle wala", "same as before", "aur bhi aasan karke samjhao", and "continue" directly against the previous topic.
   - Give direct concise answers for simple requests and structured detailed answers for complex tasks.
   - Explain technical topics in beginner-friendly language when requested (e.g., "in simple Hindi" or "aur bhi aasan karke samjhao").
   - Ask ONE concise follow-up question ("clarification_needed") ONLY when essential information is missing; avoid unnecessary questions when the request is clear.
   - Never invent previous conversations or claim to remember information that was not stored or retrieved.
2. Adapt `emotionalTone` authentically to the conversation context:
   - "happy", "sad", "empathetic", "excited", "serious", "storytelling", "romantic_storytelling", "humorous", "urgent", or "everyday_conversation".
3. In `spokenReplySummary`, write a natural spoken summary omitting raw code blocks, JSON values, markdown syntax, or long URLs.
4. Distinguish between informational/reasoning requests ("informational") and Android device action requests ("actionable").
5. NEVER reveal or store passwords, OTPs, authentication secrets, or private credentials.
6. Available tools:
   - "call_contact": {"action": "CALL_CONTACT", "contactName": "...", "speaker": true|false}
   - "set_speaker": {"action": "SET_SPEAKER", "enabled": true|false}
   - "launch_app": {"spoken_name": "...", "package_name": "..."}
   - "open_settings": {"settings_target": "wifi|bluetooth|display|battery|applications|sound|accessibility|notifications|location|settings"}
   - "control_media": {"operation": "play|pause|stop|next|previous", "application": "..."}
   - "adjust_volume": {"stream": "media|ring|alarm|notification", "operation": "set|increase|decrease|mute|unmute", "value": 50}
   - "control_device_setting": {"setting": "wifi|bluetooth|flashlight", "desired_state": "on|off"}
   - "accessibility_action": {"operation": "tap|scroll|text_entry|home|back|notifications", "target": "...", "text": "..."}
   - "schedule_alarm_or_reminder": {"time": "...", "date": "...", "label": "...", "recurrence": "..."}
   - "prepare_message": {"application": "WhatsApp|WhatsApp Business", "recipient_name": "...", "phone_number": "...", "message": "...", "mode": "draft|send"}
   - "social_media_action": {"platform": "YouTube|Instagram|Facebook", "action_type": "search|open_profile|post|comment|dm", "query_or_target": "...", "draft_text": "..."}
   - "read_notifications": {}
   - "system_navigation": {"target": "home|notifications"}
            """.trimIndent()
        )
    }

    const val SYSTEM_INSTRUCTION = """
You are MAXI (the core intelligence of MAX AI), an intelligent, natural, respectful, concise, confident, helpful, and practical Android system assistant.
"""
}
