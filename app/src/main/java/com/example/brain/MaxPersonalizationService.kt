package com.example.brain

import com.example.data.MaxPreferencesRepository
import com.example.data.MaxUserPreferences
import com.example.data.MemoryItemEntity
import java.util.Locale

/**
 * Learning & Personalization Engine (`userPreferencesService` — Section 4).
 *
 * Responsibilities:
 * - Learns from explicit user preferences, user corrections, repeated formatting preferences,
 *   preferred language, explanation level, and message feedback ("helpful", "not_helpful", "incorrect").
 * - Never claims to retrain Gemini or modify its model weights automatically; implements personalization
 *   strictly through persistent memory, conversation context, and retrieval.
 * - Allows users to inspect and reset personalization in Settings.
 */
class MaxPersonalizationService(
    private val preferencesRepo: MaxPreferencesRepository,
    private val memoryService: MaxMemoryService
) {

    /**
     * Analyzes a user utterance for explicit preference updates, corrections, or explanation-style signals
     * and updates persistent memories / preferences when adaptive learning is enabled.
     */
    suspend fun observeAndLearnFromUserInput(
        userInput: String,
        prefs: MaxUserPreferences
    ): LearnedPreferenceSignal? {
        if (!prefs.adaptiveLearningEnabled || !prefs.persistentMemoryConsent) return null
        val lower = userInput.trim().lowercase(Locale.US)

        // 1. User Corrections ("No, I prefer English", "Actually reply in Hindi", "Nahi, mujhe Hindi mein chahiye")
        val isCorrection = lower.startsWith("no,") || lower.startsWith("actually") ||
            lower.startsWith("nahi,") || lower.contains("correction:") ||
            lower.contains("instead of") || lower.contains("don't use") || lower.contains("mat bolo")

        if (isCorrection && (lower.contains("hindi") || lower.contains("english") || lower.contains("hinglish"))) {
            val targetLang = when {
                lower.contains("hinglish") -> "Hinglish"
                lower.contains("hindi") -> "Hindi"
                else -> "English"
            }
            preferencesRepo.updatePreferredLanguage(targetLang)
            memoryService.saveOrUpdateMemory(
                factText = "User prefers responses in $targetLang",
                explicitCategory = BrainMemoryCategory.CORRECTION.displayName,
                sourceType = "Correction",
                isCorrection = true
            )
            return LearnedPreferenceSignal(
                updatedLanguage = targetLang,
                updatedExplanationLevel = null,
                summary = "Updated preferred language to $targetLang based on correction"
            )
        }

        // 2. Consistent simple / beginner-friendly explanation signals
        if (lower.contains("simple hindi") || lower.contains("aasan hindi") ||
            lower.contains("beginner friendly") || lower.contains("explain like i am 5") ||
            lower.contains("aasan bhasha mein")
        ) {
            if (lower.contains("always") || lower.contains("hamesha") || lower.contains("remember")) {
                preferencesRepo.updateExplanationLevel("Beginner-Friendly")
                if (lower.contains("hindi")) {
                    preferencesRepo.updatePreferredLanguage("Hindi")
                }
                memoryService.saveOrUpdateMemory(
                    factText = if (lower.contains("hindi")) {
                        "User prefers simple, beginner-friendly Hindi explanations"
                    } else {
                        "User prefers simple, beginner-friendly explanations"
                    },
                    explicitCategory = BrainMemoryCategory.LANGUAGE_AND_STYLE.displayName,
                    sourceType = "Inferred"
                )
            }
        }

        // 3. Step-by-step code / technical explanation preference
        if ((lower.contains("step by step") || lower.contains("step-by-step")) &&
            (lower.contains("always") || lower.contains("hamesha") || lower.contains("prefer") || lower.contains("code"))
        ) {
            preferencesRepo.updateExplanationLevel("Detailed Step-by-Step")
            memoryService.saveOrUpdateMemory(
                factText = "User prefers step-by-step explanations and structured code breakdowns",
                explicitCategory = BrainMemoryCategory.LANGUAGE_AND_STYLE.displayName,
                sourceType = "Inferred"
            )
            return LearnedPreferenceSignal(
                updatedLanguage = null,
                updatedExplanationLevel = "Detailed Step-by-Step",
                summary = "Adapted explanation level to Detailed Step-by-Step"
            )
        }

        return null
    }

    /**
     * Records user feedback on an AI response ("helpful", "not_helpful", "incorrect")
     * and stores a calibration note in memory if the user marked a response as not helpful or incorrect.
     */
    suspend fun recordResponseFeedback(
        messageText: String,
        feedbackType: String,
        prefs: MaxUserPreferences
    ) {
        if (!prefs.adaptiveLearningEnabled || !prefs.persistentMemoryConsent) return
        when (feedbackType) {
            "not_helpful" -> {
                memoryService.saveOrUpdateMemory(
                    factText = "User marked a previous response as not helpful; prefer clearer, more direct and practical answers.",
                    explicitCategory = BrainMemoryCategory.CORRECTION.displayName,
                    sourceType = "Feedback",
                    isCorrection = true
                )
            }
            "incorrect" -> {
                memoryService.saveOrUpdateMemory(
                    factText = "User flagged a previous answer for accuracy; verify facts carefully and distinguish assumptions from verified facts.",
                    explicitCategory = BrainMemoryCategory.CORRECTION.displayName,
                    sourceType = "Feedback",
                    isCorrection = true
                )
            }
        }
    }

    /**
     * Builds the active personalization directive injected into the Brain's reasoning prompt.
     */
    fun buildPersonalizationPromptDirective(
        prefs: MaxUserPreferences,
        memories: List<MemoryItemEntity>,
        effectiveLanguage: String
    ): String = buildString {
        appendLine("[PERSONALIZATION & USER LEARNING PROFILE]:")
        appendLine("- Target Response Language: $effectiveLanguage")
        appendLine("- Explanation Depth & Formatting Level: ${prefs.explanationLevel}")
        when (prefs.explanationLevel) {
            "Beginner-Friendly" -> appendLine("  -> Explain technical concepts in simple, everyday beginner-friendly language with relatable examples.")
            "Detailed Step-by-Step" -> appendLine("  -> Break down complex tasks and code step by step with clear numbered sections.")
            "Concise Direct" -> appendLine("  -> Keep answers brief, direct, and to the point without fluff.")
            else -> appendLine("  -> Give direct concise answers for simple requests and structured detailed answers for complex tasks.")
        }
        val corrections = memories.filter { it.category == BrainMemoryCategory.CORRECTION.displayName }
        if (corrections.isNotEmpty()) {
            appendLine("- Active User Corrections & Calibration:")
            corrections.take(5).forEach { corr ->
                appendLine("  * ${corr.memoryFact}")
            }
        }
    }
}

data class LearnedPreferenceSignal(
    val updatedLanguage: String?,
    val updatedExplanationLevel: String?,
    val summary: String
)
