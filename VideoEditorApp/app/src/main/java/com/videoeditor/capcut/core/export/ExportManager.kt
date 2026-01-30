package com.videoeditor.capcut.core.export

import com.videoeditor.capcut.data.models.*
import kotlinx.coroutines.flow.Flow

interface ExportManager {
    suspend fun exportProject(
        project: VideoProject,
        settings: ExportSettings,
        onProgress: (ExportProgress) -> Unit
    ): Result<String>

    suspend fun exportToGif(
        project: VideoProject,
        outputPath: String,
        fps: Int = 15,
        scale: Int = 480,
        onProgress: (ExportProgress) -> Unit
    ): Result<String>

    suspend fun exportFrame(
        videoPath: String,
        timeMs: Long,
        outputPath: String,
        quality: Int = 100
    ): Result<String>

    suspend fun cancelExport()

    fun getExportProgress(): Flow<ExportProgress>

    suspend fun estimateFileSize(
        project: VideoProject,
        settings: ExportSettings
    ): Long

    suspend fun validateProject(project: VideoProject): List<String>
}
