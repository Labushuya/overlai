package de.overlai.core.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Zugriff auf Sessions + Nachrichten. Flows für reaktive Listen (UI beobachtet).
@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(
        id: String,
        title: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE chat_sessions SET providerId = :providerId, modelId = :modelId, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProviderModel(
        id: String,
        providerId: String,
        modelId: String?,
        updatedAt: Long,
    )

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    // Löscht die Session; Nachrichten gehen per FK-CASCADE mit.
    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
