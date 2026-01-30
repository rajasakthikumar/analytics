package com.videoeditor.capcut.core.engine

import android.graphics.Bitmap
import com.videoeditor.capcut.data.models.*
import kotlinx.coroutines.flow.Flow

interface VideoEngine {
    suspend fun loadVideo(path: String): VideoInfo
    suspend fun extractFrame(path: String, timeMs: Long): Bitmap?
    suspend fun extractFrames(path: String, count: Int): List<Bitmap>
    suspend fun trimVideo(inputPath: String, outputPath: String, startMs: Long, endMs: Long): Boolean
    suspend fun splitVideo(inputPath: String, outputDir: String, splitPointMs: Long): Pair<String, String>?
    suspend fun mergeVideos(inputPaths: List<String>, outputPath: String): Boolean
    suspend fun changeSpeed(inputPath: String, outputPath: String, speed: Float): Boolean
    suspend fun rotateVideo(inputPath: String, outputPath: String, degrees: Int): Boolean
    suspend fun cropVideo(inputPath: String, outputPath: String, cropRect: CropRect): Boolean
    suspend fun flipVideo(inputPath: String, outputPath: String, horizontal: Boolean): Boolean
    suspend fun reverseVideo(inputPath: String, outputPath: String): Boolean
    suspend fun getVideoMetadata(path: String): VideoMetadata?
    suspend fun generateThumbnail(path: String, timeMs: Long, width: Int, height: Int): Bitmap?
    suspend fun generateWaveform(audioPath: String): List<Float>
    fun cancelOperation()
}

data class VideoInfo(
    val path: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val frameRate: Float,
    val bitrate: Long,
    val hasAudio: Boolean,
    val audioChannels: Int,
    val audioSampleRate: Int
)

data class VideoMetadata(
    val duration: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val frameRate: Float,
    val bitrate: Long,
    val codec: String,
    val format: String,
    val hasAudio: Boolean,
    val audioCodec: String?,
    val audioChannels: Int,
    val audioSampleRate: Int,
    val audioBitrate: Int
)

data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)
