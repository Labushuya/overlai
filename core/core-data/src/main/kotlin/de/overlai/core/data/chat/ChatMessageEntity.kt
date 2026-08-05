package de.overlai.core.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Eine persistierte Nachricht einer Session. `role` als String (Role.name aus core-llm).
// Das transiente UI-Flag `streaming` wird NICHT gespeichert — beim Laden ist alles fertig.
// FK auf die Session mit CASCADE: Session löschen entfernt ihre Nachrichten mit.
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
)
