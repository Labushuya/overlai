package de.overlai.core.data.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Ein Projekt/eine Gruppe zum Organisieren von Chat-Sessions. Chats verweisen optional
// per ChatSessionEntity.projectId hierher (FK, onDelete=SET_NULL).
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)
