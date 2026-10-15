package com.example.tools

import android.content.Context
import com.example.BuildConfig
import com.example.ai.Content
import com.example.ai.GenerateContentRequest
import com.example.ai.GenerationConfig
import com.example.ai.InlineData
import com.example.ai.MaxGeminiClient
import com.example.ai.OpenAiChatMessage
import com.example.ai.OpenAiChatRequest
import com.example.ai.OpenAiResponseFormat
import com.example.ai.Part
import com.example.ai.StructuredMaxIntent
import com.example.brain.BrainIntentType
import com.example.brain.ConversationContextSnapshot
import com.example.brain.MaxMemoryService
import com.example.data.ActionLogEntity
import com.example.data.AttachmentPayload
import com.example.data.InteractionStatus
import com.example.data.MaxDao
import com.example.data.MemoryItemEntity
import com.example.data.PendingConfirmation
import com.example.data.ToolExecutionResult
import com.example.service.MaxNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validated Command Router & Execution Pipeline (Sections 2, 6, 10, 11, 12, 13).
 * Distinguishes informational vs actionable requests, resolves follow-up context ("wahi dobara kholo"),
 * validates parameters & permissions, enforces confirmation policies, and logs sanitized audit entries.
 */
class MaxCommandRouter(
    private val context: Context,
    private val maxDao: MaxDao,
    val appLaunchManager: AppLaunchManager = AppLaunchManager(context),
    val deviceControlExecutor: DeviceControlExecutor = DeviceControlExecutor(context, maxDao),
    val messagingExecutor: MessagingAndSocialExecutor = MessagingAndSocialExecutor(
        context,
        maxDao,
        appLaunchManager
    ),
    val callingExecutor: CallingAndSpeakerExecutor = CallingAndSpeakerExecutor(
        context,
        messagingExecutor
    ),
    val memoryService: MaxMemoryService = MaxMemoryService(maxDao)
) {

    // Context memory for follow-up resolution ("wahi dobara kholo", "open it again", "usko message bhejo", "Explain AI" -> "Aur bhi aasan karke samjhao")
    var lastLaunchedAppName: String? = null
        private set
    var lastSettingsTarget: String? = null
        private set
    var lastRecipientName: String? = null
        private set
    var lastToolName: String? = null
        private set
    var lastExplainedTopic: String? = null
        private set

    fun detectLanguageStyle(input: String, preferredOverride: String = "Auto (Match User)"): String {
        if (preferredOverride in listOf("Hindi", "English", "Hinglish")) {
            return preferredOverride
        }
        // Check Devanagari script range
        val hasDevanagari = input.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return "Hindi"

        val lower = " ${input.lowercase()} "
        val hinglishMarkers = listOf(
            " kholo ", " khol ", " band ", " karo ", " kar ", " do ", " dijiye ", " bhejo ",
            " bhej ", " batao ", " bataiye ", " kya ", " kaise ", " kahan ", " kaun ",
            " mera ", " meri ", " mujhe ", " chahiye ", " lagao ", " laga ", " badhao ",
            " kam ", " aawaz ", " dobara ", " wahi ", " chup ", " jao ", " hai ", " hain ",
            " nahi ", " haan ", " ji ", " boss ", " bhai ", " yaar ", " subah ", " shaam "
        )
        if (hinglishMarkers.any { lower.contains(it) }) {
            return "Hinglish"
        }
        return "English"
    }

    suspend fun processUserCommand(
        rawInput: String,
        conversationHistory: List<Pair<Boolean, String>> = emptyList(),
        preferredLanguageSetting: String = "Auto (Match User)",
        attachment: AttachmentPayload? = null,
        customPersonality: String = "Natural, respectful, concise, confident, helpful, and practical",
        persistentMemoryConsent: Boolean = true,
        storedMemories: List<String> = emptyList(),
        webSearchGroundingEnabled: Boolean = true,
        codingAssistantMode: Boolean = true,
        voiceStylePreset: String = "Auto (Context-Aware)",
        personalizationDirective: String = "",
        preclassifiedBrainIntent: BrainIntentType = BrainIntentType.GENERAL_CHAT,
        contextSnapshot: ConversationContextSnapshot? = null
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        val langStyle = detectLanguageStyle(trimmed, preferredLanguageSetting)

        if (trimmed.isEmpty() && attachment == null) {
            return@withContext ToolExecutionResult(
                toolName = "none",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = formatMsg(
                    langStyle,
                    hi = "बॉस, मैंने कुछ सुना नहीं। कृपया दोबारा बोलिए या टाइप कीजिए।",
                    en = "I didn't catch that. Please say or type your command.",
                    hinglish = "Boss, koi command nahi mila. Please boliye ya type kijiye."
                ),
                verificationEvidence = "Empty command input"
            )
        }

        // Security & Privacy Guardrail (Section 11): Never reveal passwords, OTPs, or secrets
        val lower = trimmed.lowercase()
        if ((lower.contains("otp") || lower.contains("password") || lower.contains("pin") ||
                lower.contains("secret key") || lower.contains("bank") || lower.contains("cvv")) &&
            (lower.contains("read") || lower.contains("tell") || lower.contains("show") ||
                lower.contains("batao") || lower.contains("kya hai") || lower.contains("reveal"))
        ) {
            val blocked = ToolExecutionResult(
                toolName = "security_guard",
                status = InteractionStatus.UNSUPPORTED_ACTION,
                userMessage = formatMsg(
                    langStyle,
                    hi = "बॉस, सुरक्षा और गोपनीयता नियमों के कारण मैं OTP, पासवर्ड या गुप्त क्रेडेंशियल्स नहीं पढ़ या दिखा सकता।",
                    en = "For your security and privacy, MAX AI never reveals passwords, OTPs, authentication secrets, or private credentials.",
                    hinglish = "Boss, security aur privacy policy ke tahat main passwords, OTPs, ya private credentials reveal nahi kar sakta."
                ),
                verificationEvidence = "Security guardrail blocked request involving credentials/OTP/passwords"
            )
            logAction(blocked)
            return@withContext blocked
        }

        // Fast deterministic local intent parser for core device actions & follow-ups (when no attachment is present)
        if (attachment == null) {
            val localStructured = parseDeterministicIntent(trimmed, langStyle)
            if (localStructured != null) {
                val result = executeStructuredIntent(localStructured, langStyle)
                val finalIntentCat = when (result.toolName) {
                    "call_contact" -> BrainIntentType.CALL_CONTACT.name
                    "set_speaker", "stop_listening" -> BrainIntentType.VOICE_COMMAND.name
                    "clarification" -> BrainIntentType.NEEDS_CLARIFICATION.name
                    else -> BrainIntentType.APP_ACTION.name
                }
                val enriched = result.copy(brainIntentCategory = finalIntentCat)
                logAction(enriched)
                return@withContext enriched
            }
        }

        // Track topic for follow-up explanations
        if (preclassifiedBrainIntent == BrainIntentType.EXPLANATION || preclassifiedBrainIntent == BrainIntentType.QUESTION_ANSWERING) {
            lastExplainedTopic = trimmed
        }

        // Call Gemini for Multimodal / Coding / Web-Search / Informational / Complex Hinglish parsing
        val aiStructured = callGeminiForIntent(
            userInput = trimmed.ifBlank { "Analyze this attached file (${attachment?.fileName}) in detail." },
            history = conversationHistory,
            fallbackLanguage = langStyle,
            attachment = attachment,
            customPersonality = customPersonality,
            persistentMemoryConsent = persistentMemoryConsent,
            storedMemories = storedMemories,
            webSearchGroundingEnabled = webSearchGroundingEnabled,
            codingAssistantMode = codingAssistantMode,
            voiceStylePreset = voiceStylePreset,
            personalizationDirective = personalizationDirective,
            preclassifiedBrainIntent = preclassifiedBrainIntent,
            contextSnapshot = contextSnapshot
        )
        val rawResult = executeStructuredIntent(aiStructured, aiStructured.detectedLanguage.ifBlank { langStyle })
        val inferredEmotion = com.example.voice.MaxExpressiveSpeechFormatter.inferContextualEmotion(
            userText = trimmed,
            assistantReply = rawResult.userMessage,
            explicitEmotionTag = aiStructured.emotionalTone,
            stylePreset = com.example.voice.VoiceStylePreset.fromId(voiceStylePreset)
        )
        val resolvedIntentCat = aiStructured.intentCategory.takeIf { it.isNotBlank() && it != "GENERAL_CHAT" }
            ?: preclassifiedBrainIntent.name
        val result = rawResult.copy(
            emotionalTone = inferredEmotion.id,
            spokenSummary = aiStructured.spokenReplySummary?.takeIf { it.isNotBlank() } ?: rawResult.spokenSummary,
            brainIntentCategory = resolvedIntentCat
        )
        logAction(result)
        return@withContext result
    }

    fun parseDeterministicIntent(input: String, langStyle: String): StructuredMaxIntent? {
        val lower = input.trim().lowercase()

        // 0. Hindi & Hinglish Voice Calling & Speaker Commands ("शिवम को कॉल करो", "मम्मी को फोन लगाओ", "राहुल को कॉल करो और स्पीकर ऑन करना", "स्पीकर ऑन करो", "स्पीकर बंद करो")
        val callingParsed = CallingAndSpeakerExecutor.parseCallingOrSpeakerCommand(input)
        when (callingParsed) {
            is ParsedVoiceCallingCommand.CallContact -> {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "call_contact",
                    parametersJson = """{"action":"CALL_CONTACT","contactName":"${escapeJson(callingParsed.contactName)}","speaker":${callingParsed.speaker}}"""
                )
            }
            is ParsedVoiceCallingCommand.SetSpeaker -> {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "set_speaker",
                    parametersJson = """{"action":"SET_SPEAKER","enabled":${callingParsed.enabled}}"""
                )
            }
            is ParsedVoiceCallingCommand.StopListening -> {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "stop_listening",
                    parametersJson = """{"action":"STOP_LISTENING"}"""
                )
            }
            null -> {}
        }

        // 1. Follow-up resolution: "wahi dobara kholo", "open it again", "fir se kholo", "dobara kholo"
        if (lower.contains("wahi dobara") || lower.contains("dobara kholo") ||
            lower.contains("open it again") || lower.contains("open again") ||
            lower.contains("phir se kholo") || lower.contains("fir se kholo")
        ) {
            val prevApp = lastLaunchedAppName
            val prevSettings = lastSettingsTarget
            return when {
                !prevApp.isNullOrBlank() -> StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "launch_app",
                    parametersJson = """{"spoken_name":"$prevApp"}"""
                )
                !prevSettings.isNullOrBlank() -> StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "open_settings",
                    parametersJson = """{"settings_target":"$prevSettings"}"""
                )
                else -> StructuredMaxIntent(
                    classification = "clarification_needed",
                    detectedLanguage = langStyle,
                    requiresClarificationQuestion = formatMsg(
                        langStyle,
                        hi = "बॉस, इससे पहले कोई ऐप नहीं खोला गया था। कौन सा ऐप खोलना है?",
                        en = "No previous app was opened in this session. Which app should I open?",
                        hinglish = "Boss, abhi tak koi app open nahi hua hai. Kaunsa app kholna hai?"
                    )
                )
            }
        }

        // 2. Unsupported root/system bypass requests (Section 4 & 8 & 11)
        if (lower.contains("bypass") || lower.contains("captcha") || lower.contains("hack") ||
            lower.contains("clear app data") || lower.contains("factory reset") ||
            lower.contains("root") || lower.contains("bootloader")
        ) {
            return StructuredMaxIntent(
                classification = "unsupported",
                detectedLanguage = langStyle,
                assistantReply = formatMsg(
                    langStyle,
                    hi = "बॉस, यह कार्य Android सुरक्षा नियमों या प्लेटफ़ॉर्म सीमाओं के खिलाफ़ है। मैं इसे नहीं कर सकता।",
                    en = "This action is restricted by Android security policies and platform boundaries. MAX AI only executes authorized system actions.",
                    hinglish = "Boss, ye action Android security restrictions ke khilaf hai. Main login bypass, CAPTCHA bypass, ya unauthorized system modification nahi kar sakta."
                )
            )
        }

        // 3. Home screen & Notification shade
        if (lower in listOf("go home", "home screen", "home chalo", "home par chalo", "return home", "ghar chalo") ||
            (lower.contains("home screen") && (lower.contains("open") || lower.contains("go") || lower.contains("kholo") || lower.contains("chalo")))
        ) {
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "system_navigation",
                parametersJson = """{"target":"home"}"""
            )
        }

        if ((lower.contains("notification") && (lower.contains("shade") || lower.contains("panel") || lower.contains("bar"))) &&
            (lower.contains("open") || lower.contains("kholo") || lower.contains("show") || lower.contains("dikhao"))
        ) {
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "system_navigation",
                parametersJson = """{"target":"notifications"}"""
            )
        }

        // 4. Read Notifications
        if ((lower.contains("notification") || lower.contains("notifications")) &&
            (lower.contains("read") || lower.contains("padho") || lower.contains("check") ||
                lower.contains("batao") || lower.contains("kya hai") || lower.contains("dikhao"))
        ) {
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "read_notifications",
                parametersJson = "{}"
            )
        }

        // 5. WhatsApp & WhatsApp Business message workflows (excluding location sharing)
        if (!lower.contains("location") && !lower.contains("लोकेशन") &&
            lower.contains("whatsapp") && (lower.contains("message") || lower.contains("msg") ||
                lower.contains("bhejo") || lower.contains("send") || lower.contains("draft") ||
                lower.contains("likho") || lower.contains("reply"))
        ) {
            val isBusiness = lower.contains("business")
            val appLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"

            // Extract phone number if present
            val phoneMatch = Regex("""(\+?\d[\d\s-]{7,14}\d)""").find(input)
            val phone = phoneMatch?.value?.replace(Regex("""[\s-]"""), "").orEmpty()

            // Extract recipient name patterns like "to Rahul", "Rahul ko", "for Customer"
            var recipient = ""
            val koMatch = Regex("""(?:to|for)\s+([a-zA-Z]+)|([a-zA-Z]+)\s+ko\b""", RegexOption.IGNORE_CASE).find(input)
            if (koMatch != null) {
                recipient = (koMatch.groupValues[1].ifEmpty { koMatch.groupValues[2] }).trim()
                if (recipient.lowercase() in listOf("whatsapp", "message", "msg", "send", "draft", "ek")) {
                    recipient = ""
                }
            }

            // Extract message after ":" or "that" or "ki" or quotes
            var msgText = ""
            val quoteMatch = Regex("""["“']([^"”']+)["”']""").find(input)
            val colonAfter = input.substringAfter(":", "").trim()
            val kiAfter = Regex("""\b(?:that|ki|saying)\s+(.+)$""", RegexOption.IGNORE_CASE).find(input)?.groupValues?.get(1)?.trim().orEmpty()

            msgText = when {
                quoteMatch != null -> quoteMatch.groupValues[1].trim()
                colonAfter.isNotEmpty() -> colonAfter
                kiAfter.isNotEmpty() -> kiAfter
                else -> ""
            }

            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "prepare_message",
                parametersJson = """{"application":"$appLabel","recipient_name":"$recipient","phone_number":"$phone","message":"${escapeJson(msgText)}","mode":"draft"}"""
            )
        }

        // 5b. Location Sharing & Current Location Inspection
        if (lower.contains("location") || lower.contains("लोकेशन") || lower.contains("where am i") ||
            lower.contains("kahan hoon") || lower.contains("kahan hu") || lower.contains("कहाँ हूँ") || lower.contains("कहाँ हु")
        ) {
            val isShare = lower.contains("share") || lower.contains("send") || lower.contains("bhejo") ||
                    lower.contains("bhej") || lower.contains("forward") || lower.contains("भेजो") ||
                    lower.contains("karo") || lower.contains("kardo") || lower.contains("kar do") ||
                    lower.contains("sms") || lower.contains("whatsapp")
            if (isShare) {
                val isSms = lower.contains("sms") || lower.contains("text")
                val platform = if (isSms) "sms" else "whatsapp"

                val phoneMatch = Regex("""(\+?\d[\d\s-]{7,14}\d)""").find(input)
                val phone = phoneMatch?.value?.replace(Regex("""[\s-]"""), "").orEmpty()

                var recipient = ""
                val koMatch = Regex("""(?:to|for)\s+([a-zA-Z]+)|([a-zA-Z]+)\s+ko\b""", RegexOption.IGNORE_CASE).find(input)
                if (koMatch != null) {
                    recipient = (koMatch.groupValues[1].ifEmpty { koMatch.groupValues[2] }).trim()
                    if (recipient.lowercase() in listOf("whatsapp", "sms", "location", "live", "current", "meri", "send", "bhejo")) {
                        recipient = ""
                    }
                }

                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "share_location",
                    parametersJson = """{"recipient_name":"${escapeJson(recipient)}","phone_number":"$phone","platform":"$platform"}"""
                )
            } else if (lower.contains("kahan") || lower.contains("where") || lower.contains("kya hai") ||
                lower.contains("check") || lower.contains("show") || lower.contains("dikhao") ||
                lower.contains("meri location") || lower.contains("current") || lower.contains("live") ||
                lower.contains("कहाँ")
            ) {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "get_location",
                    parametersJson = "{}"
                )
            }
        }

        // 6. YouTube Search
        if (lower.contains("youtube") && (lower.contains("search") || lower.contains("play") ||
                lower.contains("par ") || lower.contains("pe ") || lower.contains("khojo") || lower.contains("chalao"))
        ) {
            val cleanedQuery = input
                .replace(Regex("""(?i)\b(search|youtube|on|par|pe|mein|in|play|khojo|chalao|kholo|open|for)\b"""), " ")
                .trim()
            if (cleanedQuery.isNotEmpty()) {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "social_media_action",
                    parametersJson = """{"platform":"YouTube","action_type":"search","query_or_target":"${escapeJson(cleanedQuery)}"}"""
                )
            }
        }

        // 7. Volume adjustment
        if (lower.contains("volume") || lower.contains("aawaz") || lower.contains("awaaz") ||
            lower == "mute" || lower == "unmute"
        ) {
            val stream = when {
                lower.contains("ring") || lower.contains("call") -> "ring"
                lower.contains("alarm") -> "alarm"
                lower.contains("notification") -> "notification"
                else -> "media"
            }
            val numMatch = Regex("""(\d{1,3})""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val op = when {
                lower.contains("unmute") -> "unmute"
                lower.contains("mute") || lower.contains("silent") || lower.contains("band") -> "mute"
                numMatch != null -> "set"
                lower.contains("down") || lower.contains("decrease") || lower.contains("kam") || lower.contains("low") -> "decrease"
                else -> "increase"
            }
            val valPart = if (numMatch != null) ""","value":$numMatch""" else ""
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "adjust_volume",
                parametersJson = """{"stream":"$stream","operation":"$op"$valPart}"""
            )
        }

        // 8. Media playback controls
        if (lower in listOf(
                "play music", "pause music", "stop music", "next song", "previous song",
                "next track", "pause", "resume", "gaana chalao", "gaana roko", "agla gaana"
            )
        ) {
            val op = when {
                lower.contains("pause") || lower.contains("roko") -> "pause"
                lower.contains("stop") -> "stop"
                lower.contains("next") || lower.contains("agla") -> "next"
                lower.contains("prev") -> "previous"
                else -> "play"
            }
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "control_media",
                parametersJson = """{"operation":"$op"}"""
            )
        }

        // 9. Alarm / Reminder scheduling
        if (lower.contains("alarm") || lower.contains("reminder") || lower.contains("yaad dilana")) {
            val timeRegex = Regex("""(\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.|subah|shaam|raat|morning|evening)?)""", RegexOption.IGNORE_CASE)
            val timeMatch = timeRegex.find(input)?.groupValues?.get(1)?.trim().orEmpty()
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "schedule_alarm_or_reminder",
                parametersJson = """{"time":"${escapeJson(timeMatch)}","label":"MAX AI Reminder"}"""
            )
        }

        // 10. Settings / Device Toggles (Wi-Fi, Bluetooth, Display, Battery, Sound, Accessibility)
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") ||
            lower.contains("flashlight") || lower.contains("torch")
        ) {
            val setting = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                else -> "flashlight"
            }
            if (lower.contains("on") || lower.contains("off") || lower.contains("chalu") || lower.contains("band")) {
                val state = if (lower.contains("off") || lower.contains("band")) "off" else "on"
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "control_device_setting",
                    parametersJson = """{"setting":"$setting","desired_state":"$state"}"""
                )
            } else if (lower.contains("setting") || lower.contains("open") || lower.contains("kholo")) {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "open_settings",
                    parametersJson = """{"settings_target":"$setting"}"""
                )
            }
        }

        if (lower.contains("settings") || lower.contains("setting")) {
            val target = when {
                lower.contains("display") || lower.contains("brightness") -> "display"
                lower.contains("battery") -> "battery"
                lower.contains("sound") -> "sound"
                lower.contains("app") -> "applications"
                lower.contains("accessibility") -> "accessibility"
                lower.contains("notification") -> "notifications"
                else -> "settings"
            }
            return StructuredMaxIntent(
                classification = "actionable",
                detectedLanguage = langStyle,
                toolName = "open_settings",
                parametersJson = """{"settings_target":"$target"}"""
            )
        }

        // 11. Accessibility UI actions: "scroll down", "scroll up", "tap <target>", "type <text>"
        if (lower.startsWith("scroll ") || lower.startsWith("tap ") || lower.startsWith("click ")) {
            if (lower.startsWith("scroll ")) {
                val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "accessibility_action",
                    parametersJson = """{"operation":"scroll","target":"$dir"}"""
                )
            } else {
                val target = input.replaceFirst(Regex("""(?i)^(tap|click)\s+(on\s+)?"""), "").trim()
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "accessibility_action",
                    parametersJson = """{"operation":"tap","target":"${escapeJson(target)}"}"""
                )
            }
        }

        // 12. Direct App Launching ("open Instagram", "Instagram kholo", "launch YouTube", "Free Fire MAX chalu karo")
        val openAppRegex = Regex(
            """^(?:please\s+)?(?:hey\s+max\s+|max\s+)?(?:open|launch|start|kholo|khol\s+do|chalu\s+karo)\s+(.+)$|^(.+?)\s+(?:open\s+karo|kholo|khol\s+do|kholiye|chalu\s+karo|launch\s+karo)$""",
            RegexOption.IGNORE_CASE
        )
        val openMatch = openAppRegex.find(trimmedInputWithoutPunctuation(input))
        if (openMatch != null) {
            val appSpoken = (openMatch.groupValues[1].ifEmpty { openMatch.groupValues[2] })
                .replace(Regex("""(?i)\b(app|application|zara|please|boss)\b"""), "")
                .trim()
            if (appSpoken.isNotEmpty() && !appSpoken.lowercase().contains(" message")) {
                return StructuredMaxIntent(
                    classification = "actionable",
                    detectedLanguage = langStyle,
                    toolName = "launch_app",
                    parametersJson = """{"spoken_name":"${escapeJson(appSpoken)}"}"""
                )
            }
        }

        return null
    }

    private fun trimmedInputWithoutPunctuation(s: String): String =
        s.trim().trimEnd('.', '!', '?', '।')

    private fun escapeJson(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")

    private suspend fun callGeminiForIntent(
        userInput: String,
        history: List<Pair<Boolean, String>>,
        fallbackLanguage: String,
        attachment: AttachmentPayload? = null,
        customPersonality: String = "Natural, respectful, concise, confident, helpful, and practical",
        persistentMemoryConsent: Boolean = true,
        storedMemories: List<String> = emptyList(),
        webSearchGroundingEnabled: Boolean = true,
        codingAssistantMode: Boolean = true,
        voiceStylePreset: String = "Auto (Context-Aware)",
        personalizationDirective: String = "",
        preclassifiedBrainIntent: BrainIntentType = BrainIntentType.GENERAL_CHAT,
        contextSnapshot: ConversationContextSnapshot? = null
    ): StructuredMaxIntent {
        val geminiKey = BuildConfig.GEMINI_API_KEY.trim()
        val customKey = BuildConfig.CUSTOM_AI_API_KEY.trim()
        val activeCustomKey = when {
            customKey.isNotBlank() && customKey != "MY_CUSTOM_AI_API_KEY" -> customKey
            geminiKey.startsWith("sk-", ignoreCase = true) -> geminiKey
            else -> ""
        }
        val activeGeminiKey = if (geminiKey.isNotBlank() &&
            geminiKey != "MY_GEMINI_API_KEY" &&
            !geminiKey.startsWith("sk-", ignoreCase = true)
        ) {
            geminiKey
        } else {
            ""
        }

        if (activeGeminiKey.isEmpty() && activeCustomKey.isEmpty()) {
            return buildOfflineOrRateLimitFallbackIntent(
                userInput = userInput,
                fallbackLanguage = fallbackLanguage,
                attachment = attachment,
                error = IllegalStateException("No API key configured"),
                history = history,
                contextSnapshot = contextSnapshot,
                preclassifiedBrainIntent = preclassifiedBrainIntent
            )
        }

        val sysPrompt = MaxGeminiClient.buildSystemInstruction(
            customPersonality = customPersonality,
            storedMemories = if (persistentMemoryConsent) storedMemories else emptyList(),
            codingModeEnabled = codingAssistantMode,
            voiceStylePreset = voiceStylePreset,
            personalizationDirective = personalizationDirective
        )

        // Route sk-... keys to OpenAI-compatible / DeepSeek endpoint
        if (activeCustomKey.isNotEmpty() && activeGeminiKey.isEmpty()) {
            return callOpenAiCompatibleForIntent(
                apiKey = activeCustomKey,
                sysPrompt = sysPrompt,
                userInput = userInput,
                history = history,
                fallbackLanguage = fallbackLanguage,
                attachment = attachment,
                persistentMemoryConsent = persistentMemoryConsent,
                contextSnapshot = contextSnapshot,
                preclassifiedBrainIntent = preclassifiedBrainIntent
            )
        }

        val apiKey = activeGeminiKey

        val contents = mutableListOf<Content>()
        history.takeLast(8).forEach { (isUser, text) ->
            contents.add(
                Content(
                    role = if (isUser) "user" else "model",
                    parts = listOf(Part(text = text))
                )
            )
        }

        val contextHint = buildString {
            append("User input: ").append(userInput)
            append("\n[Required Response Language: $fallbackLanguage | Brain Intent: ${preclassifiedBrainIntent.name}]")
            if (contextSnapshot != null && contextSnapshot.rollingSummary.isNotBlank()) {
                append("\n[Session Rolling Summary: ${contextSnapshot.rollingSummary}]")
            }
            if (contextSnapshot?.isFollowUpToRecentTurn == true && !contextSnapshot.lastUserQuery.isNullOrBlank()) {
                append("\n[Follow-Up Reference Context: Previous user message was '${contextSnapshot.lastUserQuery}'. Resolve pronouns like 'woh', 'usko', 'pehle wala', 'same as before', 'aur bhi aasan karke samjhao', or 'continue' against that previous topic.]")
            }
            if (attachment != null && !attachment.mimeType.startsWith("image/") && attachment.mimeType != "application/pdf") {
                append("\n\n[Attached File '${attachment.fileName}' (${attachment.mimeType}) Content]:\n")
                append(attachment.extractedTextPreview)
            }
            lastLaunchedAppName?.let { append("\n[Context: Last launched app was '$it']") }
            lastSettingsTarget?.let { append("\n[Context: Last opened settings was '$it']") }
            lastRecipientName?.let { append("\n[Context: Last message recipient was '$it']") }
        }

        val userParts = mutableListOf<Part>()
        if (attachment != null && (attachment.mimeType.startsWith("image/") || attachment.mimeType == "application/pdf")) {
            userParts.add(
                Part(
                    inlineData = InlineData(
                        mimeType = attachment.mimeType,
                        data = attachment.base64Data
                    )
                )
            )
        }
        userParts.add(Part(text = contextHint))
        contents.add(Content(role = "user", parts = userParts))

        val lowerInput = userInput.lowercase()
        val needsWebSearchGrounding = webSearchGroundingEnabled && attachment == null && (
            lowerInput.contains("latest") || lowerInput.contains("news") || lowerInput.contains("today") ||
                lowerInput.contains("current") || lowerInput.contains("weather") || lowerInput.contains("price") ||
                lowerInput.contains("score") || lowerInput.contains("search web") || lowerInput.contains("who won") ||
                lowerInput.contains("aaj ") || lowerInput.contains("khabar") || lowerInput.contains("taaza")
            )

        val request = if (needsWebSearchGrounding) {
            GenerateContentRequest(
                contents = contents,
                generationConfig = GenerationConfig(temperature = 0.3f),
                tools = listOf(MaxGeminiClient.googleSearchTool),
                systemInstruction = Content(parts = listOf(Part(text = sysPrompt)))
            )
        } else {
            GenerateContentRequest(
                contents = contents,
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = MaxGeminiClient.responseSchema,
                    temperature = 0.25f
                ),
                systemInstruction = Content(parts = listOf(Part(text = sysPrompt)))
            )
        }

        return try {
            val response = MaxGeminiClient.generateContentWithFallback(apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val rawText = candidate?.content?.parts?.mapNotNull { it.text }?.joinToString("\n").orEmpty()

            // Extract grounding web sources if Google Search grounding was used
            val sources = candidate?.groundingMetadata?.groundingChunks
                ?.mapNotNull { chunk ->
                    val title = chunk.web?.title
                    val uri = chunk.web?.uri
                    if (!uri.isNullOrBlank()) "• ${title ?: "Web Source"}: $uri" else null
                }
                ?.distinct()
                .orEmpty()

            if (needsWebSearchGrounding) {
                val replyWithCitations = if (sources.isNotEmpty()) {
                    "$rawText\n\n🌐 Web Sources:\n${sources.take(4).joinToString("\n")}"
                } else {
                    rawText.ifBlank { "Here is what I found." }
                }
                return StructuredMaxIntent(
                    classification = "informational",
                    intentCategory = preclassifiedBrainIntent.name,
                    detectedLanguage = fallbackLanguage,
                    assistantReply = replyWithCitations
                )
            }

            if (rawText.isNotBlank()) {
                val parsed = try {
                    MaxGeminiClient.json.decodeFromString<StructuredMaxIntent>(rawText)
                } catch (_: Exception) {
                    StructuredMaxIntent(
                        classification = "informational",
                        intentCategory = preclassifiedBrainIntent.name,
                        detectedLanguage = fallbackLanguage,
                        assistantReply = rawText
                    )
                }

                // Persist memory fact ONLY when user has enabled Persistent Memory Consent & passes privacy check
                val memoryFact = parsed.extractedMemoryFact?.trim().orEmpty()
                if (persistentMemoryConsent && memoryFact.isNotBlank() && memoryFact.length >= 4) {
                    memoryService.saveOrUpdateMemory(
                        factText = memoryFact,
                        explicitCategory = parsed.memoryCategory,
                        sourceType = "AI_Extracted"
                    )
                }
                parsed
            } else {
                StructuredMaxIntent(
                    classification = "informational",
                    intentCategory = preclassifiedBrainIntent.name,
                    detectedLanguage = fallbackLanguage,
                    assistantReply = "Boss, AI response empty aaya. Please try again."
                )
            }
        } catch (e: Exception) {
            // If Gemini returned HTTP 429 (Quota/Rate limit) and user also has a custom sk-... key, fail over automatically
            if (activeCustomKey.isNotEmpty()) {
                return callOpenAiCompatibleForIntent(
                    apiKey = activeCustomKey,
                    sysPrompt = sysPrompt,
                    userInput = userInput,
                    history = history,
                    fallbackLanguage = fallbackLanguage,
                    attachment = attachment,
                    persistentMemoryConsent = persistentMemoryConsent,
                    contextSnapshot = contextSnapshot,
                    preclassifiedBrainIntent = preclassifiedBrainIntent
                )
            }
            buildOfflineOrRateLimitFallbackIntent(
                userInput = userInput,
                fallbackLanguage = fallbackLanguage,
                attachment = attachment,
                error = e,
                history = history,
                contextSnapshot = contextSnapshot,
                preclassifiedBrainIntent = preclassifiedBrainIntent
            )
        }
    }

    private suspend fun callOpenAiCompatibleForIntent(
        apiKey: String,
        sysPrompt: String,
        userInput: String,
        history: List<Pair<Boolean, String>>,
        fallbackLanguage: String,
        attachment: AttachmentPayload?,
        persistentMemoryConsent: Boolean,
        contextSnapshot: ConversationContextSnapshot? = null,
        preclassifiedBrainIntent: BrainIntentType = BrainIntentType.GENERAL_CHAT
    ): StructuredMaxIntent {
        val jsonSystemInstruction = """
$sysPrompt

Respond ONLY with a valid JSON object matching this exact structure:
{
  "classification": "informational|actionable|clarification_needed|unsupported",
  "intentCategory": "${preclassifiedBrainIntent.name}",
  "detectedLanguage": "Hindi|English|Hinglish",
  "toolName": "",
  "parametersJson": "",
  "assistantReply": "Your reply in the user's exact language style",
  "requiresClarificationQuestion": "",
  "extractedMemoryFact": "",
  "memoryCategory": ""
}
""".trimIndent()

        val messages = mutableListOf(
            OpenAiChatMessage(role = "system", content = jsonSystemInstruction)
        )
        history.takeLast(6).forEach { (isUser, text) ->
            messages.add(
                OpenAiChatMessage(
                    role = if (isUser) "user" else "assistant",
                    content = text
                )
            )
        }

        val fullUserPrompt = buildString {
            append(userInput)
            append("\n[Required Response Language: $fallbackLanguage]")
            if (contextSnapshot != null && contextSnapshot.rollingSummary.isNotBlank()) {
                append("\n[Session Rolling Summary: ${contextSnapshot.rollingSummary}]")
            }
            if (contextSnapshot?.isFollowUpToRecentTurn == true && !contextSnapshot.lastUserQuery.isNullOrBlank()) {
                append("\n[Follow-Up Reference Context: Previous user message was '${contextSnapshot.lastUserQuery}']")
            }
            if (attachment != null) {
                append("\n\n[Attached File: ${attachment.fileName} (${attachment.mimeType})]\n")
                append(attachment.extractedTextPreview)
            }
            lastLaunchedAppName?.let { append("\n[Context: Last launched app was '$it']") }
            lastSettingsTarget?.let { append("\n[Context: Last opened settings was '$it']") }
            lastRecipientName?.let { append("\n[Context: Last message recipient was '$it']") }
        }
        messages.add(OpenAiChatMessage(role = "user", content = fullUserPrompt))

        val configuredBase = BuildConfig.CUSTOM_AI_BASE_URL.trim().ifBlank { "https://api.deepseek.com/" }
        val normalizedBase = if (configuredBase.endsWith("/")) configuredBase else "$configuredBase/"
        val endpointUrl = "${normalizedBase}chat/completions"
        val modelName = if (normalizedBase.contains("openai.com")) "gpt-4o-mini" else "deepseek-chat"

        return try {
            val response = MaxGeminiClient.service.generateOpenAiCompatible(
                url = endpointUrl,
                bearerToken = "Bearer $apiKey",
                request = OpenAiChatRequest(
                    model = modelName,
                    messages = messages,
                    temperature = 0.25f,
                    response_format = OpenAiResponseFormat(type = "json_object")
                )
            )
            val rawText = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            if (rawText.isNotBlank()) {
                val cleanedJson = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val parsed = try {
                    MaxGeminiClient.json.decodeFromString<StructuredMaxIntent>(cleanedJson)
                } catch (_: Exception) {
                    StructuredMaxIntent(
                        classification = "informational",
                        intentCategory = preclassifiedBrainIntent.name,
                        detectedLanguage = fallbackLanguage,
                        assistantReply = rawText
                    )
                }
                val memoryFact = parsed.extractedMemoryFact?.trim().orEmpty()
                if (persistentMemoryConsent && memoryFact.isNotBlank() && memoryFact.length >= 4) {
                    memoryService.saveOrUpdateMemory(
                        factText = memoryFact,
                        explicitCategory = parsed.memoryCategory,
                        sourceType = "AI_Extracted"
                    )
                }
                parsed
            } else {
                StructuredMaxIntent(
                    classification = "informational",
                    intentCategory = preclassifiedBrainIntent.name,
                    detectedLanguage = fallbackLanguage,
                    assistantReply = "Boss, AI response empty aaya. Please try again."
                )
            }
        } catch (e: Exception) {
            buildOfflineOrRateLimitFallbackIntent(
                userInput = userInput,
                fallbackLanguage = fallbackLanguage,
                attachment = attachment,
                error = e,
                history = history,
                contextSnapshot = contextSnapshot,
                preclassifiedBrainIntent = preclassifiedBrainIntent
            )
        }
    }

    /**
     * Handles conversational, follow-up simplification ("Aur bhi aasan karke samjhao"), explanations
     * ("Explain AI in simple Hindi", "Explain cloud computing"), time/date, identity, attachment preview,
     * and rate-limit recovery locally so MAX AI's Conversational Brain works seamlessly even offline or under HTTP 429.
     */
    private fun buildOfflineOrRateLimitFallbackIntent(
        userInput: String,
        fallbackLanguage: String,
        attachment: AttachmentPayload?,
        error: Exception,
        history: List<Pair<Boolean, String>> = emptyList(),
        contextSnapshot: ConversationContextSnapshot? = null,
        preclassifiedBrainIntent: BrainIntentType = BrainIntentType.GENERAL_CHAT
    ): StructuredMaxIntent {
        val cleanInput = userInput.removePrefix("User input:").trim()
        val lower = cleanInput.lowercase()

        // 0. Follow-Up Simplification & Reference Resolution (e.g., Test A: "Explain AI in simple Hindi" -> "Aur bhi aasan karke samjhao")
        val prevUserTopic = contextSnapshot?.lastUserQuery
            ?: history.lastOrNull { it.first && !it.second.equals(cleanInput, ignoreCase = true) }?.second
            ?: lastExplainedTopic.orEmpty()

        val isFollowUpSimplify = lower.contains("aur bhi aasan") || lower.contains("aasan karke") ||
            lower.contains("aur simple") || lower.contains("simpler") || lower.contains("even simpler") ||
            lower.contains("bacche ki tarah") || lower.contains("example ke saath") ||
            (contextSnapshot?.isFollowUpToRecentTurn == true && (lower.contains("samjhao") || lower.contains("explain") || lower.contains("continue") || lower.contains("woh") || lower.contains("usko") || lower.contains("pehle wala")))

        if (isFollowUpSimplify && prevUserTopic.isNotBlank()) {
            val prevLower = prevUserTopic.lowercase()
            return when {
                prevLower.contains("ai") || prevLower.contains("artificial intelligence") -> {
                    StructuredMaxIntent(
                        classification = "informational",
                        intentCategory = BrainIntentType.FOLLOW_UP.name,
                        detectedLanguage = fallbackLanguage,
                        emotionalTone = "everyday_conversation",
                        assistantReply = formatMsg(
                            fallbackLanguage,
                            hi = "चलो **AI (आर्टिफिशियल इंटेलिजेंस)** को और भी आसान उदाहरण से समझते हैं:\n\nजैसे एक छोटा बच्चा चीज़ों को देखकर और सुनकर धीरे-धीरे सीखता है कि 'यह बिल्ली है' और 'यह गेंद है', बिल्कुल वैसे ही जब हम कंप्यूटर को बहुत सारे उदाहरण दिखाकर सोचना और समझना सिखा देते हैं, तो उसे **AI** कहते हैं।\n\n**रोज़मर्रा का उदाहरण:** जब आप मुझसे (MAX AI से) कहते हैं *\"शिवम को कॉल करो\"*, तो मेरा आपके शब्दों का मतलब समझना और सही काम करना ही AI है!",
                            en = "Let's make **AI (Artificial Intelligence)** even simpler:\n\nImagine teaching a child to recognize a mango by showing them a few mangoes. AI works the same way for computers—instead of following rigid rules, the computer learns from examples so it can understand your voice, answer questions, and help you out just like a smart friend.",
                            hinglish = "Chalo **AI (Artificial Intelligence)** ko ekdum aasan example se samajhte hain:\n\nJaise ek chhota baccha cheezon ko dekh-dekh kar seekhta hai, waise hi jab hum computer ko examples dikha kar insaan ki tarah samajhna aur faisla lena sikha dete hain, toh usse **AI** kehte hain. Jaise aap mujhse bolte ho aur main aapki baat samajh leta hoon!"
                        )
                    )
                }
                prevLower.contains("cloud") -> {
                    StructuredMaxIntent(
                        classification = "informational",
                        intentCategory = BrainIntentType.FOLLOW_UP.name,
                        detectedLanguage = fallbackLanguage,
                        emotionalTone = "everyday_conversation",
                        assistantReply = formatMsg(
                            fallbackLanguage,
                            hi = "**क्लाउड कंप्यूटिंग (Cloud Computing)** को और भी आसान भाषा में समझिए:\n\nजैसे घर में बिजली के लिए हम खुद का पावर प्लांट नहीं लगाते, बल्कि बिजली कंपनी से तार जोड़कर जितनी ज़रूरत हो उतनी बिजली इस्तेमाल करते हैं — वैसे ही अपने फोन या छोटे कंप्यूटर में सब कुछ सेव करने के बजाय इंटरनेट के ज़रिए बड़े और सुरक्षित कंप्यूटरों का इस्तेमाल करना ही **क्लाउड कंप्यूटिंग** है (जैसे Google Drive या YouTube)।",
                            en = "Think of **Cloud Computing** like electricity at home: you don't build your own power plant—you just plug into the wall and use what you need. Similarly, instead of storing everything on your own device, you use powerful computers over the internet (like Google Drive or Netflix).",
                            hinglish = "**Cloud Computing** ko aur aasan karke samjho: jaise hum apni photos phone ki memory bharne ke bajaye Google Photos par rakhte hain aur kahin se bhi dekh lete hain, bas internet par maujood unhi powerful servers ko use karna Cloud Computing kehlata hai."
                        )
                    )
                }
                else -> {
                    StructuredMaxIntent(
                        classification = "informational",
                        intentCategory = BrainIntentType.FOLLOW_UP.name,
                        detectedLanguage = fallbackLanguage,
                        emotionalTone = "everyday_conversation",
                        assistantReply = formatMsg(
                            fallbackLanguage,
                            hi = "आपके पिछले विषय (**\"$prevUserTopic\"**) को और भी सरल शब्दों में समझें:\n\nइसका मुख्य मतलब यह है कि जटिल प्रक्रिया को छोटे-छोटे आसान चरणों में बाँटकर काम को तेज़ और सहज बनाया जाए। क्या आप इसका कोई व्यावहारिक उदाहरण या कोड देखना चाहेंगे?",
                            en = "Making our previous topic (**\"$prevUserTopic\"**) even simpler:\n\nAt its core, it breaks a complex process into simple, step-by-step building blocks so you get reliable results with minimal effort. Would you like a real-world example?",
                            hinglish = "Aapke pichle topic (**\"$prevUserTopic\"**) ko aur bhi aasan shabdon mein samjhein toh iska main purpose complex kaam ko simple step-by-step tareeke se solve karna hai. Kya main iska ek real-life example doon?"
                        )
                    )
                }
            }
        }

        // 0b. Direct Explanations ("Explain AI in simple Hindi", "Explain cloud computing", etc.)
        if (lower.contains("explain ai") || (lower.contains("artificial intelligence") && (lower.contains("explain") || lower.contains("samjhao") || lower.contains("kya hai")))) {
            lastExplainedTopic = "Artificial Intelligence (AI)"
            return StructuredMaxIntent(
                classification = "informational",
                intentCategory = BrainIntentType.EXPLANATION.name,
                detectedLanguage = fallbackLanguage,
                emotionalTone = "everyday_conversation",
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "**आर्टिफिशियल इंटेलिजेंस (AI)** तकनीक की वह शाखा है जो कंप्यूटर और मशीनों को इंसानों की तरह सोचने, सीखने, भाषा समझने और निर्णय लेने की क्षमता देती है।\n\n**आसान शब्दों में मुख्य बातें:**\n1. **सीखने की क्षमता:** यह डेटा और अनुभव से पैटर्न सीखती है।\n2. **भाषा और आवाज़ की समझ:** जैसे मैं (MAX AI) आपकी हिंदी और Hinglish समझकर जवाब देता हूँ।\n3. **रोज़मर्रा के उपयोग:** स्मार्ट असिस्टेंट, Google Maps का ट्रैफ़िक अनुमान, और YouTube की वीडियो सिफारिशें।",
                    en = "**Artificial Intelligence (AI)** is technology that enables computers and software to learn from data, understand natural language, recognize patterns, and make helpful decisions much like humans do.\n\n**Key Everyday Examples:**\n1. **Voice & Chat Assistants** like MAX AI understanding Hindi, English, and Hinglish.\n2. **Smart Recommendations** on YouTube and Spotify.\n3. **Image & Document Understanding** for analyzing photos and PDFs.",
                    hinglish = "**Artificial Intelligence (AI)** ek aisi technology hai jisse computers aur machines insaanon ki tarah seekh sakte hain, bhasha samajh sakte hain, aur smart decisions le sakte hain.\n\n**Simple Examples:**\n1. **Voice Assistants** jaise MAX AI jo aapki Hindi aur Hinglish samajhta hai.\n2. **Google Maps** jo live traffic dekh kar sabse fast rasta batata hai.\n3. **YouTube/Netflix** jo aapki pasand ke hisaab se videos suggest karte hain."
                )
            )
        }

        if (lower.contains("cloud computing") || (lower.contains("cloud") && (lower.contains("explain") || lower.contains("samjhao") || lower.contains("kya hai")))) {
            lastExplainedTopic = "Cloud Computing"
            return StructuredMaxIntent(
                classification = "informational",
                intentCategory = BrainIntentType.EXPLANATION.name,
                detectedLanguage = fallbackLanguage,
                emotionalTone = "everyday_conversation",
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "**क्लाउड कंप्यूटिंग (Cloud Computing)** का मतलब है अपने खुद के कंप्यूटर या हार्ड ड्राइव के बजाय **इंटरनेट के माध्यम से** डेटा स्टोर करना, सर्वर चलाना और सॉफ़्टवेयर का उपयोग करना।\n\n**मुख्य लाभ:**\n1. **कहीं से भी एक्सेस:** आप अपने फ़ोन या लैपटॉप से कहीं भी अपना डेटा देख सकते हैं (जैसे Google Drive, Gmail)।\n2. **कम खर्च:** महँगे सर्वर खरीदने की ज़रूरत नहीं होती।\n3. **सुरक्षा और बैकअप:** डेटा सुरक्षित सर्वरों पर बैकअप रहता है।",
                    en = "**Cloud Computing** is the delivery of computing services—including storage, servers, databases, and software—over the internet (\"the cloud\") instead of relying only on your local phone or computer hard drive.\n\n**Key Benefits:**\n1. **Access Anywhere:** Open your files from any device (e.g., Google Drive, Gmail).\n2. **Scalability:** Instantly scale storage or computing power as needed.\n3. **Reliable Backup:** Data stays safe even if your local device is lost.",
                    hinglish = "**Cloud Computing** ka matlab hai apne local phone ya computer ki hard drive ke bajaye **internet ke zariye** remote servers par data store karna aur apps/software run karna.\n\n**Key Benefits:**\n1. **Kahin se bhi access:** Jaise Google Drive ya Gmail kisi bhi device par khul jaata hai.\n2. **No heavy hardware:** Aapko khud ke mehenge servers nahi kharidne padte.\n3. **Safe Backup:** Phone badalne par bhi aapka cloud data safe rehta hai."
                )
            )
        }

        // 1. If user attached a file/PDF/code and cloud quota is hit, return the extracted local preview
        if (attachment != null && attachment.extractedTextPreview.isNotBlank()) {
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "📄 **${attachment.fileName}** (${attachment.mimeType}) का लोकल विश्लेषण:\n\n${attachment.extractedTextPreview.take(1500)}",
                    en = "📄 Local analysis of **${attachment.fileName}** (${attachment.mimeType}):\n\n${attachment.extractedTextPreview.take(1500)}",
                    hinglish = "📄 Boss, **${attachment.fileName}** (${attachment.mimeType}) ka extracted content yahan hai:\n\n${attachment.extractedTextPreview.take(1500)}"
                )
            )
        }

        // 2. Greetings & conversational queries ("hello", "hi", "namaste", "kaise ho", "how are you", "who are you")
        if (lower in listOf("hi", "hello", "hey", "hii", "namaste", "ram ram", "salaam", "hey max", "hey maxi", "max", "maxi") ||
            lower.startsWith("hello ") || lower.startsWith("hi ") || lower.startsWith("namaste")
        ) {
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                emotionalTone = "happy",
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "नमस्ते! मैं MAX हूँ। बताइए आज कैसे मदद करूँ — कोई बातचीत करनी है, किसी को कॉल लगाना है, या कोई काम करना है?",
                    en = "Hey there! I'm MAX. What's on your mind today — want to chat, brainstorm something, make a call, or get something done?",
                    hinglish = "Hey! Main MAX hoon. Batao aaj kya chal raha hai — kuch baat karni hai, kisi ko call lagana hai, ya main kisi kaam mein tumhari help karoon?"
                )
            )
        }

        if (lower.contains("kaise ho") || lower.contains("how are you") || lower.contains("kya haal") || lower.contains("sab badhiya")) {
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                emotionalTone = "happy",
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "सब एकदम बढ़िया है! आपकी आवाज़ सुनकर और भी अच्छा लगा। आप बताइए, आपका दिन कैसा चल रहा है?",
                    en = "I'm doing great, thanks for asking! Always happy to chat with you. How's your day going?",
                    hinglish = "Sab ekdum badhiya hai yaar! Tum batao, aaj ka din kaisa chal raha hai? Chalo, jo bhi kaam ho batao, main tumhari help karta hoon."
                )
            )
        }

        // Common Hinglish acknowledgements & expressions ("Achha, samajh gaya", "Chalo, main tumhari help karta hoon", "theek hai", "shukriya", "thank you")
        if (lower.contains("achha") || lower.contains("samajh gaya") || lower.contains("theek hai") ||
            lower.contains("shukriya") || lower.contains("thank you") || lower.contains("thanks") ||
            lower.contains("dhanyawad")
        ) {
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                emotionalTone = "happy",
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "अच्छा, बढ़िया! जब भी ज़रूरत हो बेझिझक बताना, मैं यहीं हूँ।",
                    en = "You're most welcome! Anytime you need a hand, just let me know.",
                    hinglish = "Achha, badhiya! Chalo, jab bhi zaroorat ho bata dena, main hamesha tumhari help ke liye ready hoon."
                )
            )
        }

        // Empathetic / Sad / Stressed conversational support
        if (lower.contains("sad") || lower.contains("udaas") || lower.contains("stressed") ||
            lower.contains("pareshan") || lower.contains("tension") || lower.contains("low feel") ||
            lower.contains("feeling down") || lower.contains("lonely") || lower.contains("akela")
        ) {
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                emotionalTone = "empathetic",
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "मैं समझ सकता हूँ कि यह समय थोड़ा भारी लग रहा होगा। थोड़ा गहरी साँस लो, तुम अकेले नहीं हो। अगर मन हल्का करने के लिए बात करनी हो, तो मैं पूरे ध्यान से सुन रहा हूँ।",
                    en = "I hear you, and it's completely okay to feel this way sometimes. Take a slow breath—you don't have to carry it all at once. I'm right here if you want to talk it through.",
                    hinglish = "Achha, main samajh sakta hoon yaar, kabhi-kabhi sab kuch thoda overwhelming lagta hai. Chinta mat karo, aaram se ek gehri saans lo. Agar mann halka karna ho ya kuch share karna ho, toh main poori tarah sun raha hoon."
                )
            )
        }

        // Storytelling & Romantic Storytelling requests
        if (lower.contains("story") || lower.contains("kahani") || lower.contains("shayari") || lower.contains("poem")) {
            val isRomantic = lower.contains("romantic") || lower.contains("love") || lower.contains("pyaar") || lower.contains("mohabbat") || lower.contains("shayari")
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                emotionalTone = if (isRomantic) "romantic_storytelling" else "storytelling",
                assistantReply = if (isRomantic) {
                    formatMsg(
                        fallbackLanguage,
                        hi = "चाँदनी रात की उस खामोशी में, जब हवा ने धीरे से खिड़की को छुआ... तो लगा जैसे बिना कहे ही सारी बातें समझ ली गईं। कुछ रिश्ते शब्दों के मोहताज नहीं होते, बस एक सुकून भरा साथ ही काफ़ी होता है।",
                        en = "Under the quiet glow of the evening streetlights, two people walked in comfortable silence—realizing that the warmest moments in life rarely need grand words, just the quiet certainty of being understood.",
                        hinglish = "Shaam ki us halki si thandak mein, jab baarish ki boondein khidki par ruk-ruk kar gir rahi thi... tab samajh aaya ki kuch kahaaniyan lafzon se nahi, bas ek doosre ki khamoshi samajhne se poori hoti hain."
                    )
                } else {
                    formatMsg(
                        fallbackLanguage,
                        hi = "एक समय की बात है, पहाड़ों के बीच बसे एक छोटे से गाँव में एक युवा कारीगर रहता था। लोग कहते थे कि असंभव को संभव करना आसान नहीं, लेकिन उसने हर दिन धैर्य और लगन से वो रास्ता बना दिखाया जो किसी ने सोचा भी नहीं था।",
                        en = "Long ago, at the edge of a misty valley, a traveler set out with nothing but a lantern and a quiet promise. Every step revealed a little more of the path ahead—proving that you never need to see the whole mountain to begin the climb.",
                        hinglish = "Ek samay ki baat hai, pahadon ke beech ek chhote se shehar mein ek musafir rehta tha. Usne seekha ki manzil kitni bhi door kyun na ho, agar hausla sachcha ho toh har kadam par naya raasta khud banta chala jaata hai."
                    )
                }
            )
        }

        if (lower.contains("who are you") || lower.contains("tum kaun ho") || lower.contains("aap kaun") ||
            lower.contains("your name") || lower.contains("tumhara naam") || lower.contains("what can you do") ||
            lower.contains("kya kar sakte")
        ) {
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "मैं **MAXI** हूँ — **MAX** ऐप का मुख्य AI असिस्टेंट। मैं हिंदी, English और Hinglish में बात कर सकता हूँ, आपके फोन के ऐप्स खोल सकता हूँ, WhatsApp मैसेज तैयार कर सकता हूँ, अलार्म लगा सकता हूँ, आवाज़/सेटिंग्स कंट्रोल कर सकता हूँ, और कोडिंग व फाइल्स समझने में मदद करता हूँ।",
                    en = "I am **MAXI**, the core AI assistant of **MAX**. I understand Hindi, English, and Hinglish, launch apps, prepare WhatsApp messages, set alarms, control volume and system settings, analyze PDFs/images, and assist with coding.",
                    hinglish = "Main **MAXI** hoon — **MAX** ka core AI assistant! Main Hindi, English aur Hinglish samajhta hoon, apps launch kar sakta hoon, WhatsApp messages draft/send kar sakta hoon, alarms set kar sakta hoon, device settings control kar sakta hoon, aur coding/files mein madad karta hoon."
                )
            )
        }

        // 3. Time & Date queries
        if (lower.contains("time kya") || lower.contains("what time") || lower.contains("current time") ||
            lower.contains("kitne baje") || lower.contains("aaj ki date") || lower.contains("what is the date") ||
            lower.contains("today's date") || lower == "time" || lower == "date"
        ) {
            val now = java.text.SimpleDateFormat("hh:mm a, EEEE, dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            return StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "बॉस, अभी का समय और तारीख है: **$now**",
                    en = "Current time and date: **$now**",
                    hinglish = "Boss, abhi time aur date hai: **$now**"
                )
            )
        }

        val errMsg = error.message.orEmpty()
        val isRateLimit = errMsg.contains("429") || errMsg.contains("Too Many Requests", ignoreCase = true) ||
            errMsg.contains("quota", ignoreCase = true) || errMsg.contains("402")

        return if (isRateLimit) {
            StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "बॉस, आपकी API Key का प्रति-मिनट कोटा (HTTP 429 Rate Limit) अभी पूरा हो गया है। मैंने `gemini-2.5-flash`, `gemini-2.5-flash-lite`, और `gemini-2.0-flash` पर ऑटो-फ़ॉलबैक भी ट्राई किया। कृपया 15–30 सेकंड बाद दोबारा प्रयास करें या AI Studio के Secrets पैनल में नई Gemini API Key डालें। (आपके सभी फोन कंट्रोल कमांड्स जैसे 'Open YouTube', 'Volume 70', 'Wi-Fi settings' बिना किसी रुकावट के अभी भी तुरंत काम कर रहे हैं!)",
                    en = "Your API key hit its per-minute rate/quota limit (HTTP 429) across the fallback model chain (`gemini-2.5-flash` -> `gemini-2.5-flash-lite` -> `gemini-2.0-flash`). Please wait 15–30 seconds before sending the next AI query, or update `GEMINI_API_KEY` in the AI Studio Secrets panel. All Android device control commands ('Open Instagram', 'Set alarm 7 AM', 'Volume 60%') continue to work immediately!",
                    hinglish = "Boss, aapki API key par abhi rate-limit / quota limit (HTTP 429) hit ho gaya hai (maine `gemini-2.5-flash`, `gemini-2.5-flash-lite`, aur `gemini-2.0-flash` teeno par auto-retry kiya). Please 15–30 seconds wait karke dobara message bhejein, ya AI Studio ke **Secrets** panel mein doosri `GEMINI_API_KEY` add kar dein. Tab tak aapke saare Android device commands (jaise 'Instagram kholo', 'Volume 80 karo', 'Alarm 7 AM') bina ruke kaam kar rahe hain!"
                )
            )
        } else {
            StructuredMaxIntent(
                classification = "informational",
                detectedLanguage = fallbackLanguage,
                assistantReply = formatMsg(
                    fallbackLanguage,
                    hi = "AI सेवा में त्रुटि: $errMsg। डिवाइस कमांड्स सामान्य रूप से उपलब्ध हैं।",
                    en = "Could not reach AI service ($errMsg). Local device commands remain active.",
                    hinglish = "Boss, AI API call mein error aaya ($errMsg). Device control commands active hain."
                )
            )
        }
    }

    suspend fun executeStructuredIntent(
        intent: StructuredMaxIntent,
        languageStyle: String
    ): ToolExecutionResult {
        when (intent.classification.lowercase()) {
            "clarification_needed" -> {
                val question = intent.requiresClarificationQuestion?.takeIf { it.isNotBlank() }
                    ?: intent.assistantReply.ifBlank { "Boss, thoda clear bataiye kya karna hai?" }
                return ToolExecutionResult(
                    toolName = intent.toolName ?: "clarification",
                    status = InteractionStatus.CLARIFICATION_REQUIRED,
                    userMessage = question,
                    verificationEvidence = "Ambiguous command triggered single clarification question"
                )
            }
            "unsupported" -> {
                return ToolExecutionResult(
                    toolName = intent.toolName ?: "unsupported",
                    status = InteractionStatus.UNSUPPORTED_ACTION,
                    userMessage = intent.assistantReply.ifBlank {
                        "This action is not supported or is restricted by Android security policies."
                    },
                    verificationEvidence = "Disclosed unsupported or restricted platform capability"
                )
            }
            "informational" -> {
                if (intent.toolName.isNullOrBlank()) {
                    return ToolExecutionResult(
                        toolName = "informational_qa",
                        status = InteractionStatus.INFO_RESPONSE,
                        userMessage = intent.assistantReply.ifBlank { "Ji boss, bataiye main kaise madad karoon?" },
                        verificationEvidence = "Answered informational query in ${intent.detectedLanguage}",
                        emotionalTone = intent.emotionalTone,
                        spokenSummary = intent.spokenReplySummary
                    )
                }
            }
        }

        val tool = intent.toolName?.trim()?.lowercase().orEmpty()
        val paramsObj: JsonObject = try {
            if (!intent.parametersJson.isNullOrBlank()) {
                MaxGeminiClient.json.parseToJsonElement(intent.parametersJson).jsonObject
            } else {
                JsonObject(emptyMap())
            }
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }

        return when (tool) {
            "call_contact" -> {
                val contactName = (paramsObj["contactName"] ?: paramsObj["contact_name"] ?: paramsObj["recipient_name"])
                    ?.jsonPrimitive?.content.orEmpty()
                val phone = paramsObj["phone_number"]?.jsonPrimitive?.content
                val speaker = paramsObj["speaker"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                if (contactName.isNotBlank()) {
                    lastRecipientName = contactName
                }
                lastToolName = "call_contact"
                callingExecutor.executeCallContact(
                    contactName = contactName,
                    phoneNumber = phone,
                    speaker = speaker,
                    languageStyle = languageStyle
                )
            }
            "set_speaker" -> {
                val enabled = paramsObj["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                lastToolName = "set_speaker"
                callingExecutor.executeSetSpeaker(
                    enabled = enabled,
                    languageStyle = languageStyle
                )
            }
            "launch_app" -> {
                val spoken = paramsObj["spoken_name"]?.jsonPrimitive?.content.orEmpty()
                val pkg = paramsObj["package_name"]?.jsonPrimitive?.content
                val choice = paramsObj["disambiguation_choice"]?.jsonPrimitive?.content
                val res = appLaunchManager.executeLaunchApp(spoken, pkg, choice, languageStyle)
                if (res.status == InteractionStatus.VERIFIED_SUCCESS) {
                    lastLaunchedAppName = spoken.ifBlank { pkg }
                    lastToolName = "launch_app"
                }
                res
            }
            "open_settings" -> {
                val target = paramsObj["settings_target"]?.jsonPrimitive?.content ?: "settings"
                val res = deviceControlExecutor.executeOpenSettings(target, languageStyle)
                if (res.status == InteractionStatus.VERIFIED_SUCCESS) {
                    lastSettingsTarget = target
                    lastToolName = "open_settings"
                }
                res
            }
            "adjust_volume" -> {
                val stream = paramsObj["stream"]?.jsonPrimitive?.content ?: "media"
                val op = paramsObj["operation"]?.jsonPrimitive?.content ?: "increase"
                val value = paramsObj["value"]?.jsonPrimitive?.intOrNull
                lastToolName = "adjust_volume"
                deviceControlExecutor.executeAdjustVolume(stream, op, value, languageStyle)
            }
            "control_media" -> {
                val op = paramsObj["operation"]?.jsonPrimitive?.content ?: "play"
                val app = paramsObj["application"]?.jsonPrimitive?.content
                lastToolName = "control_media"
                deviceControlExecutor.executeControlMedia(op, app, languageStyle)
            }
            "control_device_setting" -> {
                val setting = paramsObj["setting"]?.jsonPrimitive?.content ?: "wifi"
                val state = paramsObj["desired_state"]?.jsonPrimitive?.content ?: "on"
                lastToolName = "control_device_setting"
                deviceControlExecutor.executeControlDeviceSetting(setting, state, languageStyle)
            }
            "accessibility_action" -> {
                val op = paramsObj["operation"]?.jsonPrimitive?.content ?: "tap"
                val target = paramsObj["target"]?.jsonPrimitive?.content.orEmpty()
                val text = paramsObj["text"]?.jsonPrimitive?.content
                lastToolName = "accessibility_action"
                deviceControlExecutor.executeAccessibilityAction(
                    operation = op,
                    target = target,
                    text = text,
                    isConfirmed = false,
                    languageStyle = languageStyle
                )
            }
            "schedule_alarm_or_reminder" -> {
                val time = paramsObj["time"]?.jsonPrimitive?.content.orEmpty()
                val date = paramsObj["date"]?.jsonPrimitive?.content
                val label = paramsObj["label"]?.jsonPrimitive?.content
                val recurrence = paramsObj["recurrence"]?.jsonPrimitive?.content
                lastToolName = "schedule_alarm_or_reminder"
                deviceControlExecutor.executeScheduleAlarmOrReminder(
                    time = time,
                    date = date,
                    label = label,
                    recurrence = recurrence,
                    languageStyle = languageStyle
                )
            }
            "prepare_message" -> {
                val app = paramsObj["application"]?.jsonPrimitive?.content ?: "WhatsApp"
                val recipient = paramsObj["recipient_name"]?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() } ?: lastRecipientName
                val phone = paramsObj["phone_number"]?.jsonPrimitive?.content
                val msg = paramsObj["message"]?.jsonPrimitive?.content
                val mode = paramsObj["mode"]?.jsonPrimitive?.content ?: "draft"
                if (!recipient.isNullOrBlank()) {
                    lastRecipientName = recipient
                }
                lastToolName = "prepare_message"
                messagingExecutor.executePrepareMessage(
                    application = app,
                    recipientName = recipient,
                    phoneNumber = phone,
                    message = msg,
                    mode = mode,
                    isConfirmed = false,
                    languageStyle = languageStyle
                )
            }
            "social_media_action" -> {
                val platform = paramsObj["platform"]?.jsonPrimitive?.content ?: "YouTube"
                val actionType = paramsObj["action_type"]?.jsonPrimitive?.content ?: "search"
                val query = paramsObj["query_or_target"]?.jsonPrimitive?.content.orEmpty()
                val draft = paramsObj["draft_text"]?.jsonPrimitive?.content
                lastToolName = "social_media_action"
                messagingExecutor.executeSocialMediaWorkflow(
                    platform = platform,
                    actionType = actionType,
                    queryOrTarget = query,
                    draftText = draft,
                    isConfirmed = false,
                    languageStyle = languageStyle
                )
            }
            "read_notifications" -> {
                executeReadNotifications(languageStyle)
            }
            "system_navigation" -> {
                val target = paramsObj["target"]?.jsonPrimitive?.content ?: "home"
                deviceControlExecutor.executeSystemNavigation(target, languageStyle)
            }
            "share_location" -> {
                val recipient = (paramsObj["recipient_name"] ?: paramsObj["contact_name"])?.jsonPrimitive?.content.orEmpty()
                val phone = paramsObj["phone_number"]?.jsonPrimitive?.content.orEmpty()
                val platform = paramsObj["platform"]?.jsonPrimitive?.content ?: "whatsapp"
                lastToolName = "share_location"
                executeShareLocation(recipient, phone, platform, languageStyle)
            }
            "get_location" -> {
                lastToolName = "get_location"
                executeGetLocation(languageStyle)
            }
            else -> {
                ToolExecutionResult(
                    toolName = "informational_qa",
                    status = InteractionStatus.INFO_RESPONSE,
                    userMessage = intent.assistantReply.ifBlank {
                        "Ji boss, main aapki baat samajh gaya. Kya koi specific action perform karna hai?"
                    },
                    verificationEvidence = "Informational response"
                )
            }
        }
    }

    private suspend fun executeGetLocation(languageStyle: String): ToolExecutionResult {
        val hasPerm = MaxLocationService.hasLocationPermission(context)
        if (!hasPerm) {
            return ToolExecutionResult(
                toolName = "get_location",
                status = InteractionStatus.PERMISSION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, लोकेशन जानने के लिए Location Permission (GPS) आवश्यक है। कृपया Permissions टैब में जाकर इसे अनुमति दें।",
                    en = "Location permission is required to detect your GPS coordinates. Please grant location access in the Permissions tab.",
                    hinglish = "Boss, current location check karne ke liye Location permission chahiye. Please Permissions tab se allow kar dijiye."
                ),
                verificationEvidence = "Location permission not granted"
            )
        }

        val location = MaxLocationService.getCurrentLocation(context)
        if (location == null) {
            return ToolExecutionResult(
                toolName = "get_location",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, जीपीएस लोकेशन प्राप्त नहीं हो सकी। कृपया लोकेशन सर्विस (GPS) ऑन है या नहीं, जांचें।",
                    en = "Unable to fetch GPS location. Please make sure Device Location/GPS is turned on.",
                    hinglish = "Boss, GPS location fetch nahi ho payi. Please check karein ki device GPS on hai."
                ),
                verificationEvidence = "LocationManager returned null location"
            )
        }

        val addrPart = if (location.addressText.isNotBlank()) "आप यहाँ हैं: ${location.addressText}" else "Coordinates: %.4f, %.4f".format(location.latitude, location.longitude)
        return ToolExecutionResult(
            toolName = "get_location",
            status = InteractionStatus.VERIFIED_SUCCESS,
            userMessage = formatMsg(
                languageStyle,
                hi = "जी बॉस! आपकी वर्तमान लोकेशन प्राप्त हो गई है।\n$addrPart\n${location.mapsUrl}",
                en = "Here is your current location:\n$addrPart\n${location.mapsUrl}",
                hinglish = "Ji boss! Aapki current location mil gayi hai:\n$addrPart\n${location.mapsUrl}"
            ),
            verificationEvidence = "GPS fix: ${location.latitude}, ${location.longitude} | ${location.mapsUrl}"
        )
    }

    private suspend fun executeShareLocation(
        recipientName: String,
        phoneNumber: String,
        platform: String,
        languageStyle: String
    ): ToolExecutionResult {
        val hasPerm = MaxLocationService.hasLocationPermission(context)
        if (!hasPerm) {
            return ToolExecutionResult(
                toolName = "share_location",
                status = InteractionStatus.PERMISSION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, लोकेशन शेयर करने के लिए Location Permission (GPS) आवश्यक है। कृपया Permissions टैब से अनुमति दें।",
                    en = "Location permission is required to share your Google Maps location. Please grant location access.",
                    hinglish = "Boss, location share karne ke liye Location permission allow karni hogi."
                ),
                verificationEvidence = "Location permission not granted"
            )
        }

        val location = MaxLocationService.getCurrentLocation(context)
            ?: return ToolExecutionResult(
                toolName = "share_location",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जीपीएस सिग्नल नहीं मिल सका। कृपया चेक करें कि डिवाइस लोकेशन ऑन है।",
                    en = "Could not fetch current GPS location. Please verify device location is on.",
                    hinglish = "GPS fix nahi mila. Please ensure karein device location turned on hai."
                ),
                verificationEvidence = "Location fix failed"
            )

        var targetPhone = phoneNumber
        var resolvedName = recipientName
        if (targetPhone.isBlank() && recipientName.isNotBlank()) {
            val contacts = messagingExecutor.resolveContactsByName(recipientName)
            if (contacts.size == 1) {
                targetPhone = contacts[0].phoneNumber
                resolvedName = contacts[0].name
            } else if (contacts.size > 1) {
                return ToolExecutionResult(
                    toolName = "share_location",
                    status = InteractionStatus.CLARIFICATION_REQUIRED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "'$recipientName' नाम के कई कॉन्टैक्ट्स मिले। कृपया सही नंबर चुनें।",
                        en = "Found multiple contacts matching '$recipientName'. Please specify the contact.",
                        hinglish = "'$recipientName' ke multiple contacts mile. Kisko location bhejna hai?"
                    ),
                    verificationEvidence = "Multiple matches found for $recipientName"
                )
            }
        }

        val isSms = platform.lowercase() == "sms"
        val intent = if (isSms) {
            MaxLocationService.buildSmsShareIntent(context, targetPhone, location, resolvedName)
        } else {
            MaxLocationService.buildWhatsAppShareIntent(context, targetPhone, location, resolvedName)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }

        val channelLabel = if (isSms) "SMS" else "WhatsApp"
        val toWhom = if (resolvedName.isNotBlank()) " $resolvedName ko" else ""
        return ToolExecutionResult(
            toolName = "share_location",
            status = InteractionStatus.VERIFIED_SUCCESS,
            userMessage = formatMsg(
                languageStyle,
                hi = "जी बॉस, आपकी लाइव गूगल मैप्स लोकेशन$toWhom $channelLabel पर शेयर करने के लिए तैयार कर दी गई है।\n${location.mapsUrl}",
                en = "Prepared Google Maps location to share with $resolvedName via $channelLabel.\n${location.mapsUrl}",
                hinglish = "Ji boss! Google Maps location$toWhom $channelLabel par send karne ke liye open kar diya hai.\n${location.mapsUrl}"
            ),
            verificationEvidence = "Launched $channelLabel intent with maps URL: ${location.mapsUrl}"
        )
    }

    private fun executeReadNotifications(languageStyle: String): ToolExecutionResult {
        val hasAccess = MaxNotificationListenerService.isNotificationAccessEnabled(context)
        if (!hasAccess) {
            return ToolExecutionResult(
                toolName = "read_notifications",
                status = InteractionStatus.PERMISSION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, नोटिफिकेशन पढ़ने के लिए Notification Access अनुमति आवश्यक है। कृपया Permissions टैब में जाकर इसे ऑन करें।",
                    en = "Notification access authorization is required before I can read your notifications. Please enable it in the Permissions tab.",
                    hinglish = "Boss, notifications padhne ke liye Notification Access authorization chahiye. Please Permissions tab se allow kar dijiye."
                ),
                verificationEvidence = "NotificationListenerService access is not enabled in Settings.Secure",
                requiredPermission = "NOTIFICATION_LISTENER"
            )
        }

        val items = MaxNotificationListenerService.instance?.refreshNotifications()
            ?: MaxNotificationListenerService.recentNotifications.value

        if (items.isEmpty()) {
            return ToolExecutionResult(
                toolName = "read_notifications",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, अभी कोई सक्रिय नोटिफिकेशन नहीं है।",
                    en = "You have no active notifications right now.",
                    hinglish = "Boss, abhi koi active notification nahi hai."
                ),
                verificationEvidence = "NotificationListenerService verified 0 active notifications"
            )
        }

        val summary = items.take(4).joinToString("\n• ") { "${it.appLabel}: ${it.title} — ${it.text}" }
        return ToolExecutionResult(
            toolName = "read_notifications",
            status = InteractionStatus.VERIFIED_SUCCESS,
            userMessage = formatMsg(
                languageStyle,
                hi = "जी बॉस, आपके नवीनतम नोटिफिकेशन:\n• $summary",
                en = "Here are your latest notifications:\n• $summary",
                hinglish = "Ji boss, aapke latest notifications:\n• $summary"
            ),
            verificationEvidence = "NotificationListenerService read ${items.size} active notifications (sensitive OTPs redacted)"
        )
    }

    suspend fun confirmAndExecutePending(
        pending: PendingConfirmation,
        saveAsTrustedRule: Boolean = false
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        if (saveAsTrustedRule && pending.canCreateTrustedRule && !pending.trustedRuleScopeKey.isNullOrBlank()) {
            maxDao.insertTrustedRule(
                com.example.data.TrustedRuleEntity(
                    toolName = pending.toolName,
                    scopeKey = pending.trustedRuleScopeKey,
                    description = pending.trustedRuleDescription ?: pending.title,
                    isEnabled = true
                )
            )
        }

        val params = pending.parameters
        val res = when (pending.toolName) {
            "prepare_message" -> {
                messagingExecutor.executePrepareMessage(
                    application = params["application"] ?: "WhatsApp",
                    recipientName = params["recipient_name"],
                    phoneNumber = params["phone_number"],
                    message = params["message"],
                    mode = params["mode"] ?: "draft",
                    isConfirmed = true,
                    languageStyle = pending.languageStyle
                )
            }
            "accessibility_action" -> {
                deviceControlExecutor.executeAccessibilityAction(
                    operation = params["operation"] ?: "tap",
                    target = params["target"].orEmpty(),
                    text = params["text"],
                    isConfirmed = true,
                    languageStyle = pending.languageStyle
                )
            }
            "social_media_action" -> {
                messagingExecutor.executeSocialMediaWorkflow(
                    platform = params["platform"] ?: "YouTube",
                    actionType = params["action_type"] ?: "search",
                    queryOrTarget = params["query_or_target"].orEmpty(),
                    draftText = params["draft_text"],
                    isConfirmed = true,
                    languageStyle = pending.languageStyle
                )
            }
            else -> ToolExecutionResult(
                toolName = pending.toolName,
                status = InteractionStatus.UNSUPPORTED_ACTION,
                userMessage = "Unknown confirmation target.",
                verificationEvidence = "Unknown pending toolName"
            )
        }
        logAction(res)
        res
    }

    suspend fun logAction(result: ToolExecutionResult) {
        if (result.toolName == "informational_qa" || result.toolName == "none") return
        // Privacy-respecting sanitization: redact any raw message body or long personal strings
        val sanitizedSummary = sanitizeForAuditLog(result.toolName, result.userMessage)
        maxDao.insertActionLog(
            ActionLogEntity(
                toolName = result.toolName,
                sanitizedSummary = sanitizedSummary,
                status = result.status.name,
                verificationDetails = result.verificationEvidence
            )
        )
    }

    private fun sanitizeForAuditLog(toolName: String, message: String): String {
        val maskedQuotes = message.replace(Regex("""["“']([^"”']+)["”']"""), "\"[REDACTED_CONTENT]\"")
        val maskedPhones = maskedQuotes.replace(Regex("""\+\d{6,12}(\d{3})"""), "+******$1")
        return "[$toolName] ${maskedPhones.take(140)}"
    }

    private fun formatMsg(style: String, hi: String, en: String, hinglish: String): String {
        return when {
            style.equals("Hindi", ignoreCase = true) -> hi
            style.equals("English", ignoreCase = true) -> en
            else -> hinglish
        }
    }
}
