package de.overlai.core.data.chat

import androidx.room.Database
import androidx.room.RoomDatabase

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Room-Datenbank für Chat-Sessions + Nachrichten + Projekte. Version 2 (E2 fügt die
// projects-Tabelle + chat_sessions.projectId hinzu; echte Migration in ChatDatabaseFactory,
// bestehende Daten bleiben erhalten). exportSchema=true → Schema-JSONs für Migrations-Tests.
@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, ProjectEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class OverlaiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun projectDao(): ProjectDao
}
