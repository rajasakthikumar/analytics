package com.videoeditor.capcut.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ThumbnailGenerator"
        private const val CACHE_SIZE = 50 * 1024 * 1024 // 50MB
        private const val THUMBNAIL_QUALITY = 80
    }

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val thumbnailDir: File by lazy {
        File(context.cacheDir, "thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun generateVideoThumbnail(
        videoPath: String,
        timeMs: Long = 0,
        width: Int = 256,
        height: Int = 256
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${videoPath}_${timeMs}_${width}x$height"

        // Check memory cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // Check disk cache
        val diskCacheFile = File(thumbnailDir, "${cacheKey.hashCode()}.jpg")
        if (diskCacheFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(diskCacheFile.absolutePath)
            bitmap?.let { memoryCache.put(cacheKey, it) }
            return@withContext bitmap
        }

        // Generate thumbnail
        val bitmap = extractFrameFromVideo(videoPath, timeMs, width, height)

        // Cache to memory and disk
        bitmap?.let {
            memoryCache.put(cacheKey, it)
            saveToDiskCache(it, diskCacheFile)
        }

        bitmap
    }

    suspend fun generateVideoThumbnails(
        videoPath: String,
        count: Int,
        width: Int = 128,
        height: Int = 72
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val thumbnails = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(videoPath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            if (duration <= 0) return@withContext emptyList()

            val interval = duration / count

            for (i in 0 until count) {
                val timeUs = i * interval * 1000 // Convert to microseconds
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                frame?.let {
                    val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                    thumbnails.add(scaled)
                    if (scaled != it) it.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnails", e)
        } finally {
            retriever.release()
        }

        thumbnails
    }

    suspend fun generateTimelineThumbnails(
        videoPath: String,
        startTimeMs: Long,
        endTimeMs: Long,
        thumbnailWidthPx: Int,
        totalWidthPx: Int,
        height: Int = 48
    ): List<Pair<Bitmap, Long>> = withContext(Dispatchers.IO) {
        val thumbnails = mutableListOf<Pair<Bitmap, Long>>()
        val count = (totalWidthPx / thumbnailWidthPx) + 1
        val duration = endTimeMs - startTimeMs
        val interval = duration / count

        for (i in 0 until count) {
            val timeMs = startTimeMs + (i * interval)
            val bitmap = generateVideoThumbnail(videoPath, timeMs, thumbnailWidthPx, height)
            bitmap?.let { thumbnails.add(Pair(it, timeMs)) }
        }

        thumbnails
    }

    private fun extractFrameFromVideo(
        videoPath: String,
        timeMs: Long,
        width: Int,
        height: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val frame = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            frame?.let {
                val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                if (scaled != it) it.recycle()
                scaled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame", e)
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun generateHighQualityThumbnail(
        videoPath: String,
        timeMs: Long,
        width: Int,
        height: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val outputPath = File(thumbnailDir, "hq_${System.currentTimeMillis()}.jpg").absolutePath
        val time = formatTime(timeMs)

        val command = "-y -ss $time -i \"$videoPath\" -vframes 1 -s ${width}x$height -q:v 2 \"$outputPath\""
        val session = FFmpegKit.execute(command)

        if (ReturnCode.isSuccess(session.returnCode)) {
            val bitmap = BitmapFactory.decodeFile(outputPath)
            File(outputPath).delete()
            bitmap
        } else {
            null
        }
    }

    suspend fun generateAudioWaveformImage(
        audioPath: String,
        width: Int,
        height: Int,
        color: Int = 0xFF4CAF50.toInt()
    ): Bitmap? = withContext(Dispatchers.IO) {
        val outputPath = File(thumbnailDir, "waveform_${System.currentTimeMillis()}.png").absolutePath

        val command = "-y -i \"$audioPath\" -filter_complex \"aformat=channel_layouts=mono,showwavespic=s=${width}x${height}:colors=#${Integer.toHexString(color).substring(2)}\" -frames:v 1 \"$outputPath\""
        val session = FFmpegKit.execute(command)

        if (ReturnCode.isSuccess(session.returnCode)) {
            val bitmap = BitmapFactory.decodeFile(outputPath)
            File(outputPath).delete()
            bitmap
        } else {
            null
        }
    }

    private fun saveToDiskCache(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to disk cache", e)
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        thumbnailDir.listFiles()?.forEach { it.delete() }
    }

    fun getCacheSize(): Long {
        var size = 0L
        thumbnailDir.listFiles()?.forEach { size += it.length() }
        return size
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000.0
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%06.3f", hours, minutes, seconds)
    }
}
