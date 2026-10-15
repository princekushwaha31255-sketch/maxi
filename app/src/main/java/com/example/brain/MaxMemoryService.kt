package com.example.brain

import com.example.data.ConversationSessionEntity
import com.example.data.MaxCloudRepository
import com.example.data.MaxDao
import com.example.data.MemoryItemEntity
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

/**
 * Memory categories required by Section 3 (Long-Term Memory System):
 * - User-approved preferences
 * - Preferred language and response style
 * - Ongoing projects and goals
 * - Important non-sensitive facts the user wants MAX AI to remember
 * - Useful summaries of earlier conversations
 * - Relevant corrections to previously stored preferences
 */
enum class BrainMemoryCategory(val displayName: String) {
    PREFERENCE("User Preference"),
    LANGUAGE_AND_STYLE("Language & Style"),
    PROJECT_AND_GOAL("Ongoing Project & Goal"),
    IMPORTANT_FACT("Important Fact"),
    CONVERSATION_SUMMARY("Conversation Summary"),
    CORRECTION("Preference Correction")
}

/**
 * Structured Intent categories required by Section 5 (Intent Classification & Action Routing):
 * - GENERAL_CHAT
 * - QUESTION_ANSWERING
 * - FOLLOW_UP
 * - EXPLANATION
 * - CODING
 * - CREATIVE_WRITING
 * - TRANSLATION
 * - SUMMARIZATION
 * - MEMORY_SAVE
 * - MEMORY_RECALL
 * - MEMORY_DELETE
 * - VOICE_COMMAND
 * - CALL_CONTACT
 * - APP_ACTION
 * - NEEDS_CLARIFICATION
 */
enum class BrainIntentType {
    GENERAL_CHAT,
    QUESTION_ANSWERING,
    FOLLOW_UP,
    EXPLANATION,
    CODING,
    CREATIVE_WRITING,
    TRANSLATION,
    SUMMARIZATION,
    MEMORY_SAVE,
    MEMORY_RECALL,
    MEMORY_DELETE,
    VOICE_COMMAND,
    CALL_CONTACT,
    APP_ACTION,
    NEEDS_CLARIFICATION;

    companion object {
        fun fromString(raw: String?): BrainIntentType {
            if (raw.isNullOrBlank()) return GENERAL_CHAT
            val clean = raw.trim().uppercase(Locale.US).replace("-", "_").replace(" ", "_")
            return entries.firstOrNull { it.name == clean } ?: GENERAL_CHAT
        }
    }
}

/**
 * Persistent Long-Term Memory Service (`memoryService`).
 * Enforces privacy guardrails (never stores passwords, OTPs, API keys, bank details, or secrets),
 * handles explicit memory save ("Remember this..."), recall ("What do you remember about me?"),
 * deletion ("Forget my language preference" / "Forget this"), and preference corrections,
 * while isolating memories per user account (`userAccountKey`).
 */
class MaxMemoryService(
    private val maxDao: MaxDao,
    private val cloudRepository: MaxCloudRepository? = null
) {

    fun currentAccountKey(): String {
        val uid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
        return if (!uid.isNullOrBlank()) uid else "local_guest"
    }

    /**
     * Privacy guardrail: Blocks saving sensitive credentials, passwords, OTPs, payment numbers, or tokens.
     */
    fun isSafeToStoreAsMemory(candidateFact: String): Boolean {
        val clean = candidateFact.trim()
        if (clean.length < 3 || clean.length > 600) return false
        val lower = clean.lowercase(Locale.US)
        val forbiddenTokens = listOf(
            "password", "passcode", "otp", "cvv", "credit card", "debit card",
            "secret key", "api key", "api_key", "private key", "pin is", "mera pin",
            "mera password", "aadhaar", "ssn", "bank account number", "bearer ", "sk-"
        )
        if (forbiddenTokens.any { lower.contains(it) }) return false
        // Reject raw 12-16 digit card/ID numbers
        if (Regex("""\b\d{12,19}\b""").containsMatchIn(clean)) return false
        return true
    }

    /**
     * Infers canonical memory key so corrections or updates overwrite outdated preferences
     * instead of accumulating conflicting duplicate memories.
     */
    fun inferCanonicalKeyAndCategory(
        factText: String,
        explicitCategory: String? = null,
        isCorrection: Boolean = false
    ): Pair<String, String> {
        val lower = factText.lowercase(Locale.US)
        return when {
            lower.contains("hindi") || lower.contains("english") || lower.contains("hinglish") ||
                lower.contains("language") || lower.contains("bhasha") -> {
                "preferred_language" to if (isCorrection) BrainMemoryCategory.CORRECTION.displayName else BrainMemoryCategory.LANGUAGE_AND_STYLE.displayName
            }
            lower.contains("step by step") || lower.contains("simple") || lower.contains("beginner") ||
                lower.contains("concise") || lower.contains("short") || lower.contains("detailed") ||
                lower.contains("explanation") || lower.contains("format") -> {
                "response_style_preference" to if (isCorrection) BrainMemoryCategory.CORRECTION.displayName else BrainMemoryCategory.LANGUAGE_AND_STYLE.displayName
            }
            lower.contains("project") || lower.contains("building") || lower.contains("working on") ||
                lower.contains("goal") || lower.contains("app") || lower.contains("website") -> {
                "ongoing_project_${factText.take(24).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}" to BrainMemoryCategory.PROJECT_AND_GOAL.displayName
            }
            lower.contains("my name is") || lower.contains("mera naam") || lower.contains("call me ") -> {
                "user_name" to BrainMemoryCategory.IMPORTANT_FACT.displayName
            }
            lower.contains("profession") || lower.contains("developer") || lower.contains("student") ||
                lower.contains("engineer") || lower.contains("designer") -> {
                "user_role" to BrainMemoryCategory.IMPORTANT_FACT.displayName
            }
            !explicitCategory.isNullOrBlank() -> {
                "fact_${factText.take(28).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}" to explicitCategory
            }
            isCorrection -> {
                "correction_${factText.take(24).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}" to BrainMemoryCategory.CORRECTION.displayName
            }
            else -> {
                "memory_${factText.take(28).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}" to BrainMemoryCategory.PREFERENCE.displayName
            }
        }
    }

    /**
     * Saves or updates a durable memory fact for the active user account if authorized and safe.
     */
    suspend fun saveOrUpdateMemory(
        factText: String,
        explicitCategory: String? = null,
        sourceType: String = "Explicit",
        isCorrection: Boolean = false
    ): MemorySaveOutcome {
        val clean = factText.trim().trimEnd('.', '।')
        if (!isSafeToStoreAsMemory(clean)) {
            return MemorySaveOutcome(
                saved = false,
                reason = "Blocked sensitive or invalid memory content (passwords, OTPs, or secrets are never stored)."
            )
        }

        val accountKey = currentAccountKey()
        val (canonicalKey, resolvedCategory) = inferCanonicalKeyAndCategory(clean, explicitCategory, isCorrection)
        val existingForKey = maxDao.findMemoryByKey(canonicalKey, accountKey)
            ?: maxDao.getMemoriesSnapshotForAccount(accountKey).firstOrNull {
                it.memoryKey == canonicalKey || it.memoryFact.equals(clean, ignoreCase = true)
            }

        val now = System.currentTimeMillis()
        return if (existingForKey != null) {
            maxDao.updateMemory(
                id = existingForKey.id,
                updatedFact = clean,
                category = resolvedCategory,
                updatedAt = now
            )
            MemorySaveOutcome(
                saved = true,
                updatedExisting = true,
                memoryFact = clean,
                category = resolvedCategory,
                reason = "Updated existing memory ($canonicalKey) for account $accountKey"
            )
        } else {
            maxDao.insertMemory(
                MemoryItemEntity(
                    memoryKey = canonicalKey,
                    memoryFact = clean,
                    category = resolvedCategory,
                    sourceType = sourceType,
                    userAccountKey = accountKey,
                    createdAt = now,
                    updatedAt = now
                )
            )
            MemorySaveOutcome(
                saved = true,
                updatedExisting = false,
                memoryFact = clean,
                category = resolvedCategory,
                reason = "Stored new memory ($canonicalKey) in Room database for account $accountKey"
            )
        }
    }

    /**
     * Retrieves relevant memories for the current user account.
     * Supports account isolation so memories belonging to different users are never mixed.
     */
    suspend fun getActiveMemoriesForCurrentUser(): List<MemoryItemEntity> {
        val accountKey = currentAccountKey()
        val accountMemories = maxDao.getMemoriesSnapshotForAccount(accountKey)
        if (accountMemories.isNotEmpty()) return accountMemories
        // Also include legacy local_guest memories if user is on local_guest
        return if (accountKey == "local_guest") {
            maxDao.getAllMemoriesSnapshot().filter {
                it.userAccountKey == "local_guest" || it.userAccountKey.isBlank()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Deletes matching memories when the user says e.g. "Forget my language preference",
     * "Forget that I prefer Hindi", or "Forget this".
     * Immediately removes matching records from Room so future retrievals never use them.
     */
    suspend fun deleteMatchingMemories(targetQuery: String): MemoryDeleteOutcome {
        val memories = getActiveMemoriesForCurrentUser()
        if (memories.isEmpty()) {
            return MemoryDeleteOutcome(deletedCount = 0, deletedFacts = emptyList())
        }

        val lowerQuery = targetQuery.lowercase(Locale.US).trim()
        val stopWords = setOf(
            "forget", "delete", "remove", "clear", "my", "that", "this", "preference",
            "memory", "about", "me", "bhool", "jao", "hata", "do", "mera", "meri", "ko", "ki"
        )
        val keywords = lowerQuery
            .split(Regex("""[\s,.:;!?]+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }

        val wantsLanguageDeleted = lowerQuery.contains("language") || lowerQuery.contains("hindi") ||
            lowerQuery.contains("english") || lowerQuery.contains("hinglish") || lowerQuery.contains("bhasha")

        val wantsAllDeleted = lowerQuery.contains("everything") || lowerQuery.contains("all memories") ||
            lowerQuery.contains("sab kuch bhool") || lowerQuery == "forget all"

        if (wantsAllDeleted) {
            val facts = memories.map { it.memoryFact }
            maxDao.clearMemoriesForAccount(currentAccountKey())
            return MemoryDeleteOutcome(deletedCount = facts.size, deletedFacts = facts)
        }

        val matched = memories.filter { item ->
            val factLower = item.memoryFact.lowercase(Locale.US)
            val keyLower = item.memoryKey.lowercase(Locale.US)
            val catLower = item.category.lowercase(Locale.US)
            when {
                wantsLanguageDeleted && (
                    keyLower == "preferred_language" ||
                        catLower.contains("language") ||
                        factLower.contains("hindi") ||
                        factLower.contains("english") ||
                        factLower.contains("hinglish") ||
                        factLower.contains("language")
                    ) -> true
                keywords.isNotEmpty() && keywords.any { kw ->
                    factLower.contains(kw) || keyLower.contains(kw) || catLower.contains(kw)
                } -> true
                else -> false
            }
        }

        val targetsToDelete = if (matched.isNotEmpty()) {
            matched
        } else if (lowerQuery in listOf("forget this", "forget that", "ise bhool jao", "ye bhool jao")) {
            listOfNotNull(memories.firstOrNull())
        } else {
            emptyList()
        }

        targetsToDelete.forEach { item ->
            maxDao.deleteMemory(item.id)
        }

        return MemoryDeleteOutcome(
            deletedCount = targetsToDelete.size,
            deletedFacts = targetsToDelete.map { it.memoryFact }
        )
    }

    /**
     * Checks whether any stored memory specifies a preferred response language ("Hindi", "English", "Hinglish").
     */
    fun resolveRememberedLanguagePreference(memories: List<MemoryItemEntity>): String? {
        for (mem in memories) {
            val lower = mem.memoryFact.lowercase(Locale.US)
            if (mem.memoryKey == "preferred_language" ||
                lower.contains("prefer") || lower.contains("respond in") ||
                lower.contains("reply in") || lower.contains("answer in") ||
                lower.contains("pasand") || lower.contains("mein jawab") ||
                lower.contains("language")
            ) {
                when {
                    lower.contains("hinglish") -> return "Hinglish"
                    lower.contains("hindi") || lower.contains("हिंदी") -> return "Hindi"
                    lower.contains("english") -> return "English"
                }
            }
        }
        return null
    }
}

data class MemorySaveOutcome(
    val saved: Boolean,
    val updatedExisting: Boolean = false,
    val memoryFact: String = "",
    val category: String = "",
    val reason: String = ""
)

data class MemoryDeleteOutcome(
    val deletedCount: Int,
    val deletedFacts: List<String>
)
