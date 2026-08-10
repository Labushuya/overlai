package de.overlai.core.data.chat

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Echte Room-Migrationen — bestehende Chats/Verläufe bleiben beim Update erhalten
// (kein destruktives Zurücksetzen). Jede Schemaänderung: version++ in OverlaiDatabase
// + neue Migration hier + in ChatDatabaseFactory.addMigrations registrieren.

// 1 → 2: projects-Tabelle + chat_sessions.projectId (FK → projects, ON DELETE SET NULL, Index).
// SQLite kann per ALTER TABLE KEINE Foreign-Key-Constraint hinzufügen — Room validiert das
// Schema beim Start und würde sonst crashen. Deshalb chat_sessions neu bauen
// (create-copy-drop-rename) mit der FK direkt im CREATE TABLE, exakt wie Room es erwartet
// (siehe schemas/…/2.json). Bestehende Zeilen werden 1:1 übernommen (projectId = NULL).
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) projects-Tabelle.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `projects` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )

            // 2) chat_sessions neu bauen — mit projectId-Spalte + FK (SET NULL) im CREATE TABLE.
            db.execSQL(
                "CREATE TABLE `chat_sessions_new` (" +
                    "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `providerId` TEXT NOT NULL, " +
                    "`modelId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`projectId` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "INSERT INTO `chat_sessions_new` " +
                    "(`id`, `title`, `providerId`, `modelId`, `createdAt`, `updatedAt`, `projectId`) " +
                    "SELECT `id`, `title`, `providerId`, `modelId`, `createdAt`, `updatedAt`, NULL FROM `chat_sessions`",
            )
            db.execSQL("DROP TABLE `chat_sessions`")
            db.execSQL("ALTER TABLE `chat_sessions_new` RENAME TO `chat_sessions`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_projectId` ON `chat_sessions`(`projectId`)")
        }
    }
