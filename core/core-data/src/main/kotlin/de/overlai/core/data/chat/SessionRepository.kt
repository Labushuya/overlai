package de.overlai.core.data.chat

import de.overlai.llm.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Schmales Domänenmodell + Repository für Chat-Sessions. Bewusst DI-frei (wie die anderen
// core-Bausteine; Bindung als @Singleton in :app). Nutzt Role aus core-llm; das Mapping auf
// das UI-Modell ChatUiMessage passiert in core-conversation (vermeidet Zirkularität
// core-data ↔ core-conversation).

data class ChatSession(
    val id: String,
    val title: String,
    val providerId: String,
    val modelId: String?,
    val updatedAt: Long,
)

// Eine persistierte Nachricht in der Domänen-Form (ohne UI-Streaming-Flag).
data class StoredMessage(
    val role: Role,
    val text: String,
)

class SessionRepository(
    private val dao: ChatDao,
) {
    fun observeSessions(): Flow<List<ChatSession>> = dao.observeSessions().map { list -> list.map { it.toDomain() } }

    fun observeMessages(sessionId: String): Flow<List<StoredMessage>> =
        dao.observeMessages(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun messages(sessionId: String): List<StoredMessage> = dao.getMessages(sessionId).map { it.toDomain() }

    suspend fun getSession(id: String): ChatSession? = dao.getSession(id)?.toDomain()

    // Neue Session anlegen; gibt die id zurück.
    suspend fun createSession(
        id: String,
        title: String,
        providerId: String,
        modelId: String?,
        now: Long,
    ): String {
        dao.upsertSession(
            ChatSessionEntity(
                id = id,
                title = title,
                providerId = providerId,
                modelId = modelId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun appendMessage(
        sessionId: String,
        role: Role,
        text: String,
        now: Long,
    ) {
        dao.insertMessage(
            ChatMessageEntity(sessionId = sessionId, role = role.name, text = text, createdAt = now),
        )
    }

    suspend fun updateTitle(
        id: String,
        title: String,
        now: Long,
    ) = dao.updateTitle(id, title, now)

    suspend fun updateProviderModel(
        id: String,
        providerId: String,
        modelId: String?,
        now: Long,
    ) = dao.updateProviderModel(id, providerId, modelId, now)

    suspend fun deleteSession(id: String) = dao.deleteSession(id)

    private fun ChatSessionEntity.toDomain() =
        ChatSession(id = id, title = title, providerId = providerId, modelId = modelId, updatedAt = updatedAt)

    private fun ChatMessageEntity.toDomain() =
        StoredMessage(role = runCatching { Role.valueOf(role) }.getOrDefault(Role.ASSISTANT), text = text)
}
