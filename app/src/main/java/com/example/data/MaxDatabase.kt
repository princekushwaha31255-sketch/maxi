package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MaxDao {
    // --- Conversation Sessions ---
    @Query("SELECT * FROM conversation_sessions WHERE userAccountKey = :userAccountKey ORDER BY updatedAt DESC")
    fun getSessionsForAccount(userAccountKey: String = "local_guest"): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ConversationSessionEntity)

    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessionsSnapshot(): List<ConversationSessionEntity>

    @Query("DELETE FROM conversation_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM conversation_sessions")
    suspend fun clearAllSessions()

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionSnapshot(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET userFeedback = :feedback WHERE id = :messageId")
    suspend fun updateMessageFeedback(messageId: Long, feedback: String?)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSessionMessages(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    // --- Action Logs ---
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllActionLogs(): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearActionLogs()

    // --- Trusted Rules ---
    @Query("SELECT * FROM trusted_rules ORDER BY createdAt DESC")
    fun getAllTrustedRules(): Flow<List<TrustedRuleEntity>>

    @Query("SELECT * FROM trusted_rules WHERE toolName = :toolName AND scopeKey = :scopeKey AND isEnabled = 1 LIMIT 1")
    suspend fun findActiveTrustedRule(toolName: String, scopeKey: String): TrustedRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedRule(rule: TrustedRuleEntity): Long

    @Query("UPDATE trusted_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setTrustedRuleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM trusted_rules WHERE id = :id")
    suspend fun deleteTrustedRule(id: Long)

    // --- Scheduled Items ---
    @Query("SELECT * FROM scheduled_items ORDER BY triggerAtMillis ASC")
    fun getAllScheduledItems(): Flow<List<ScheduledItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledItem(item: ScheduledItemEntity): Long

    @Query("DELETE FROM scheduled_items WHERE id = :id")
    suspend fun deleteScheduledItem(id: Long)

    // --- Long-Term User Memories ---
    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC, createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM user_memories WHERE userAccountKey = :userAccountKey ORDER BY updatedAt DESC, createdAt DESC")
    fun getMemoriesForAccount(userAccountKey: String): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM user_memories WHERE userAccountKey = :userAccountKey ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getMemoriesSnapshotForAccount(userAccountKey: String): List<MemoryItemEntity>

    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getAllMemoriesSnapshot(): List<MemoryItemEntity>

    @Query("SELECT * FROM user_memories WHERE memoryKey = :memoryKey AND userAccountKey = :userAccountKey LIMIT 1")
    suspend fun findMemoryByKey(memoryKey: String, userAccountKey: String): MemoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryItemEntity): Long

    @Query("UPDATE user_memories SET memoryFact = :updatedFact, category = :category, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMemory(id: Long, updatedFact: String, category: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM user_memories WHERE userAccountKey = :userAccountKey")
    suspend fun clearMemoriesForAccount(userAccountKey: String)

    @Query("DELETE FROM user_memories")
    suspend fun clearAllMemories()
}

@Database(
    entities = [
        ConversationSessionEntity::class,
        ChatMessageEntity::class,
        ActionLogEntity::class,
        TrustedRuleEntity::class,
        ScheduledItemEntity::class,
        MemoryItemEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MaxDatabase : RoomDatabase() {
    abstract fun maxDao(): MaxDao

    companion object {
        @Volatile
        private var INSTANCE: MaxDatabase? = null

        fun getInstance(context: Context): MaxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MaxDatabase::class.java,
                    "max_ai_database.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
