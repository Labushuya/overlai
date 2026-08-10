package de.overlai.core.data.chat

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Verifiziert MIGRATION_1_2 END-TO-END: legt die v1-Datei-DB per SQL an, fügt einen Chat ein,
// öffnet danach eine ECHTE Room-Instanz (v2) mit der Migration und liest per DAO. Der letzte
// Schritt triggert Room's Schema-Validierung (validateMigration) — genau die hatte beim Start
// gecrasht, weil ALTER TABLE die FK nicht hinzufügen kann. Der Test hätte den Crash gefangen.
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.getDatabasePath(dbName).path).delete()
    }

    // Legt eine echte, dateibasierte v1-DB an (Schema wie vor E2) und schließt sie wieder.
    private fun createV1Database() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(ctx)
                    .name(dbName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE chat_sessions (" +
                                        "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                                        "providerId TEXT NOT NULL, modelId TEXT, " +
                                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                                )
                                db.execSQL(
                                    "CREATE TABLE chat_messages (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "sessionId TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, " +
                                        "createdAt INTEGER NOT NULL, " +
                                        "FOREIGN KEY(sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE)",
                                )
                                db.execSQL("CREATE INDEX index_chat_messages_sessionId ON chat_messages(sessionId)")
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO chat_sessions (id, title, providerId, modelId, createdAt, updatedAt) " +
                    "VALUES ('s1', 'Alt', 'openai', NULL, 1, 1)",
            )
        }
        helper.close()
    }

    @Test
    fun `MIGRATION_1_2 validiert gegen Room-Schema und behaelt Bestandschat`() {
        createV1Database()

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db =
            Room.databaseBuilder(ctx, OverlaiDatabase::class.java, dbName)
                .addMigrations(MIGRATION_1_2)
                .build()
        try {
            runBlocking {
                // Erster DAO-Zugriff öffnet die DB → Migration läuft + Room validiert das Schema.
                val session = db.chatDao().getSession("s1")
                assertThat(session).isNotNull()
                assertThat(session!!.title).isEqualTo("Alt")
                assertThat(session.projectId).isNull()

                // Projekte nutzbar + Verschieben funktioniert (FK aktiv).
                db.projectDao().upsertProject(ProjectEntity("p1", "P", 1, 1))
                db.chatDao().moveSessionToProject("s1", "p1", 2)
                assertThat(db.chatDao().getSession("s1")!!.projectId).isEqualTo("p1")
            }
        } finally {
            db.close()
        }
    }
}
