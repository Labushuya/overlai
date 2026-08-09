package de.overlai.core.data.chat

import android.content.Context
import androidx.room.Room

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Factory für die Room-DB — hält Room in core-data gekapselt (:app hat keine Room-
// Dependency, kennt nur diese Funktion + SessionRepository). Bindung als @Singleton in :app.
object ChatDatabaseFactory {
    fun create(context: Context): OverlaiDatabase =
        Room.databaseBuilder(context, OverlaiDatabase::class.java, "overlai.db").build()

    // :app soll Room nicht sehen — nur diese Factory nutzen, die direkt das Repository liefert.
    fun createRepository(context: Context): SessionRepository = SessionRepository(create(context).chatDao())
}
