package com.videoeditor.capcut.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [ProjectEntity::class, DraftEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC LIMIT :limit")
    fun getRecentProjects(limit: Int): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

    // Drafts
    @Query("SELECT * FROM drafts ORDER BY savedAt DESC")
    fun getAllDrafts(): Flow<List<DraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :draftId")
    suspend fun deleteDraft(draftId: String)

    @Query("SELECT * FROM drafts WHERE projectId = :projectId ORDER BY savedAt DESC LIMIT 1")
    suspend fun getLatestDraft(projectId: String): DraftEntity?
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val aspectRatio: String,
    val resolution: String,
    val frameRate: String,
    val duration: Long,
    val thumbnailPath: String?,
    val projectDataJson: String // Serialized VideoProject
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val savedAt: Long,
    val projectDataJson: String
)

class Converters {
    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        return value?.split(",") ?: emptyList()
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return list.joinToString(",")
    }
}
