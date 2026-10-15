package com.example.brain

import com.example.data.AttachmentPayload
import com.example.data.InteractionStatus
import com.example.data.MaxPreferencesRepository
import com.example.data.MaxUserPreferences
import com.example.data.MemoryItemEntity
import com.example.data.ToolExecutionResult
import com.example.tools.MaxCommandRouter

/**
 * Centralized AI Brain Service (`BrainService` — Core Intelligence Engine, Intent Routing,
 * Conversation Context, Memory Orchestration, Personalization, and Gemini API / Tool Orchestration).
 *
 * Orchestrates every user message (both typed and voice-transcribed) through the 7-stage pipeline:
 * 1. Read current user message & normalize informal Hindi / English / Hinglish.
 * 2. Retrieve active session context window + rolling summary + follow-up references (`conversationService`).
 * 3. Retrieve account-isolated long-term memories (`memoryService`).
 * 4. Retrieve & update user preferences / adaptive style (`personalizationService`).
 * 5. Classify intent across the 15 structured `BrainIntentType` categories (`classifyBrainIntent`).
 * 6. Generate high-quality context-aware responses via Gemini API or execute real Android tools (`commandRouter`).
 * 7. Persist new conversation messages and learn useful long-term facts/preferences when permitted.
 */
open class BrainService(
    memoryService: MaxMemoryService,
    conversationService: MaxConversationService,
    personalizationService: MaxPersonalizationService,
    private val preferencesRepo: MaxPreferencesRepository
) : MaxBrainService(
    memoryService = memoryService,
    conversationService = conversationService,
    personalizationService = personalizationService,
    preferencesRepo = preferencesRepo
) {

    /**
     * Full centralized orchestration result returned by [processTurn].
     */
    data class BrainTurnOutcome(
        val result: ToolExecutionResult,
        val effectiveLanguage: String,
        val brainIntent: BrainIntentType,
        val contextSnapshot: ConversationContextSnapshot,
        val activeMemories: List<MemoryItemEntity>
    )

    /**
     * Central entry point that orchestrates communication between conversation history,
     * user preferences, long-term memory, deterministic Android tools, and the Gemini API,
     * ensuring all responses are context-aware and multilingual (Hindi, English, Hinglish).
     */
    suspend fun processTurn(
        rawInput: String,
        sessionId: String,
        prefs: MaxUserPreferences,
        commandRouter: MaxCommandRouter,
        attachment: AttachmentPayload? = null
    ): BrainTurnOutcome {
        val trimmed = rawInput.trim()
        val accountKey = memoryService.currentAccountKey()

        // 1. Build conversation context snapshot (recent turns, rolling summary, follow-up detection)
        val contextSnapshot = conversationService.buildConversationContextSnapshot(
            sessionId = sessionId,
            currentUserMessage = trimmed,
            accountKey = accountKey
        )

        // 2. Retrieve active long-term memories for current user account
        val activeMemories = memoryService.getActiveMemoriesForCurrentUser()

        // 3. Observe & learn from user preferences / corrections
        personalizationService.observeAndLearnFromUserInput(trimmed, prefs)

        // 4. Classify Brain Intent across the 15 structured categories
        val brainIntent = classifyBrainIntent(
            rawInput = trimmed,
            contextSnapshot = contextSnapshot,
            attachment = attachment
        )

        // 5. Resolve effective response language (explicit request -> follow-up continuity -> stored memory -> settings -> input script)
        val detectedInputStyle = commandRouter.detectLanguageStyle(
            trimmed.ifBlank { "Analyze ${attachment?.fileName}" },
            "Auto (Match User)"
        )
        val effectiveLanguage = resolveEffectiveLanguage(
            userInput = trimmed,
            detectedInputLanguage = detectedInputStyle,
            prefs = prefs,
            storedMemories = activeMemories,
            contextSnapshot = contextSnapshot
        )

        // 6. Execute direct Brain memory/history intents or route to Gemini API & Android tools
        val directBrainResult = if (attachment == null) {
            handleDirectBrainMemoryOrHistoryIntent(
                rawInput = trimmed,
                intentType = brainIntent,
                contextSnapshot = contextSnapshot,
                prefs = prefs,
                detectedLanguage = effectiveLanguage
            )
        } else {
            null
        }

        val finalResult = if (directBrainResult != null) {
            commandRouter.logAction(directBrainResult)
            directBrainResult
        } else {
            val refreshedMemories = memoryService.getActiveMemoriesForCurrentUser()
            val personalizationDirective = personalizationService.buildPersonalizationPromptDirective(
                prefs = prefs,
                memories = refreshedMemories,
                effectiveLanguage = effectiveLanguage
            )
            commandRouter.processUserCommand(
                rawInput = trimmed,
                conversationHistory = contextSnapshot.recentTurns,
                preferredLanguageSetting = effectiveLanguage,
                attachment = attachment,
                customPersonality = prefs.assistantPersonality,
                persistentMemoryConsent = prefs.persistentMemoryConsent,
                storedMemories = refreshedMemories.map { "[${it.category}] ${it.memoryFact}" },
                webSearchGroundingEnabled = prefs.webSearchGroundingEnabled,
                codingAssistantMode = prefs.codingAssistantMode,
                voiceStylePreset = prefs.voiceStylePreset,
                personalizationDirective = personalizationDirective,
                preclassifiedBrainIntent = brainIntent,
                contextSnapshot = contextSnapshot
            )
        }

        return BrainTurnOutcome(
            result = finalResult,
            effectiveLanguage = effectiveLanguage,
            brainIntent = brainIntent,
            contextSnapshot = contextSnapshot,
            activeMemories = activeMemories
        )
    }
}
