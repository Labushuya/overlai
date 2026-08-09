package de.overlai.core.data.chat

import androidx.room.Database
import androidx.room.RoomDatabase

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Room-Datenbank für Chat-Sessions + Nachrichten. Version 1 (erstes Schema; noch keine
// Migration nötig). In :app via Room.databaseBuilder als @Singleton bereitgestellt.
@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OverlaiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
