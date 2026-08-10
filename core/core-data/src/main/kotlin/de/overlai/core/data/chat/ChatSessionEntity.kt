package de.overlai.core.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Metadaten einer Chat-Session. Jede Session hat einen EIGENEN Provider+Modell
// (unabhängig von der globalen Auswahl) — das ist der Kern von Multi-Chat.
// E2: optionale Zuordnung zu einem Projekt (projectId). onDelete=SET_NULL → wird ein
// Projekt gelöscht, bleiben seine Chats erhalten (landen in „Ohne Projekt").
@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("projectId")],
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    // null → der Default-Modell des Providers wird verwendet.
    val modelId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // null → keinem Projekt zugeordnet („Ohne Projekt").
    val projectId: String? = null,
)
