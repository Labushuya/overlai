package de.overlai.core.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// CHANGE-MARKER: Projekte/Gruppen (Phase 3 E2, siehe CHANGELOG.md)
// Zugriff auf Projekte/Gruppen (getrennt von ChatDao, um dessen Umfang klein zu halten).
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectName(
        id: String,
        name: String,
        updatedAt: Long,
    )

    // Projekt löschen; zugeordnete Sessions bleiben (projectId wird per FK SET NULL genullt).
    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}
