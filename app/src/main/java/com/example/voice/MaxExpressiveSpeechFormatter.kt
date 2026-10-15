package com.example.voice

import java.util.Locale

/**
 * Supported emotional tones for MAX AI's expressive human-like voice engine.
 * Emotion is inferred from the actual conversation context or Gemini's structured intent,
 * and can also be calibrated via user voice style presets.
 */
enum class VoiceEmotion(
    val id: String,
    val displayLabel: String,
    val badgeEmoji: String,
    val pitchMultiplier: Float,
    val rateMultiplier: Float,
    val interSentencePauseMs: Long,
    val geminiStyleDirective: String
) {
    EVERYDAY_CONVERSATION(
        id = "everyday_conversation",
        displayLabel = "Everyday Conversation",
        badgeEmoji = "💬",
        pitchMultiplier = 1.0f,
        rateMultiplier = 0.98f,
        interSentencePauseMs = 180L,
        geminiStyleDirective = "Speak like a real, warm, relatable human in a relaxed, effortless conversational cadence. Include subtle natural breathing pauses, authentic voice inflections, and native Indian accent for Hindi and Hinglish:"
    ),
    HAPPY(
        id = "happy",
        displayLabel = "Happy & Warm",
        badgeEmoji = "😊",
        pitchMultiplier = 1.05f,
        rateMultiplier = 1.02f,
        interSentencePauseMs = 150L,
        geminiStyleDirective = "Speak with genuine human warmth, friendly smile in the voice, upbeat energy, and lifelike emotional expression with natural Indian cadence:"
    ),
    SAD(
        id = "sad",
        displayLabel = "Gentle & Caring",
        badgeEmoji = "💙",
        pitchMultiplier = 0.95f,
        rateMultiplier = 0.88f,
        interSentencePauseMs = 260L,
        geminiStyleDirective = "Speak gently, softly, with heartfelt empathy, quiet care, and patient unhurried pauses:"
    ),
    EMPATHETIC(
        id = "empathetic",
        displayLabel = "Empathetic",
        badgeEmoji = "🤝",
        pitchMultiplier = 0.98f,
        rateMultiplier = 0.92f,
        interSentencePauseMs = 230L,
        geminiStyleDirective = "Speak in a compassionate, reassuring, sincere, and deeply understanding conversational tone with gentle pacing:"
    ),
    EXCITED(
        id = "excited",
        displayLabel = "Excited",
        badgeEmoji = "✨",
        pitchMultiplier = 1.08f,
        rateMultiplier = 1.05f,
        interSentencePauseMs = 130L,
        geminiStyleDirective = "Speak enthusiastically with vibrant excitement, lifelike sparkle, and natural conversational momentum:"
    ),
    SERIOUS(
        id = "serious",
        displayLabel = "Serious & Composed",
        badgeEmoji = "🎯",
        pitchMultiplier = 0.97f,
        rateMultiplier = 0.95f,
        interSentencePauseMs = 190L,
        geminiStyleDirective = "Speak in a grounded, articulate, confident, and professional human tone with crisp articulation:"
    ),
    STORYTELLING(
        id = "storytelling",
        displayLabel = "Storytelling",
        badgeEmoji = "📖",
        pitchMultiplier = 1.01f,
        rateMultiplier = 0.92f,
        interSentencePauseMs = 300L,
        geminiStyleDirective = "Narrate with captivating emotional range, rich atmospheric pauses, and natural storytelling intonation:"
    ),
    ROMANTIC_STORYTELLING(
        id = "romantic_storytelling",
        displayLabel = "Romantic Storytelling",
        badgeEmoji = "🌙",
        pitchMultiplier = 0.96f,
        rateMultiplier = 0.88f,
        interSentencePauseMs = 320L,
        geminiStyleDirective = "Deliver in a soft, velvety, warm, and intimate narrative style with poetic pauses and gentle expression:"
    ),
    HUMOROUS(
        id = "humorous",
        displayLabel = "Playful & Humorous",
        badgeEmoji = "😄",
        pitchMultiplier = 1.04f,
        rateMultiplier = 1.01f,
        interSentencePauseMs = 160L,
        geminiStyleDirective = "Speak in a witty, lighthearted, cheerful tone with natural comedic timing and playful inflection:"
    ),
    URGENT(
        id = "urgent",
        displayLabel = "Urgent & Direct",
        badgeEmoji = "⚡",
        pitchMultiplier = 1.02f,
        rateMultiplier = 1.08f,
        interSentencePauseMs = 100L,
        geminiStyleDirective = "Speak briskly, directly, and with focused clarity and immediate urgency:"
    );

    companion object {
        fun fromId(raw: String?): VoiceEmotion {
            if (raw.isNullOrBlank()) return EVERYDAY_CONVERSATION
            val clean = raw.trim().lowercase(Locale.US).replace(" ", "_").replace("-", "_")
            return entries.firstOrNull { it.id == clean || it.name.lowercase(Locale.US) == clean }
                ?: when {
                    clean.contains("romant") -> ROMANTIC_STORYTELLING
                    clean.contains("story") || clean.contains("narrat") -> STORYTELLING
                    clean.contains("empath") || clean.contains("reassur") || clean.contains("support") -> EMPATHETIC
                    clean.contains("sad") || clean.contains("sorrow") || clean.contains("grief") -> SAD
                    clean.contains("excit") || clean.contains("enthusias") -> EXCITED
                    clean.contains("happy") || clean.contains("cheer") || clean.contains("warm") -> HAPPY
                    clean.contains("humor") || clean.contains("funny") || clean.contains("playful") || clean.contains("witty") -> HUMOROUS
                    clean.contains("urgent") || clean.contains("alert") || clean.contains("emergency") -> URGENT
                    clean.contains("serious") || clean.contains("calm") || clean.contains("professional") -> SERIOUS
                    else -> EVERYDAY_CONVERSATION
                }
        }
    }
}

/**
 * User-selectable Voice Style Presets in MAX AI Settings & Quick Controls.
 */
enum class VoiceStylePreset(
    val id: String,
    val displayTitle: String,
    val subtitle: String,
    val defaultGeminiVoice: String,
    val pitchOffset: Float,
    val rateOffset: Float,
    val defaultEmotionOverride: VoiceEmotion? = null
) {
    AUTO_ADAPTIVE(
        id = "Auto (Context-Aware)",
        displayTitle = "Auto (Emotionally Adaptive)",
        subtitle = "Automatically adapts tone, warmth, and pacing to conversation context",
        defaultGeminiVoice = "Puck",
        pitchOffset = 0.0f,
        rateOffset = 0.0f,
        defaultEmotionOverride = null
    ),
    WARM_COMPANION(
        id = "Warm Companion",
        displayTitle = "Warm Companion",
        subtitle = "Friendly, empathetic, and comforting conversational partner",
        defaultGeminiVoice = "Aoede",
        pitchOffset = 0.02f,
        rateOffset = -0.03f,
        defaultEmotionOverride = VoiceEmotion.EMPATHETIC
    ),
    CALM_PROFESSIONAL(
        id = "Calm & Composed",
        displayTitle = "Calm & Composed",
        subtitle = "Clear, grounded, confident delivery for coding & daily tasks",
        defaultGeminiVoice = "Charon",
        pitchOffset = -0.04f,
        rateOffset = -0.02f,
        defaultEmotionOverride = VoiceEmotion.SERIOUS
    ),
    LIVELY_EXPRESSIVE(
        id = "Energetic & Lively",
        displayTitle = "Energetic & Lively",
        subtitle = "Upbeat, bright, and enthusiastic conversational presence",
        defaultGeminiVoice = "Fenrir",
        pitchOffset = 0.05f,
        rateOffset = 0.05f,
        defaultEmotionOverride = VoiceEmotion.HAPPY
    ),
    STORYTELLER(
        id = "Expressive Storyteller",
        displayTitle = "Expressive Storyteller",
        subtitle = "Rich narrative cadence with thoughtful pauses",
        defaultGeminiVoice = "Kore",
        pitchOffset = 0.0f,
        rateOffset = -0.05f,
        defaultEmotionOverride = VoiceEmotion.STORYTELLING
    );

    companion object {
        fun fromId(id: String?): VoiceStylePreset {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.displayTitle.equals(id, ignoreCase = true)
            } ?: AUTO_ADAPTIVE
        }
    }
}

/**
 * A single prosody-modulated utterance chunk for natural sentence-by-sentence speech synthesis.
 */
data class ExpressiveSpeechChunk(
    val text: String,
    val pitch: Float,
    val rate: Float,
    val postPauseMs: Long,
    val emotion: VoiceEmotion
)

/**
 * Prepares AI responses for ultra-natural spoken output:
 * 1. Strips raw Markdown formatting, fenced code blocks, JSON payloads, and technical verification logs.
 * 2. Converts mathematical expressions, symbols, and URLs into natural spoken Hindi/English/Hinglish phrasing.
 * 3. Infers contextual emotion from user input & assistant reply when not explicitly tagged.
 * 4. Segments speech into natural thought chunks with subtle sentence-level prosody variation and pauses.
 */
object MaxExpressiveSpeechFormatter {

    /**
     * Infers the most natural contextual [VoiceEmotion] from the conversation turn without forcing emotion
     * onto neutral commands.
     */
    fun inferContextualEmotion(
        userText: String,
        assistantReply: String,
        explicitEmotionTag: String? = null,
        stylePreset: VoiceStylePreset = VoiceStylePreset.AUTO_ADAPTIVE
    ): VoiceEmotion {
        if (!explicitEmotionTag.isNullOrBlank()) {
            val parsed = VoiceEmotion.fromId(explicitEmotionTag)
            if (parsed != VoiceEmotion.EVERYDAY_CONVERSATION ||
                explicitEmotionTag.contains("everyday", ignoreCase = true)
            ) {
                return parsed
            }
        }

        val combined = "$userText $assistantReply".lowercase(Locale.US)
        val userLower = userText.lowercase(Locale.US)

        return when {
            // Romantic storytelling context
            (combined.contains("romantic") || combined.contains("love story") ||
                combined.contains("pyaar ki kahani") || combined.contains("mohabbat") ||
                combined.contains("poem for love") || combined.contains("shayari")) &&
                (combined.contains("story") || combined.contains("kahani") || combined.contains("poem") ||
                    combined.contains("shayari") || assistantReply.length > 100) ->
                VoiceEmotion.ROMANTIC_STORYTELLING

            // General Storytelling / Narration context
            userLower.contains("tell me a story") || userLower.contains("kahani sunao") ||
                userLower.contains("ek kahani") || userLower.contains("once upon a time") ||
                combined.contains("ek samay ki baat") || combined.contains("long ago") ->
                VoiceEmotion.STORYTELLING

            // Sad / grief / loss context
            userLower.contains("feeling sad") || userLower.contains("very sad") ||
                userLower.contains("depressed") || userLower.contains("udaas") ||
                userLower.contains("mann udaas") || userLower.contains("heartbroken") ||
                userLower.contains("lost my") || userLower.contains("passed away") ||
                userLower.contains("dukhi") ->
                VoiceEmotion.SAD

            // Empathetic / stressed / anxious / struggling context
            userLower.contains("stressed") || userLower.contains("anxious") ||
                userLower.contains("worried") || userLower.contains("pareshan") ||
                userLower.contains("tension") || userLower.contains("help me calm") ||
                userLower.contains("tired of") || userLower.contains("thak gaya") ||
                assistantReply.lowercase(Locale.US).contains("main samajh sakta hoon") ||
                assistantReply.lowercase(Locale.US).contains("i understand how") ||
                assistantReply.lowercase(Locale.US).contains("chinta mat karo") ->
                VoiceEmotion.EMPATHETIC

            // Urgent / emergency / immediate action context
            userLower.contains("urgent") || userLower.contains("emergency") ||
                userLower.contains("jaldi karo") || userLower.contains("turant") ||
                userLower.contains("immediately") || userLower.contains("asap") ->
                VoiceEmotion.URGENT

            // Humorous / joke / witty context
            userLower.contains("joke") || userLower.contains("chutkula") ||
                userLower.contains("funny") || userLower.contains("make me laugh") ||
                userLower.contains("hasa do") || userLower.contains("roast") ->
                VoiceEmotion.HUMOROUS

            // Excited / celebration / breakthrough context
            userLower.contains("awesome") || userLower.contains("amazing news") ||
                userLower.contains("i got the job") || userLower.contains("passed the exam") ||
                userLower.contains("party") || userLower.contains("badhai") ||
                assistantReply.contains("🎉") || assistantReply.contains("Congratulations!") ->
                VoiceEmotion.EXCITED

            // Happy / warm greeting / gratitude context
            userLower.contains("thank you") || userLower.contains("shukriya") ||
                userLower.contains("dhanyawad") || userLower.contains("good morning") ||
                userLower.contains("suprabhat") || userLower.contains("kya haal hai") ||
                userLower.contains("kaise ho") ->
                VoiceEmotion.HAPPY

            // Serious / technical / security / coding explanation
            userLower.contains("explain") || userLower.contains("architecture") ||
                userLower.contains("algorithm") || userLower.contains("security") ||
                assistantReply.contains("```") ->
                stylePreset.defaultEmotionOverride ?: VoiceEmotion.SERIOUS

            else -> stylePreset.defaultEmotionOverride ?: VoiceEmotion.EVERYDAY_CONVERSATION
        }
    }

    /**
     * Cleans raw text so MAX AI never reads out code blocks, raw JSON keys, markdown asterisks,
     * URLs with protocol slashes, or technical audit logs aloud.
     */
    fun sanitizeForNaturalSpeech(rawText: String, languageStyle: String = "Hinglish"): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // 1. Remove "🌐 Web Sources:" citation footer block from spoken output
        val webSourcesIdx = text.indexOf("🌐 Web Sources:")
        if (webSourcesIdx != -1) {
            text = text.substring(0, webSourcesIdx).trim()
        }

        // 2. Replace fenced code blocks with a brief, natural spoken summary instead of reading syntax aloud
        val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")
        if (codeBlockRegex.containsMatchIn(text)) {
            val codeNotice = when {
                languageStyle.equals("Hindi", ignoreCase = true) ->
                    " मैंने कोड चैट स्क्रीन पर लिख दिया है। "
                languageStyle.equals("English", ignoreCase = true) ->
                    " I've shared the code block on your screen. "
                else ->
                    " Maine code screen par share kar diya hai. "
            }
            var replacedFirst = false
            text = codeBlockRegex.replace(text) {
                if (!replacedFirst) {
                    replacedFirst = true
                    codeNotice
                } else {
                    " "
                }
            }
        }

        // 3. Remove raw inline JSON objects like {"action":"CALL_CONTACT",...}
        text = text.replace(Regex("""\{[^{}]*"action"\s*:[^{}]*\}"""), " ")

        // 4. Convert URLs to natural spoken domain references ("https://developer.android.com/jetpack" -> "developer.android.com")
        text = text.replace(Regex("""https?://(www\.)?([a-zA-Z0-9.-]+)(/[^\s)]*)?""")) { match ->
            val domain = match.groupValues[2]
            domain
        }

        // 5. Strip Markdown links [title](url) -> title
        text = text.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")

        // 6. Strip inline code ticks, bold/italic markers, and markdown headings
        text = text.replace(Regex("""`([^`]+)`"""), "$1")
        text = text.replace("**", "")
        text = text.replace("__", "")
        text = text.replace(Regex("""(?m)^#{1,6}\s+"""), "")
        text = text.replace(Regex("""(?m)^\s*[-*•]\s+"""), "")

        // 7. Convert mathematical expressions and technical operators into natural spoken phrasing
        text = convertMathAndTechnicalTokens(text, languageStyle)

        // 8. Strip decorative/technical emojis so TTS doesn't say "sparkles" or "check mark button"
        text = text.replace(
            Regex("""[📎📄🌐✅❌⚠️🔒🛡️🎯✨💬😊💙🤝📖🌙😄⚡🎉🔊📞🔍]"""),
            " "
        )

        // 9. Optimize conversational contractions & Indian terms for authentic cadence
        text = polishConversationalPronunciation(text, languageStyle)

        // 10. Normalize whitespace while preserving punctuation for natural pauses
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Polishes common spoken Indian/Hinglish contractions and phrasing so Android TTS
     * synthesizers pronounce words smoothly without robotic stumbles.
     */
    private fun polishConversationalPronunciation(input: String, languageStyle: String): String {
        var text = input
        // Ensure proper spacing around punctuation for natural breathing pauses
        text = text.replace(" ,", ",")
            .replace(" .", ".")
            .replace(" !", "!")
            .replace(" ?", "?")
            .replace("...", ", ")

        // Smooth out common tech abbreviations for spoken clarity
        text = text.replace(Regex("""\bOK\b""", RegexOption.IGNORE_CASE), "Okay")
            .replace(Regex("""\bAI\b"""), "A.I.")
            .replace(Regex("""\bMAXI\b"""), "Maxi")
            .replace(Regex("""\bMAX\b"""), "Max")
            .replace(Regex("""\bGPS\b"""), "G.P.S.")
            .replace(Regex("""\bSMS\b"""), "S.M.S.")
            .replace(Regex("""\bURL\b"""), "U.R.L.")
            .replace(Regex("""\bFAQ\b"""), "F.A.Q.")

        return text
    }

    /**
     * Converts common Romanized Hinglish conversational words to authentic phonetic forms or Devanagari
     * so Android's Hindi neural voice engine pronounces them with native Indian cadence, correct vowel lengths,
     * and fluent tone.
     */
    fun adaptForIndianNeuralVoice(text: String): String {
        if (text.isBlank()) return text
        var result = text

        // If the text is purely English, keep it in natural English
        val hasDevanagari = result.any { it in '\u0900'..'\u097F' }
        val hindiMarkers = listOf(
            "hai", "hain", "hoon", "aap", "aapka", "aapki", "aapke", "mera", "meri", "karo", "karna", "karta",
            "karenge", "shukriya", "dhanyawad", "namaste", "bilkul", "theek", "thik", "kaise", "kya", "kyun",
            "batao", "bataiye", "chalo", "nahi", "nahin", "samajh", "bhai", "dost", "madad", "chinta", "taiyaar"
        )
        val lower = result.lowercase(Locale.US)
        val matchesHindi = hindiMarkers.count { lower.contains(Regex("""\b$it\b""")) } >= 2

        if (!hasDevanagari && matchesHindi) {
            // Transform key Romanized conversational markers to Devanagari for native Indian TTS synthesis
            val replacements = listOf(
                Regex("""\bNamaste\b""", RegexOption.IGNORE_CASE) to "नमस्ते",
                Regex("""\bShukriya\b""", RegexOption.IGNORE_CASE) to "शुक्रिया",
                Regex("""\bDhanyawad\b""", RegexOption.IGNORE_CASE) to "धन्यवाद",
                Regex("""\bBilkul\b""", RegexOption.IGNORE_CASE) to "बिल्कुल",
                Regex("""\bTheek hai\b""", RegexOption.IGNORE_CASE) to "ठीक है",
                Regex("""\bThik hai\b""", RegexOption.IGNORE_CASE) to "ठीक है",
                Regex("""\bHaan\b""", RegexOption.IGNORE_CASE) to "हाँ",
                Regex("""\bHaanji\b""", RegexOption.IGNORE_CASE) to "हाँजी",
                Regex("""\bChalo\b""", RegexOption.IGNORE_CASE) to "चलो",
                Regex("""\bChinta mat karo\b""", RegexOption.IGNORE_CASE) to "चिंता मत करो",
                Regex("""\bSuniye\b""", RegexOption.IGNORE_CASE) to "सुनिए",
                Regex("""\bBataiye\b""", RegexOption.IGNORE_CASE) to "बताइए",
                Regex("""\bBatao\b""", RegexOption.IGNORE_CASE) to "बताओ",
                Regex("""\bMadad\b""", RegexOption.IGNORE_CASE) to "मदद",
                Regex("""\bLocation\b""", RegexOption.IGNORE_CASE) to "लोकेशन",
                Regex("""\bShare kar diya\b""", RegexOption.IGNORE_CASE) to "शेयर कर दिया",
                Regex("""\bShare kar raha hoon\b""", RegexOption.IGNORE_CASE) to "शेयर कर रहा हूँ"
            )
            for ((regex, rep) in replacements) {
                result = result.replace(regex, rep)
            }
        }
        return result
    }

    private fun convertMathAndTechnicalTokens(input: String, languageStyle: String): String {
        var result = input
        val isHindi = languageStyle.equals("Hindi", ignoreCase = true)
        // Replace math operators surrounded by digits (e.g., "25 * 4 = 100", "10 / 2", "5 + 3")
        result = result.replace(Regex("""(\d+)\s*\*\s*(\d+)"""), "$1 multiplied by $2")
        result = result.replace(Regex("""(\d+)\s*×\s*(\d+)"""), "$1 multiplied by $2")
        result = result.replace(Regex("""(\d+)\s*÷\s*(\d+)"""), "$1 divided by $2")
        result = result.replace(Regex("""(\d+)\s*/\s*(\d+)"""), "$1 by $2")
        result = result.replace(Regex("""(\d+)\s*\+\s*(\d+)"""), "$1 plus $2")
        result = result.replace(
            Regex("""(\d+)\s*=\s*(\d+)"""),
            if (isHindi) "$1 बराबर $2" else "$1 equals $2"
        )
        result = result.replace("->", " to ")
        result = result.replace(">=", " greater than or equal to ")
        result = result.replace("<=", " less than or equal to ")
        result = result.replace("!=", " not equal to ")
        return result
    }

    /**
     * Breaks sanitized spoken text into natural conversational thought units with clause-level
     * breathing pauses, subtle micro-intonation, and prosody modulation so Android TTS sounds
     * warm, effortless, and lifelike rather than flat or robotic.
     */
    fun buildExpressiveSpeechChunks(
        rawText: String,
        languageStyle: String,
        emotion: VoiceEmotion,
        stylePreset: VoiceStylePreset,
        basePitch: Float,
        baseRate: Float,
        preferMaleVoice: Boolean
    ): List<ExpressiveSpeechChunk> {
        val cleaned = sanitizeForNaturalSpeech(rawText, languageStyle)
        if (cleaned.isBlank()) return emptyList()

        // 1. First split by major sentence boundaries (., !, ?, ।, ;)
        val rawSentences = cleaned
            .split(Regex("""(?<=[.!?।;])\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawSentences.isEmpty()) return emptyList()

        val targetBasePitch = (basePitch * emotion.pitchMultiplier + stylePreset.pitchOffset)
            .let { if (preferMaleVoice) it.coerceIn(0.78f, 1.12f) else it.coerceIn(0.80f, 1.35f) }
        val targetBaseRate = (baseRate * emotion.rateMultiplier + stylePreset.rateOffset)
            .coerceIn(0.75f, 1.25f)

        val chunks = mutableListOf<ExpressiveSpeechChunk>()

        rawSentences.forEachIndexed { sentIndex, sentence ->
            val isQuestion = sentence.endsWith("?")
            val isExclamation = sentence.endsWith("!")
            val isOpening = sentIndex == 0 && sentence.length < 50
            val isConcluding = sentIndex == rawSentences.lastIndex

            // If a sentence has multiple long clauses separated by commas or dashes, break into thought units
            val clauses = if (sentence.length > 55 && (sentence.contains(",") || sentence.contains(" — ") || sentence.contains(" - "))) {
                sentence.split(Regex("""(?<=,)\s+|(?<=\s—\s)|(?<=\s-\s)"""))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                listOf(sentence)
            }

            clauses.forEachIndexed { clauseIndex, clause ->
                val isLastClauseInSentence = clauseIndex == clauses.lastIndex

                // Natural human micro-intonation
                val pitchDelta = when {
                    isQuestion && isLastClauseInSentence -> 0.035f // Natural upward inquiry inflection
                    isExclamation && isLastClauseInSentence -> 0.03f
                    isOpening && clauseIndex == 0 -> 0.025f // Warm melodic greeting lift
                    isConcluding && isLastClauseInSentence -> -0.02f // Grounded, calming resolution
                    else -> 0.0f
                }

                val rateDelta = when {
                    clause.length > 100 -> -0.03f // Slightly calmer pacing on intricate explanations
                    isOpening && clauseIndex == 0 -> 0.01f
                    else -> 0.0f
                }

                // Breathing pause duration:
                // Commas get subtle natural breathing breaks (80-110ms); full sentences get 160-240ms
                val pauseMs = when {
                    isConcluding && isLastClauseInSentence -> 0L
                    !isLastClauseInSentence -> 95L // Human micro-breath at clause juncture
                    isQuestion || isExclamation -> emotion.interSentencePauseMs + 50L
                    else -> emotion.interSentencePauseMs
                }

                chunks.add(
                    ExpressiveSpeechChunk(
                        text = clause,
                        pitch = (targetBasePitch + pitchDelta).coerceIn(0.75f, 1.35f),
                        rate = (targetBaseRate + rateDelta).coerceIn(0.75f, 1.35f),
                        postPauseMs = pauseMs,
                        emotion = emotion
                    )
                )
            }
        }

        return chunks
    }
}
