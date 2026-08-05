package de.overlai.core.data.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Metadaten einer Chat-Session. Jede Session hat einen EIGENEN Provider+Modell
// (unabhängig von der globalen Auswahl) — das ist der Kern von Multi-Chat.
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    // null → der Default-Modell des Providers wird verwendet.
    val modelId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
