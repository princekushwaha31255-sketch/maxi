package com.example.brain

import com.example.data.AttachmentPayload
import com.example.data.InteractionStatus
import com.example.data.MaxPreferencesRepository
import com.example.data.MaxUserPreferences
import com.example.data.MemoryItemEntity
import com.example.data.ToolExecutionResult
import java.util.Locale

/**
 * Centralized Conversational AI Brain Service (`brainService` — Sections 1, 2, 3, 4, 5, 6, 8, 9, 10).
 *
 * Orchestrates every user message (both typed and voice-transcribed) through:
 * 1. Reading current user message & normalizing spelling / informal Hindi-English-Hinglish.
 * 2. Retrieving active session context window + rolling summary (`conversationService`).
 * 3. Retrieving account-isolated long-term memories (`memoryService`).
 * 4. Learning from explicit preferences / corrections (`userPreferencesService`).
 * 5. Classifying structured intent across the 15 BrainIntentType categories (`intentRouter`).
 *    - Handles `MEMORY_SAVE` ("Remember that I prefer Hindi responses")
 *    - Handles `MEMORY_RECALL` ("What do you remember about me?", "What did I ask you earlier?")
 *    - Handles `MEMORY_DELETE` ("Forget my language preference")
 *    - Handles `FOLLOW_UP` ("Aur bhi aasan karke samjhao", "woh", "usko", "pehle wala", "same as before", "continue")
 * 6. Generating high-quality context-aware responses or routing validated Android tools (`responseService` & `toolService`).
 */
open class MaxBrainService(
    val memoryService: MaxMemoryService,
    val conversationService: MaxConversationService,
    val personalizationService: MaxPersonalizationService,
    private val preferencesRepo: MaxPreferencesRepository
) {

    /**
     * Classifies a user message into one of the 15 structured `BrainIntentType` categories
     * before or alongside Gemini reasoning.
     */
    fun classifyBrainIntent(
        rawInput: String,
        contextSnapshot: ConversationContextSnapshot,
        attachment: AttachmentPayload? = null
    ): BrainIntentType {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase(Locale.US)

        if (trimmed.isEmpty() && attachment == null) {
            return BrainIntentType.NEEDS_CLARIFICATION
        }

        // 1. Explicit Memory Save ("Remember that...", "Remember this:", "Yaad rakhna ki...")
        if (isExplicitMemorySaveRequest(lower)) {
            return BrainIntentType.MEMORY_SAVE
        }

        // 2. Explicit Memory Delete ("Forget my language preference", "Forget that...", "Forget this", "Bhool jao...")
        if (isExplicitMemoryDeleteRequest(lower)) {
            return BrainIntentType.MEMORY_DELETE
        }

        // 3. Explicit Memory / Conversation Recall ("What do you remember about me?", "What did I ask you earlier?")
        if (isMemoryOrHistoryRecallRequest(lower)) {
            return BrainIntentType.MEMORY_RECALL
        }

        // 4. Calling / Speaker Voice Commands ("Call Shivam", "Shivam ko call karo", "Speaker on karo")
        val callingParsed = com.example.tools.CallingAndSpeakerExecutor.parseCallingOrSpeakerCommand(trimmed)
        if (callingParsed != null) {
            return when (callingParsed) {
                is com.example.tools.ParsedVoiceCallingCommand.CallContact -> BrainIntentType.CALL_CONTACT
                else -> BrainIntentType.VOICE_COMMAND
            }
        }

        // 5. Follow-Up Resolution ("Aur bhi aasan karke samjhao", "woh", "usko", "pehle wala", "same as before", "continue")
        if (contextSnapshot.isFollowUpToRecentTurn) {
            return BrainIntentType.FOLLOW_UP
        }

        // 6. Translation
        if (lower.startsWith("translate ") || lower.contains("translate into") ||
            lower.contains("translate to ") || lower.contains("mein translate karo") ||
            lower.contains("anuvad karo") || lower.contains("meaning in hindi") ||
            lower.contains("meaning in english")
        ) {
            return BrainIntentType.TRANSLATION
        }

        // 7. Summarization
        if (lower.startsWith("summarize") || lower.contains("summary of") ||
            lower.contains("short summary") || lower.contains("saaransh") ||
            lower.contains("summarize karo") || lower.contains("tl;dr")
        ) {
            return BrainIntentType.SUMMARIZATION
        }

        // 8. Coding
        if (lower.contains("write code") || lower.contains("kotlin") || lower.contains("python") ||
            lower.contains("javascript") || lower.contains("react") || lower.contains("typescript") ||
            lower.contains("function") || lower.contains("bug") || lower.contains("compile") ||
            lower.contains("algorithm") || lower.contains("sql") || lower.contains("html") ||
            lower.contains("code likho")
        ) {
            return BrainIntentType.CODING
        }

        // 9. Creative Writing
        if (lower.contains("story") || lower.contains("kahani") || lower.contains("poem") ||
            lower.contains("shayari") || lower.contains("essay") || lower.contains("script") ||
            lower.contains("write a letter") || lower.contains("email likho")
        ) {
            return BrainIntentType.CREATIVE_WRITING
        }

        // 10. Explanation
        if (lower.startsWith("explain ") || lower.contains("samjhao") ||
            lower.contains("samjhaiye") || lower.contains("how does ") ||
            lower.contains("kaise kaam karta") || lower.contains("elaborate")
        ) {
            return BrainIntentType.EXPLANATION
        }

        // 11. Question Answering
        if (lower.startsWith("what ") || lower.startsWith("why ") || lower.startsWith("who ") ||
            lower.startsWith("when ") || lower.startsWith("where ") || lower.startsWith("which ") ||
            lower.contains("kya hai") || lower.contains("kaun hai") || lower.contains("kab ") ||
            lower.endsWith("?")
        ) {
            return BrainIntentType.QUESTION_ANSWERING
        }

        return BrainIntentType.GENERAL_CHAT
    }

    /**
     * Intercepts and executes direct Brain intents (`MEMORY_SAVE`, `MEMORY_DELETE`, `MEMORY_RECALL`)
     * deterministically against the real database so they work 100% reliably with zero hallucination.
     * Returns null if the message should proceed to tool execution or Gemini reasoning.
     */
    suspend fun handleDirectBrainMemoryOrHistoryIntent(
        rawInput: String,
        intentType: BrainIntentType,
        contextSnapshot: ConversationContextSnapshot,
        prefs: MaxUserPreferences,
        detectedLanguage: String
    ): ToolExecutionResult? {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase(Locale.US)

        when (intentType) {
            BrainIntentType.MEMORY_SAVE -> {
                val extractedFact = extractFactToRemember(trimmed)
                if (extractedFact.isBlank()) {
                    return ToolExecutionResult(
                        toolName = "memory_save",
                        status = InteractionStatus.CLARIFICATION_REQUIRED,
                        userMessage = when (detectedLanguage) {
                            "Hindi" -> "जी बॉस, आप क्या याद रखवाना चाहते हैं? कृपया स्पष्ट बताइए (जैसे: 'Remember that I prefer Hindi responses')।"
                            "English" -> "What would you like me to remember? Please share the preference or fact you want saved."
                            else -> "Boss, aap kya remember karwana chahte hain? Please woh fact ya preference bataiye."
                        },
                        verificationEvidence = "Memory save requested without fact payload",
                        brainIntentCategory = BrainIntentType.NEEDS_CLARIFICATION.name
                    )
                }

                if (!prefs.persistentMemoryConsent) {
                    // Automatically enable consent when user explicitly commands "Remember that..."
                    preferencesRepo.updatePersistentMemoryConsent(true)
                }

                // Also check if the remembered fact sets a language preference ("Remember that I prefer Hindi responses")
                val factLower = extractedFact.lowercase(Locale.US)
                var updatedLangPreference: String? = null
                if (factLower.contains("prefer") || factLower.contains("response") ||
                    factLower.contains("reply") || factLower.contains("language") ||
                    factLower.contains("bhasha") || factLower.contains("jawab")
                ) {
                    when {
                        factLower.contains("hinglish") -> {
                            updatedLangPreference = "Hinglish"
                            preferencesRepo.updatePreferredLanguage("Hinglish")
                        }
                        factLower.contains("hindi") || factLower.contains("हिंदी") -> {
                            updatedLangPreference = "Hindi"
                            preferencesRepo.updatePreferredLanguage("Hindi")
                        }
                        factLower.contains("english") -> {
                            updatedLangPreference = "English"
                            preferencesRepo.updatePreferredLanguage("English")
                        }
                    }
                }

                val saveOutcome = memoryService.saveOrUpdateMemory(
                    factText = extractedFact,
                    sourceType = "Explicit"
                )

                if (!saveOutcome.saved) {
                    return ToolExecutionResult(
                        toolName = "memory_save",
                        status = InteractionStatus.UNSUPPORTED_ACTION,
                        userMessage = when (detectedLanguage) {
                            "Hindi" -> "सुरक्षा नियमों के कारण मैं पासवर्ड, OTP या संवेदनशील गोपनीय जानकारी मेमोरी में सेव नहीं करता।"
                            "English" -> "For your security, MAX AI does not save passwords, OTPs, payment credentials, or sensitive secrets in memory."
                            else -> "Security policy ke tahat main passwords, OTPs, ya sensitive credentials memory mein save nahi karta."
                        },
                        verificationEvidence = saveOutcome.reason,
                        brainIntentCategory = BrainIntentType.MEMORY_SAVE.name
                    )
                }

                val replyLang = updatedLangPreference ?: detectedLanguage
                val replyText = when (replyLang) {
                    "Hindi" -> "जी बॉस, मैंने यह मेमोरी में सुरक्षित रूप से सेव कर लिया है: **\"${saveOutcome.memoryFact}\"**। आगे की बातचीत में मैं इसका ध्यान रखूँगा।"
                    "English" -> "Got it! I've saved this to your long-term memory: **\"${saveOutcome.memoryFact}\"** (${saveOutcome.category}). I'll keep this in mind across future conversations."
                    else -> "Done! Maine aapki long-term memory mein ye save kar liya hai: **\"${saveOutcome.memoryFact}\"** (${saveOutcome.category}). Agli conversations mein bhi main iska dhyan rakhunga."
                }
                return ToolExecutionResult(
                    toolName = "memory_save",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = replyText,
                    verificationEvidence = saveOutcome.reason,
                    emotionalTone = "happy",
                    brainIntentCategory = BrainIntentType.MEMORY_SAVE.name
                )
            }

            BrainIntentType.MEMORY_DELETE -> {
                val deleteOutcome = memoryService.deleteMatchingMemories(trimmed)
                // If user asked to forget language preference, also reset preferredLanguage to Auto (Match User)
                if (lower.contains("language") || lower.contains("hindi") ||
                    lower.contains("english") || lower.contains("hinglish") || lower.contains("bhasha") ||
                    deleteOutcome.deletedFacts.any {
                        val fl = it.lowercase(Locale.US)
                        fl.contains("hindi") || fl.contains("english") || fl.contains("hinglish") || fl.contains("language")
                    }
                ) {
                    preferencesRepo.updatePreferredLanguage("Auto (Match User)")
                }

                val replyText = if (deleteOutcome.deletedCount > 0) {
                    val deletedJoined = deleteOutcome.deletedFacts.joinToString("; ") { "\"$it\"" }
                    when (detectedLanguage) {
                        "Hindi" -> "जी बॉस, मैंने आपकी मेमोरी से यह जानकारी हटा दी है: $deletedJoined। अब आगे इसका उपयोग नहीं किया जाएगा।"
                        "English" -> "Done. I have forgotten and deleted the following from your stored memory: $deletedJoined. It will no longer be used."
                        else -> "Ji boss, maine aapki stored memory se ye delete kar diya hai: $deletedJoined. Ab aage iska use nahi hoga."
                    }
                } else {
                    when (detectedLanguage) {
                        "Hindi" -> "मेमोरी में इससे जुड़ी कोई सेव की गई जानकारी नहीं मिली। यदि आपने भाषा की प्राथमिकता सेट की थी, तो उसे भी डिफ़ॉल्ट (Auto) पर रीसेट कर दिया गया है।"
                        "English" -> "No matching stored memory record was found, and any language preference override has been reset to Auto."
                        else -> "Isse milta-julta koi saved memory record nahi mila, aur language preference ko Auto par reset kar diya gaya hai."
                    }
                }

                return ToolExecutionResult(
                    toolName = "memory_delete",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = replyText,
                    verificationEvidence = "Deleted ${deleteOutcome.deletedCount} memory record(s) from Room database",
                    brainIntentCategory = BrainIntentType.MEMORY_DELETE.name
                )
            }

            BrainIntentType.MEMORY_RECALL -> {
                // Distinguish between "What did I ask you earlier?" (Conversation History Recall)
                // and "What do you remember about me?" (Long-Term Memory Recall)
                val isAskingEarlierConversation = lower.contains("ask you earlier") ||
                    lower.contains("asked you earlier") || lower.contains("asked earlier") ||
                    lower.contains("what did i ask") || lower.contains("what did we talk") ||
                    lower.contains("pehle kya pucha") || lower.contains("maine kya pucha") ||
                    lower.contains("humne kya baat ki") || lower.contains("earlier in this chat") ||
                    lower.contains("previous question") || lower.contains("pichla sawal")

                if (isAskingEarlierConversation) {
                    val priorUserQuestions = contextSnapshot.priorUserQuestions
                    val replyText = if (priorUserQuestions.isNotEmpty()) {
                        val recentQuestionsList = priorUserQuestions.takeLast(5)
                            .mapIndexed { idx, q -> "${idx + 1}. \"$q\"" }
                            .joinToString("\n")
                        when (detectedLanguage) {
                            "Hindi" -> "इस बातचीत में आपने मुझसे पहले यह पूछा था:\n$recentQuestionsList"
                            "English" -> "Earlier in this conversation, you asked me:\n$recentQuestionsList"
                            else -> "Is conversation mein aapne mujhse pehle ye pucha tha:\n$recentQuestionsList"
                        }
                    } else {
                        when (detectedLanguage) {
                            "Hindi" -> "इस बातचीत के सेशन में इससे पहले कोई पिछला प्रश्न उपलब्ध नहीं है। यह इस सेशन का पहला प्रश्न है।"
                            "English" -> "There is no earlier question stored in this conversation session yet—this is the start of our current chat."
                            else -> "Is conversation session mein isse pehle koi previous question available nahi hai—ye hamari current chat ki shuruat hai."
                        }
                    }
                    return ToolExecutionResult(
                        toolName = "conversation_recall",
                        status = InteractionStatus.INFO_RESPONSE,
                        userMessage = replyText,
                        verificationEvidence = "Retrieved ${priorUserQuestions.size} actual user question(s) from session ${contextSnapshot.sessionId}",
                        brainIntentCategory = BrainIntentType.MEMORY_RECALL.name
                    )
                }

                // Otherwise: Long-Term Stored Memories Recall ("What do you remember about me?")
                val activeMemories = memoryService.getActiveMemoriesForCurrentUser()
                val replyText = if (activeMemories.isNotEmpty()) {
                    val bulletList = activeMemories.take(12).joinToString("\n") {
                        "• **${it.category}**: ${it.memoryFact}"
                    }
                    when (detectedLanguage) {
                        "Hindi" -> "यहाँ वह जानकारी है जो आपने मुझे याद रखने की अनुमति दी है:\n\n$bulletList\n\nआप कभी भी 'Forget ...' कहकर या Settings में जाकर किसी भी मेमोरी को हटा सकते हैं।"
                        "English" -> "Here is what I currently have saved in your long-term memory:\n\n$bulletList\n\nYou can say \"Forget [topic]\" anytime or manage these in Settings."
                        else -> "Aapki long-term memory mein abhi ye facts aur preferences saved hain:\n\n$bulletList\n\nAap kabhi bhi \"Forget [topic]\" bolkar ya Settings mein jaakar inhe edit ya delete kar sakte hain."
                    }
                } else {
                    when (detectedLanguage) {
                        "Hindi" -> "अभी आपकी लॉन्ग-टर्म मेमोरी में कोई जानकारी सेव नहीं है। आप मुझसे 'Remember that I prefer Hindi responses' जैसा कुछ भी कहकर याद रखवा सकते हैं।"
                        "English" -> "I don't have any saved long-term memories about you right now. You can ask me to remember something anytime—for example: \"Remember that I prefer Hindi responses.\""
                        else -> "Abhi aapki long-term memory mein koi saved fact nahi hai. Aap kabhi bhi \"Remember that I prefer Hindi responses\" bolkar preference save karwa sakte hain."
                    }
                }
                return ToolExecutionResult(
                    toolName = "memory_recall",
                    status = InteractionStatus.INFO_RESPONSE,
                    userMessage = replyText,
                    verificationEvidence = "Retrieved ${activeMemories.size} stored memory item(s) for account ${memoryService.currentAccountKey()}",
                    brainIntentCategory = BrainIntentType.MEMORY_RECALL.name
                )
            }

            else -> return null
        }
    }

    /**
     * Resolves the effective response language by honoring:
     * 1. Explicit language requested in the current user prompt (e.g., "in simple Hindi", "in English")
     * 2. Saved long-term memory preference (e.g., Test B: "Remember that I prefer Hindi responses" -> new chat "Explain cloud computing")
     * 3. User's Settings preference (`preferredLanguageStyle`)
     * 4. Detected language of the user's current message
     */
    fun resolveEffectiveLanguage(
        userInput: String,
        detectedInputLanguage: String,
        prefs: MaxUserPreferences,
        storedMemories: List<MemoryItemEntity>,
        contextSnapshot: ConversationContextSnapshot
    ): String {
        val lower = userInput.lowercase(Locale.US)
        // 1. Explicit in-prompt language request
        when {
            lower.contains("in hindi") || lower.contains("hindi mein") || lower.contains("हिंदी में") ||
                lower.contains("simple hindi") || lower.contains("aasan hindi") -> return "Hindi"
            lower.contains("in english") || lower.contains("english mein") -> return "English"
            lower.contains("in hinglish") || lower.contains("hinglish mein") -> return "Hinglish"
        }

        // 2. If this is a follow-up ("Aur bhi aasan karke samjhao") and previous turn was in Hindi, keep Hindi
        if (contextSnapshot.isFollowUpToRecentTurn && contextSnapshot.lastAssistantReply != null) {
            val prevHasDevanagari = contextSnapshot.lastAssistantReply.any { it in '\u0900'..'\u097F' }
            if (prevHasDevanagari && (lower.contains("aasan") || lower.contains("samjhao") || lower.contains("aur "))) {
                return "Hindi"
            }
        }

        // 3. Check stored long-term memory if memory consent is enabled (e.g. Test B)
        if (prefs.persistentMemoryConsent) {
            val rememberedLang = memoryService.resolveRememberedLanguagePreference(storedMemories)
            if (rememberedLang != null) {
                return rememberedLang
            }
        }

        // 4. Settings override
        if (prefs.preferredLanguageStyle in listOf("Hindi", "English", "Hinglish")) {
            return prefs.preferredLanguageStyle
        }

        return detectedInputLanguage
    }

    /**
     * Enriches user input with explicit follow-up reference resolution and rolling session summary
     * so both Gemini and the offline/fallback engine understand what "woh", "usko", "pehle wala",
     * "aur bhi aasan karke samjhao", or "continue" refer to.
     */
    fun buildContextEnrichedPrompt(
        userInput: String,
        contextSnapshot: ConversationContextSnapshot,
        effectiveLanguage: String,
        intentType: BrainIntentType,
        personalizationDirective: String
    ): String = buildString {
        appendLine("User Message: $userInput")
        appendLine("Detected Brain Intent: ${intentType.name}")
        appendLine("Required Response Language: $effectiveLanguage")
        if (contextSnapshot.rollingSummary.isNotBlank()) {
            appendLine("[Earlier Session Summary]: ${contextSnapshot.rollingSummary}")
        }
        if (contextSnapshot.isFollowUpToRecentTurn && !contextSnapshot.lastUserQuery.isNullOrBlank()) {
            appendLine("[Follow-Up Context Resolution]:")
            appendLine("- Previous User Request: ${contextSnapshot.lastUserQuery}")
            contextSnapshot.lastAssistantReply?.take(600)?.let {
                appendLine("- Previous MAX AI Response Excerpt: $it")
            }
            appendLine("-> Interpret pronouns/references ('woh', 'usko', 'pehle wala', 'same as before', 'aur bhi aasan karke samjhao', 'continue') as referring directly to the previous topic above.")
        }
        appendLine(personalizationDirective)
    }

    private fun isExplicitMemorySaveRequest(lower: String): Boolean {
        return lower.startsWith("remember that ") ||
            lower.startsWith("remember this") ||
            lower.startsWith("remember my ") ||
            lower.startsWith("please remember ") ||
            lower.startsWith("save to memory") ||
            lower.contains("yaad rakhna ki ") ||
            lower.contains("yaad rakho ki ") ||
            lower.contains("meri preference save karo")
    }

    private fun isExplicitMemoryDeleteRequest(lower: String): Boolean {
        return lower.startsWith("forget my ") ||
            lower.startsWith("forget that ") ||
            lower.startsWith("forget this") ||
            lower.startsWith("delete my memory") ||
            lower.startsWith("clear my memory") ||
            lower.contains("bhool jao") ||
            lower.contains("memory delete karo") ||
            lower.contains("forget what i told you about")
    }

    private fun isMemoryOrHistoryRecallRequest(lower: String): Boolean {
        return lower.contains("what do you remember about me") ||
            lower.contains("what do you know about me") ||
            lower.contains("show my memories") ||
            lower.contains("mere baare mein kya yaad hai") ||
            lower.contains("kya yaad rakha hai") ||
            lower.contains("what did i ask you earlier") ||
            lower.contains("what did i ask earlier") ||
            lower.contains("what did we talk about earlier") ||
            lower.contains("maine pehle kya pucha") ||
            lower.contains("pehle kya pucha tha")
    }

    private fun extractFactToRemember(input: String): String {
        val cleaned = input.trim()
            .replaceFirst(Regex("""(?i)^(please\s+)?(hey\s+max\s*,?\s*)?(remember\s+that|remember\s+this\s*:?|remember\s+|save\s+to\s+memory\s*:?|yaad\s+rakhna\s+ki|yaad\s+rakho\s+ki)\s*"""), "")
            .trim()
        return if (cleaned.lowercase(Locale.US).startsWith("i ")) {
            "User ${cleaned.substring(2)}"
        } else {
            cleaned
        }
    }
}
