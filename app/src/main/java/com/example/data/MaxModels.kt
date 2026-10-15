package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class InteractionStatus {
    VERIFIED_SUCCESS,
    PENDING_CONFIRMATION,
    CLARIFICATION_REQUIRED,
    PERMISSION_REQUIRED,
    EXECUTION_FAILED,
    UNSUPPORTED_ACTION,
    INFO_RESPONSE,
    CANCELLED
}

@Serializable
enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    CANCELLED
}

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String = "New Conversation",
    val rollingSummary: String = "",
    val lastActiveTopic: String = "",
    val messageCount: Int = 0,
    val userAccountKey: String = "local_guest",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String = "default_session",
    val isUser: Boolean,
    val text: String,
    val detectedLanguage: String = "Hinglish", // "Hindi", "English", "Hinglish"
    val intentCategory: String = "GENERAL_CHAT",
    val toolInvoked: String? = null,
    val status: String = InteractionStatus.INFO_RESPONSE.name,
    val verificationEvidence: String? = null,
    val userFeedback: String? = null, // "helpful", "not_helpful", "incorrect", or null
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val sanitizedSummary: String,
    val status: String, // InteractionStatus.name
    val verificationDetails: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "trusted_rules")
data class TrustedRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val scopeKey: String, // e.g., "whatsapp:+919876543210" or "social:youtube_search"
    val description: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scheduled_items")
data class ScheduledItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeText: String,
    val dateText: String?,
    val label: String,
    val recurrence: String?,
    val isVerified: Boolean = true,
    val triggerAtMillis: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_memories")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryKey: String = "", // Canonical key e.g., "preferred_language", "ongoing_project", "custom_fact"
    val memoryFact: String,
    val category: String = "Preference", // Preference, Language & Style, Project & Goal, Important Fact, Conversation Summary, Correction
    val sourceType: String = "Explicit", // Explicit, Inferred, Correction, Manual
    val userAccountKey: String = "local_guest",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AttachmentPayload(
    val fileName: String,
    val mimeType: String,
    val base64Data: String,
    val extractedTextPreview: String = "",
    val sizeBytes: Long = 0L
)

@Serializable
data class PendingConfirmation(
    val id: String,
    val toolName: String,
    val title: String,
    val summary: String,
    val parameters: Map<String, String>,
    val riskReason: String,
    val languageStyle: String = "Hinglish",
    val canCreateTrustedRule: Boolean = false,
    val trustedRuleScopeKey: String? = null,
    val trustedRuleDescription: String? = null
)

@Serializable
data class AppCandidate(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

@Serializable
data class DisambiguationPrompt(
    val originalSpokenName: String,
    val candidates: List<AppCandidate>,
    val languageStyle: String = "Hinglish"
)

@Serializable
data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

@Serializable
data class ActiveNotificationItem(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long
)

@Serializable
data class ToolExecutionResult(
    val toolName: String,
    val status: InteractionStatus,
    val userMessage: String,
    val verificationEvidence: String,
    val emotionalTone: String = "everyday_conversation",
    val spokenSummary: String? = null,
    val brainIntentCategory: String = "GENERAL_CHAT",
    val pendingConfirmation: PendingConfirmation? = null,
    val disambiguationPrompt: DisambiguationPrompt? = null,
    val requiredPermission: String? = null
)
