package com.videoeditor.capcut.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videoeditor.capcut.core.utils.FileManager
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fileManager: FileManager
) : VideoEngine {

    companion object {
        private const val TAG = "VideoEngine"
    }

    private var currentSessionId: Long = -1

    override suspend fun loadVideo(path: String): VideoInfo = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val frameRate = getFrameRate(path)

            VideoInfo(
                path = path,
                duration = duration,
                width = width,
                height = height,
                rotation = rotation,
                frameRate = frameRate,
                bitrate = bitrate,
                hasAudio = hasAudio,
                audioChannels = 2,
                audioSampleRate = 44100
            )
        } finally {
            retriever.release()
        }
    }

    private fun getFrameRate(path: String): Float {
        val session = FFprobeKit.getMediaInformation(path)
        val info = session.mediaInformation
        return info?.streams?.firstOrNull { it.type == "video" }
            ?.let { stream ->
                val frameRateStr = stream.averageFrameRate ?: stream.realFrameRate
                frameRateStr?.let {
                    if (it.contains("/")) {
                        val parts = it.split("/")
                        if (parts.size == 2) {
                            parts[0].toFloatOrNull()?.div(parts[1].toFloatOrNull() ?: 1f)
                        } else null
                    } else it.toFloatOrNull()
                }
            } ?: 30f
    }

    override suspend fun extractFrame(path: String, timeMs: Long): Bitmap? = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame: ${e.message}")
            null
        } finally {
            retriever.release()
        }
    }

    override suspend fun extractFrames(path: String, count: Int): List<Bitmap> = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val interval = duration / count

            for (i in 0 until count) {
                val timeUs = (i * interval * 1000)
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                    frames.add(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frames: ${e.message}")
        } finally {
            retriever.release()
        }
        frames
    }

    override suspend fun trimVideo(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(ioDispatcher) {
        val startTime = formatTime(startMs)
        val duration = formatTime(endMs - startMs)

        val command = "-y -ss $startTime -i \"$inputPath\" -t $duration -c copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun splitVideo(
        inputPath: String,
        outputDir: String,
        splitPointMs: Long
    ): Pair<String, String>? = withContext(ioDispatcher) {
        val videoInfo = loadVideo(inputPath)
        val splitTime = formatTime(splitPointMs)

        val fileName = File(inputPath).nameWithoutExtension
        val extension = File(inputPath).extension

        val part1Path = "$outputDir/${fileName}_part1.$extension"
        val part2Path = "$outputDir/${fileName}_part2.$extension"

        val command1 = "-y -i \"$inputPath\" -t $splitTime -c copy \"$part1Path\""
        val command2 = "-y -ss $splitTime -i \"$inputPath\" -c copy \"$part2Path\""

        val success1 = executeFFmpegCommand(command1)
        val success2 = executeFFmpegCommand(command2)

        if (success1 && success2) {
            Pair(part1Path, part2Path)
        } else {
            null
        }
    }

    override suspend fun mergeVideos(
        inputPaths: List<String>,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        if (inputPaths.isEmpty()) return@withContext false

        val listFile = File(fileManager.getTempDir(), "concat_list.txt")
        listFile.writeText(inputPaths.joinToString("\n") { "file '$it'" })

        val command = "-y -f concat -safe 0 -i \"${listFile.absolutePath}\" -c copy \"$outputPath\""
        val result = executeFFmpegCommand(command)

        listFile.delete()
        result
    }

    override suspend fun changeSpeed(
        inputPath: String,
        outputPath: String,
        speed: Float
    ): Boolean = withContext(ioDispatcher) {
        val videoFilter = "setpts=${1/speed}*PTS"
        val audioFilter = "atempo=$speed"

        val command = if (speed <= 2.0f && speed >= 0.5f) {
            "-y -i \"$inputPath\" -filter_complex \"[0:v]$videoFilter[v];[0:a]$audioFilter[a]\" -map \"[v]\" -map \"[a]\" \"$outputPath\""
        } else {
            // For extreme speeds, chain atempo filters
            val atempoChain = buildAtempoChain(speed)
            "-y -i \"$inputPath\" -filter_complex \"[0:v]$videoFilter[v];[0:a]$atempoChain[a]\" -map \"[v]\" -map \"[a]\" \"$outputPath\""
        }

        executeFFmpegCommand(command)
    }

    private fun buildAtempoChain(speed: Float): String {
        val filters = mutableListOf<String>()
        var remainingSpeed = speed

        while (remainingSpeed > 2.0f) {
            filters.add("atempo=2.0")
            remainingSpeed /= 2.0f
        }
        while (remainingSpeed < 0.5f) {
            filters.add("atempo=0.5")
            remainingSpeed /= 0.5f
        }
        filters.add("atempo=$remainingSpeed")

        return filters.joinToString(",")
    }

    override suspend fun rotateVideo(
        inputPath: String,
        outputPath: String,
        degrees: Int
    ): Boolean = withContext(ioDispatcher) {
        val transpose = when (degrees) {
            90 -> "transpose=1"
            180 -> "transpose=1,transpose=1"
            270 -> "transpose=2"
            else -> return@withContext false
        }

        val command = "-y -i \"$inputPath\" -vf \"$transpose\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun cropVideo(
        inputPath: String,
        outputPath: String,
        cropRect: CropRect
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$inputPath\" -vf \"crop=${cropRect.width}:${cropRect.height}:${cropRect.left}:${cropRect.top}\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun flipVideo(
        inputPath: String,
        outputPath: String,
        horizontal: Boolean
    ): Boolean = withContext(ioDispatcher) {
        val filter = if (horizontal) "hflip" else "vflip"
        val command = "-y -i \"$inputPath\" -vf \"$filter\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun reverseVideo(
        inputPath: String,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$inputPath\" -vf reverse -af areverse \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun getVideoMetadata(path: String): VideoMetadata? = withContext(ioDispatcher) {
        try {
            val session = FFprobeKit.getMediaInformation(path)
            val info = session.mediaInformation ?: return@withContext null

            val videoStream = info.streams?.firstOrNull { it.type == "video" }
            val audioStream = info.streams?.firstOrNull { it.type == "audio" }

            VideoMetadata(
                duration = (info.duration?.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L,
                width = videoStream?.width?.toIntOrNull() ?: 0,
                height = videoStream?.height?.toIntOrNull() ?: 0,
                rotation = 0,
                frameRate = getFrameRate(path),
                bitrate = info.bitrate?.toLongOrNull() ?: 0L,
                codec = videoStream?.codec ?: "unknown",
                format = info.format ?: "unknown",
                hasAudio = audioStream != null,
                audioCodec = audioStream?.codec,
                audioChannels = audioStream?.let {
                    it.allProperties?.optString("channels")?.toIntOrNull()
                } ?: 0,
                audioSampleRate = audioStream?.sampleRate?.toIntOrNull() ?: 0,
                audioBitrate = audioStream?.bitrate?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting metadata: ${e.message}")
            null
        }
    }

    override suspend fun generateThumbnail(
        path: String,
        timeMs: Long,
        width: Int,
        height: Int
    ): Bitmap? = withContext(ioDispatcher) {
        val outputPath = "${fileManager.getThumbnailDir()}/${System.currentTimeMillis()}.jpg"
        val time = formatTime(timeMs)

        val command = "-y -ss $time -i \"$path\" -vframes 1 -s ${width}x${height} \"$outputPath\""

        if (executeFFmpegCommand(command)) {
            BitmapFactory.decodeFile(outputPath)
        } else {
            extractFrame(path, timeMs)
        }
    }

    override suspend fun generateWaveform(audioPath: String): List<Float> = withContext(ioDispatcher) {
        val waveformData = mutableListOf<Float>()

        try {
            val outputFile = File(fileManager.getTempDir(), "waveform_${System.currentTimeMillis()}.txt")
            val command = "-y -i \"$audioPath\" -ac 1 -filter:a aresample=8000 -map 0:a -c:a pcm_s16le -f data \"${outputFile.absolutePath}\""

            if (executeFFmpegCommand(command)) {
                val bytes = outputFile.readBytes()
                val samples = bytes.size / 2
                val samplesPerPoint = maxOf(1, samples / 200)

                for (i in 0 until minOf(200, samples / samplesPerPoint)) {
                    var sum = 0f
                    for (j in 0 until samplesPerPoint) {
                        val index = (i * samplesPerPoint + j) * 2
                        if (index + 1 < bytes.size) {
                            val sample = (bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xFF)
                            sum += kotlin.math.abs(sample.toFloat())
                        }
                    }
                    waveformData.add(sum / samplesPerPoint / 32768f)
                }

                outputFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating waveform: ${e.message}")
        }

        waveformData
    }

    override fun cancelOperation() {
        if (currentSessionId != -1L) {
            FFmpegKit.cancel(currentSessionId)
            currentSessionId = -1
        }
    }

    private fun executeFFmpegCommand(command: String): Boolean {
        Log.d(TAG, "Executing FFmpeg command: $command")
        val session = FFmpegKit.execute(command)
        currentSessionId = session.sessionId
        val returnCode = session.returnCode

        return if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "FFmpeg command succeeded")
            true
        } else {
            Log.e(TAG, "FFmpeg command failed: ${session.failStackTrace}")
            false
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000.0
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%06.3f", hours, minutes, seconds)
    }
}
