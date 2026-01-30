package com.videoeditor.capcut.core.utils

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appDir: File by lazy {
        File(context.getExternalFilesDir(null), "VideoEditor").apply {
            if (!exists()) mkdirs()
        }
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "VideoEditor").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getTempDir(): File {
        return File(cacheDir, "temp").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getProjectsDir(): File {
        return File(appDir, "projects").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getOutputDir(): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(moviesDir, "VideoEditor").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getThumbnailDir(): File {
        return File(cacheDir, "thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getWaveformDir(): File {
        return File(cacheDir, "waveforms").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getEffectsDir(): File {
        return File(appDir, "effects").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getFontsDir(): File {
        return File(appDir, "fonts").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getStickersDir(): File {
        return File(appDir, "stickers").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getMusicDir(): File {
        return File(appDir, "music").apply {
            if (!exists()) mkdirs()
        }
    }

    fun createTempFile(prefix: String, extension: String): File {
        return File(getTempDir(), "${prefix}_${System.currentTimeMillis()}.$extension")
    }

    fun clearTempDir() {
        getTempDir().listFiles()?.forEach { it.delete() }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach {
            if (it.isDirectory) {
                it.listFiles()?.forEach { file -> file.delete() }
            }
            it.delete()
        }
    }

    fun getProjectDir(projectId: String): File {
        return File(getProjectsDir(), projectId).apply {
            if (!exists()) mkdirs()
        }
    }

    fun deleteProject(projectId: String) {
        getProjectDir(projectId).deleteRecursively()
    }

    fun getCacheSize(): Long {
        return calculateDirSize(cacheDir)
    }

    fun getAppDataSize(): Long {
        return calculateDirSize(appDir)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
