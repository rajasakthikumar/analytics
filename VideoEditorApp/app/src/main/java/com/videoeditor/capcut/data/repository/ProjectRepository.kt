package com.videoeditor.capcut.data.repository

import com.videoeditor.capcut.data.local.DraftEntity
import com.videoeditor.capcut.data.local.ProjectDao
import com.videoeditor.capcut.data.local.ProjectEntity
import com.videoeditor.capcut.data.models.VideoProject
import com.videoeditor.capcut.core.utils.FileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ProjectRepository {
    fun getAllProjects(): Flow<List<VideoProject>>
    fun getRecentProjects(limit: Int): Flow<List<VideoProject>>
    suspend fun getProjectById(projectId: String): VideoProject?
    suspend fun saveProject(project: VideoProject)
    suspend fun deleteProject(projectId: String)
    suspend fun duplicateProject(projectId: String): VideoProject?
    suspend fun getProjectCount(): Int

    // Draft management
    fun getAllDrafts(): Flow<List<DraftEntity>>
    suspend fun saveDraft(project: VideoProject)
    suspend fun getLatestDraft(projectId: String): VideoProject?
    suspend fun deleteDraft(draftId: String)
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val fileManager: FileManager
) : ProjectRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    override fun getAllProjects(): Flow<List<VideoProject>> {
        return projectDao.getAllProjects().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    json.decodeFromString<VideoProject>(entity.projectDataJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun getRecentProjects(limit: Int): Flow<List<VideoProject>> {
        return projectDao.getRecentProjects(limit).map { entities ->
            entities.mapNotNull { entity ->
                try {
                    json.decodeFromString<VideoProject>(entity.projectDataJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override suspend fun getProjectById(projectId: String): VideoProject? {
        return projectDao.getProjectById(projectId)?.let { entity ->
            try {
                json.decodeFromString<VideoProject>(entity.projectDataJson)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun saveProject(project: VideoProject) {
        val updatedProject = project.copy(modifiedAt = System.currentTimeMillis())
        val entity = ProjectEntity(
            id = updatedProject.id,
            name = updatedProject.name,
            createdAt = updatedProject.createdAt,
            modifiedAt = updatedProject.modifiedAt,
            aspectRatio = updatedProject.aspectRatio.name,
            resolution = updatedProject.resolution.name,
            frameRate = updatedProject.frameRate.name,
            duration = updatedProject.duration,
            thumbnailPath = updatedProject.thumbnailPath,
            projectDataJson = json.encodeToString(updatedProject)
        )
        projectDao.insertProject(entity)
    }

    override suspend fun deleteProject(projectId: String) {
        projectDao.deleteProjectById(projectId)
        fileManager.deleteProject(projectId)
    }

    override suspend fun duplicateProject(projectId: String): VideoProject? {
        val original = getProjectById(projectId) ?: return null
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        saveProject(duplicate)
        return duplicate
    }

    override suspend fun getProjectCount(): Int {
        return projectDao.getProjectCount()
    }

    override fun getAllDrafts(): Flow<List<DraftEntity>> {
        return projectDao.getAllDrafts()
    }

    override suspend fun saveDraft(project: VideoProject) {
        val draft = DraftEntity(
            id = UUID.randomUUID().toString(),
            projectId = project.id,
            savedAt = System.currentTimeMillis(),
            projectDataJson = json.encodeToString(project)
        )
        projectDao.insertDraft(draft)
    }

    override suspend fun getLatestDraft(projectId: String): VideoProject? {
        return projectDao.getLatestDraft(projectId)?.let { draft ->
            try {
                json.decodeFromString<VideoProject>(draft.projectDataJson)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun deleteDraft(draftId: String) {
        projectDao.deleteDraft(draftId)
    }
}
