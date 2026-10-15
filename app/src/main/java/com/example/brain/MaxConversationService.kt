package com.example.brain

import com.example.data.ChatMessageEntity
import com.example.data.ConversationSessionEntity
import com.example.data.MaxDao
import java.util.Locale

/**
 * Conversation Context & Session Engine (`conversationService` — Section 2).
 *
 * Responsibilities:
 * - Maintains separate conversation sessions and keeps the right context per session.
 * - Supports continuing old conversations and creating new conversation sessions.
 * - Builds a context-window-aware rolling summary when conversations grow beyond the recent turn window,
 *   preserving important user requirements without sending the entire database history to Gemini.
 * - Resolves references such as "woh", "usko", "pehle wala", "same as before", "aur bhi aasan", and "continue".
 * - Answers "What did I ask you earlier?" accurately using real stored messages (never inventing history).
 */
class MaxConversationService(
    private val maxDao: MaxDao
) {

    companion object {
        const val RECENT_CONTEXT_WINDOW_TURNS = 10
        private val FOLLOW_UP_MARKERS = listOf(
            "woh", "wo ", "usko", "usey", "usse", "uske", "pehle wala", "pichla",
            "same as before", "wahi", "dobara", "continue", "aage batao", "aur batao",
            "aur bhi aasan", "aasan karke", "aur simple", "in simple", "simplify",
            "shorter", "chhota karo", "expand", "detail mein", "example do",
            "give an example", "translate it", "isko ", "isey ", "iska ", "iski ",
            "why", "kyun", "kaise", "how does it", "what about", "aur ", "phir "
        )
    }

    /**
     * Ensures the active session record exists in Room.
     */
    suspend fun ensureSessionExists(
        sessionId: String,
        accountKey: String = "local_guest"
    ): ConversationSessionEntity {
        val existing = maxDao.getSessionById(sessionId)
        if (existing != null) return existing
        val created = ConversationSessionEntity(
            sessionId = sessionId,
            title = "New Conversation",
            rollingSummary = "",
            lastActiveTopic = "",
            messageCount = 0,
            userAccountKey = accountKey
        )
        maxDao.upsertSession(created)
        return created
    }

    /**
     * Builds the Conversation Context Window for the Brain before calling Gemini or local reasoning.
     * Avoids sending the entire database history; instead combines a compact rolling summary of older turns
     * with the most recent turns.
     */
    suspend fun buildConversationContextSnapshot(
        sessionId: String,
        currentUserMessage: String,
        accountKey: String = "local_guest"
    ): ConversationContextSnapshot {
        val session = ensureSessionExists(sessionId, accountKey)
        val allSessionMessages = maxDao.getMessagesBySessionSnapshot(sessionId)
            .filter { it.toolInvoked != "system_init" }

        // Exclude the user message that was just inserted if it matches the tail
        val priorMessages = if (allSessionMessages.lastOrNull()?.isUser == true &&
            allSessionMessages.lastOrNull()?.text?.trim() == currentUserMessage.trim()
        ) {
            allSessionMessages.dropLast(1)
        } else {
            allSessionMessages
        }

        val recentMessages = priorMessages.takeLast(RECENT_CONTEXT_WINDOW_TURNS)
        val olderMessages = if (priorMessages.size > RECENT_CONTEXT_WINDOW_TURNS) {
            priorMessages.dropLast(RECENT_CONTEXT_WINDOW_TURNS)
        } else {
            emptyList()
        }

        val rollingSummary = if (olderMessages.isNotEmpty()) {
            summarizeOlderMessages(olderMessages, session.rollingSummary)
        } else {
            session.rollingSummary
        }

        val lastUserMessage = priorMessages.lastOrNull { it.isUser }?.text
        val lastAssistantMessage = priorMessages.lastOrNull { !it.isUser }?.text
        val isFollowUp = detectFollowUpReference(currentUserMessage, priorMessages.isNotEmpty())

        return ConversationContextSnapshot(
            sessionId = sessionId,
            sessionTitle = session.title,
            rollingSummary = rollingSummary,
            lastActiveTopic = session.lastActiveTopic.ifBlank { inferTopicFromText(lastUserMessage ?: currentUserMessage) },
            recentTurns = recentMessages.map { it.isUser to it.text },
            priorUserQuestions = priorMessages.filter { it.isUser }.map { it.text },
            lastUserQuery = lastUserMessage,
            lastAssistantReply = lastAssistantMessage,
            isFollowUpToRecentTurn = isFollowUp
        )
    }

    /**
     * Updates session metadata (title, rolling summary, active topic, message count) after a turn completes.
     */
    suspend fun updateSessionAfterTurn(
        sessionId: String,
        userMessage: String,
        assistantReply: String,
        accountKey: String = "local_guest"
    ) {
        val existing = ensureSessionExists(sessionId, accountKey)
        val allSessionMessages = maxDao.getMessagesBySessionSnapshot(sessionId)
            .filter { it.toolInvoked != "system_init" }

        val updatedTitle = if (existing.title == "New Conversation" || existing.title.isBlank()) {
            generateConciseTitle(userMessage)
        } else {
            existing.title
        }

        val updatedTopic = inferTopicFromText(userMessage).ifBlank { existing.lastActiveTopic }
        val olderMessages = if (allSessionMessages.size > RECENT_CONTEXT_WINDOW_TURNS) {
            allSessionMessages.dropLast(RECENT_CONTEXT_WINDOW_TURNS)
        } else {
            emptyList()
        }
        val updatedSummary = if (olderMessages.isNotEmpty()) {
            summarizeOlderMessages(olderMessages, existing.rollingSummary)
        } else {
            existing.rollingSummary
        }

        maxDao.upsertSession(
            existing.copy(
                title = updatedTitle,
                rollingSummary = updatedSummary,
                lastActiveTopic = updatedTopic,
                messageCount = allSessionMessages.size,
                userAccountKey = accountKey,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Detects whether the user message contains anaphoric/follow-up references
     * ("woh", "usko", "pehle wala", "same as before", "aur bhi aasan karke samjhao", "continue", etc.).
     */
    fun detectFollowUpReference(userInput: String, hasPriorHistory: Boolean): Boolean {
        if (!hasPriorHistory) return false
        val lower = " ${userInput.trim().lowercase(Locale.US)} "
        if (FOLLOW_UP_MARKERS.any { lower.contains(it) }) return true
        // Very short follow-up requests like "aur?", "example?", "in Hindi?", "step by step?"
        if (userInput.trim().split(Regex("\\s+")).size <= 5 &&
            (lower.contains("hindi") || lower.contains("english") || lower.contains("example") ||
                lower.contains("aasan") || lower.contains("simple") || lower.contains("short") ||
                lower.contains("detail"))
        ) {
            return true
        }
        return false
    }

    private fun summarizeOlderMessages(
        olderMessages: List<ChatMessageEntity>,
        existingSummary: String
    ): String {
        val userPoints = olderMessages
            .filter { it.isUser }
            .takeLast(6)
            .map { it.text.replace("\n", " ").take(90) }
        if (userPoints.isEmpty()) return existingSummary
        return "Earlier user topics in this session: ${userPoints.joinToString(" | ")}"
    }

    private fun generateConciseTitle(firstUserMessage: String): String {
        val clean = firstUserMessage
            .replace(Regex("📎.*\\n"), "")
            .replace("\n", " ")
            .trim()
        if (clean.isEmpty()) return "Conversation"
        return if (clean.length <= 36) clean else "${clean.take(34).trim()}…"
    }

    private fun inferTopicFromText(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return ""
        return clean.take(80)
    }
}

data class ConversationContextSnapshot(
    val sessionId: String,
    val sessionTitle: String,
    val rollingSummary: String,
    val lastActiveTopic: String,
    val recentTurns: List<Pair<Boolean, String>>,
    val priorUserQuestions: List<String>,
    val lastUserQuery: String?,
    val lastAssistantReply: String?,
    val isFollowUpToRecentTurn: Boolean
)
