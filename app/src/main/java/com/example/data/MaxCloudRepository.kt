package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    // **CRITICAL**: Log every single Firestore error in structured JSON format.
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class CloudChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    @get:PropertyName("isUser") @set:PropertyName("isUser") var isUser: Boolean = false,
    val text: String = "",
    val detectedLanguage: String = "Hinglish",
    val toolInvoked: String = "",
    val status: String = InteractionStatus.INFO_RESPONSE.name,
    val verificationEvidence: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudActionLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val toolName: String = "",
    val sanitizedSummary: String = "",
    val status: String = InteractionStatus.VERIFIED_SUCCESS.name,
    val verificationDetails: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudTrustedRule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val toolName: String = "",
    val scopeKey: String = "",
    val description: String = "",
    @get:PropertyName("isEnabled") @set:PropertyName("isEnabled") var isEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class MaxCloudRepository(private val db: FirebaseFirestore) {

    // Convenience constructor: always resolves the named database ID from string resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. Chat Messages ---

    fun observeMessages(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<CloudChatMessage>> = flow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/chat_messages"
        emitAll(
            db.collection("users").document(targetUid).collection("chat_messages")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { (it.createdAt ?: Timestamp(0, 0)).seconds }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createChatMessage(
        isUser: Boolean,
        text: String,
        detectedLanguage: String = "Hinglish",
        toolInvoked: String = "",
        status: String = InteractionStatus.INFO_RESPONSE.name,
        verificationEvidence: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("chat_messages")
        val normalizedLanguage = if (detectedLanguage in listOf("Hindi", "English", "Hinglish")) {
            detectedLanguage
        } else {
            "Hinglish"
        }
        val payload = mapOf(
            "userId" to uid,
            "isUser" to isUser,
            "text" to text.take(8000).ifBlank { " " },
            "detectedLanguage" to normalizedLanguage,
            "toolInvoked" to toolInvoked.take(120),
            "status" to status.take(64).ifBlank { InteractionStatus.INFO_RESPONSE.name },
            "verificationEvidence" to verificationEvidence.take(2000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val docRef = colRef.add(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun getUserMessages(targetUserId: String = requireUserId()): Result<List<CloudChatMessage>> = runCatching {
        val colRef = db.collection("users").document(targetUserId).collection("chat_messages")
        try {
            val snapshot = colRef.whereEqualTo("userId", targetUserId).get().await()
            snapshot.toObjects(
                CloudChatMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun getMessageById(targetUserId: String, messageId: String): Result<CloudChatMessage?> = runCatching {
        val docRef = db.collection("users").document(targetUserId)
            .collection("chat_messages").document(messageId)
        try {
            docRef.get().await().toObject(
                CloudChatMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun clearUserMessages(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("chat_messages")
        try {
            val docs = colRef.whereEqualTo("userId", uid).get().await().documents
            for (doc in docs) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    // --- 2. Action Audit Logs ---

    fun observeActionLogs(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<CloudActionLog>> = flow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/action_logs"
        emitAll(
            db.collection("users").document(targetUid).collection("action_logs")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudActionLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { (it.createdAt ?: Timestamp(0, 0)).seconds }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createActionLog(
        toolName: String,
        sanitizedSummary: String,
        status: String,
        verificationDetails: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("action_logs")
        val payload = mapOf(
            "userId" to uid,
            "toolName" to toolName.take(120).ifBlank { "unknown_tool" },
            "sanitizedSummary" to sanitizedSummary.take(2000).ifBlank { "Executed action" },
            "status" to status.take(64).ifBlank { InteractionStatus.VERIFIED_SUCCESS.name },
            "verificationDetails" to verificationDetails.take(2000).ifBlank { "Verified" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val docRef = colRef.add(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun clearUserActionLogs(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("action_logs")
        try {
            val docs = colRef.whereEqualTo("userId", uid).get().await().documents
            for (doc in docs) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    // --- 3. Trusted Rules ---

    fun observeTrustedRules(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<CloudTrustedRule>> = flow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/trusted_rules"
        emitAll(
            db.collection("users").document(targetUid).collection("trusted_rules")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudTrustedRule::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { (it.createdAt ?: Timestamp(0, 0)).seconds }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createTrustedRule(
        toolName: String,
        scopeKey: String,
        description: String,
        isEnabled: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("trusted_rules")
        val payload = mapOf(
            "userId" to uid,
            "toolName" to toolName.take(120).ifBlank { "whatsapp_action" },
            "scopeKey" to scopeKey.take(256).ifBlank { "default_scope" },
            "description" to description.take(500).ifBlank { "Trusted rule" },
            "isEnabled" to isEnabled,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val docRef = colRef.add(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun updateTrustedRuleEnabled(ruleId: String, isEnabled: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("trusted_rules").document(ruleId)
        try {
            docRef.update(
                mapOf(
                    "isEnabled" to isEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTrustedRule(ruleId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("trusted_rules").document(ruleId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
