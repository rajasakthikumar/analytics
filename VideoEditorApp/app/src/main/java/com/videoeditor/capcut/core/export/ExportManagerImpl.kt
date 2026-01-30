package com.videoeditor.capcut.core.export

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.videoeditor.capcut.core.audio.AudioEngine
import com.videoeditor.capcut.core.engine.VideoEngine
import com.videoeditor.capcut.core.filters.FilterEngine
import com.videoeditor.capcut.core.text.TextRenderer
import com.videoeditor.capcut.core.transitions.TransitionEngine
import com.videoeditor.capcut.core.utils.FileManager
import com.videoeditor.capcut.data.models.*
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val videoEngine: VideoEngine,
    private val filterEngine: FilterEngine,
    private val audioEngine: AudioEngine,
    private val textRenderer: TextRenderer,
    private val transitionEngine: TransitionEngine,
    private val fileManager: FileManager
) : ExportManager {

    companion object {
        private const val TAG = "ExportManager"
    }

    private var currentSessionId: Long = -1
    private var isCancelled = false
    private val progressFlow = MutableStateFlow(ExportProgress())

    override suspend fun exportProject(
        project: VideoProject,
        settings: ExportSettings,
        onProgress: (ExportProgress) -> Unit
    ): Result<String> = withContext(ioDispatcher) {
        isCancelled = false

        try {
            // Validate project first
            val validationErrors = validateProject(project)
            if (validationErrors.isNotEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Validation failed: ${validationErrors.joinToString()}")
                )
            }

            onProgress(ExportProgress(phase = ExportPhase.PREPARING))

            // Calculate output dimensions based on aspect ratio and resolution
            val (outputWidth, outputHeight) = calculateOutputDimensions(project.aspectRatio, settings.resolution)

            // Get output path
            val outputPath = settings.outputPath ?: "${fileManager.getOutputDir()}/${project.name}_${System.currentTimeMillis()}.${settings.format.extension}"

            // Process video tracks
            onProgress(ExportProgress(phase = ExportPhase.PROCESSING_VIDEO, progress = 0.1f))

            val videoTracks = project.tracks.filter { it.type == TrackType.VIDEO }
            val audioTracks = project.tracks.filter { it.type == TrackType.AUDIO }
            val textTracks = project.tracks.filter { it.type == TrackType.TEXT }

            // Build FFmpeg filter complex
            val filterComplex = buildFilterComplex(project, videoTracks, audioTracks, textTracks, outputWidth, outputHeight)

            // Build input arguments
            val inputArgs = buildInputArguments(project)

            // Build output arguments
            val outputArgs = buildOutputArguments(settings, outputWidth, outputHeight)

            // Execute FFmpeg command
            onProgress(ExportProgress(phase = ExportPhase.ENCODING, progress = 0.3f))

            val command = buildString {
                append("-y ")
                append(inputArgs)
                append(" ")
                append(filterComplex)
                append(" ")
                append(outputArgs)
                append(" \"$outputPath\"")
            }

            Log.d(TAG, "Export command: $command")

            val session = FFmpegKit.executeAsync(command,
                { session ->
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        onProgress(ExportProgress(
                            phase = ExportPhase.COMPLETED,
                            progress = 1f,
                            outputPath = outputPath
                        ))
                    } else {
                        onProgress(ExportProgress(
                            phase = ExportPhase.FAILED,
                            error = session.failStackTrace ?: "Unknown error"
                        ))
                    }
                },
                { log -> Log.d(TAG, log.message) },
                { stats ->
                    updateProgress(stats, project.duration, onProgress)
                }
            )

            currentSessionId = session.sessionId

            // Wait for completion
            session.waitForCompletion()

            if (isCancelled) {
                File(outputPath).delete()
                return@withContext Result.failure(Exception("Export cancelled"))
            }

            if (ReturnCode.isSuccess(session.returnCode)) {
                Result.success(outputPath)
            } else {
                Result.failure(Exception(session.failStackTrace ?: "Export failed"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            onProgress(ExportProgress(phase = ExportPhase.FAILED, error = e.message))
            Result.failure(e)
        }
    }

    override suspend fun exportToGif(
        project: VideoProject,
        outputPath: String,
        fps: Int,
        scale: Int,
        onProgress: (ExportProgress) -> Unit
    ): Result<String> = withContext(ioDispatcher) {
        try {
            onProgress(ExportProgress(phase = ExportPhase.PREPARING))

            // First, export to temporary video
            val tempVideoPath = "${fileManager.getTempDir()}/temp_video_${System.currentTimeMillis()}.mp4"
            val tempSettings = ExportSettings(
                resolution = Resolution.SD_480P,
                frameRate = FrameRate.FPS_24,
                outputPath = tempVideoPath
            )

            val videoResult = exportProject(project, tempSettings) { }
            if (videoResult.isFailure) {
                return@withContext Result.failure(videoResult.exceptionOrNull() ?: Exception("Video export failed"))
            }

            onProgress(ExportProgress(phase = ExportPhase.ENCODING, progress = 0.5f))

            // Generate palette for better GIF quality
            val palettePath = "${fileManager.getTempDir()}/palette_${System.currentTimeMillis()}.png"
            val paletteCommand = "-y -i \"$tempVideoPath\" -vf \"fps=$fps,scale=$scale:-1:flags=lanczos,palettegen\" \"$palettePath\""

            val paletteSession = FFmpegKit.execute(paletteCommand)
            if (!ReturnCode.isSuccess(paletteSession.returnCode)) {
                File(tempVideoPath).delete()
                return@withContext Result.failure(Exception("Palette generation failed"))
            }

            onProgress(ExportProgress(phase = ExportPhase.RENDERING, progress = 0.75f))

            // Create GIF using palette
            val gifCommand = "-y -i \"$tempVideoPath\" -i \"$palettePath\" -filter_complex \"fps=$fps,scale=$scale:-1:flags=lanczos[x];[x][1:v]paletteuse\" \"$outputPath\""

            val gifSession = FFmpegKit.execute(gifCommand)

            // Clean up temp files
            File(tempVideoPath).delete()
            File(palettePath).delete()

            if (ReturnCode.isSuccess(gifSession.returnCode)) {
                onProgress(ExportProgress(phase = ExportPhase.COMPLETED, progress = 1f, outputPath = outputPath))
                Result.success(outputPath)
            } else {
                onProgress(ExportProgress(phase = ExportPhase.FAILED, error = "GIF creation failed"))
                Result.failure(Exception("GIF creation failed"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "GIF export failed", e)
            onProgress(ExportProgress(phase = ExportPhase.FAILED, error = e.message))
            Result.failure(e)
        }
    }

    override suspend fun exportFrame(
        videoPath: String,
        timeMs: Long,
        outputPath: String,
        quality: Int
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val time = formatTime(timeMs)
            val command = "-y -ss $time -i \"$videoPath\" -vframes 1 -q:v ${(100 - quality) / 10 + 1} \"$outputPath\""

            val session = FFmpegKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                Result.success(outputPath)
            } else {
                Result.failure(Exception("Frame export failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelExport() {
        isCancelled = true
        if (currentSessionId != -1L) {
            FFmpegKit.cancel(currentSessionId)
            currentSessionId = -1
        }
    }

    override fun getExportProgress(): Flow<ExportProgress> = callbackFlow {
        FFmpegKitConfig.enableStatisticsCallback { stats ->
            trySend(ExportProgress(
                progress = 0f, // Would need total duration to calculate
                currentFrame = stats.videoFrameNumber,
                elapsedTime = stats.time.toLong()
            ))
        }
        awaitClose { }
    }

    override suspend fun estimateFileSize(
        project: VideoProject,
        settings: ExportSettings
    ): Long {
        val durationSeconds = project.duration / 1000.0
        val videoBitrate = settings.videoBitrate * settings.quality.bitrateMultiplier
        val audioBitrate = settings.audioBitrate

        // Estimate: (video bitrate + audio bitrate) * duration / 8 (bits to bytes)
        return ((videoBitrate + audioBitrate) * durationSeconds / 8 * 1000).toLong()
    }

    override suspend fun validateProject(project: VideoProject): List<String> {
        val errors = mutableListOf<String>()

        if (project.tracks.isEmpty()) {
            errors.add("Project has no tracks")
        }

        val videoTracks = project.tracks.filter { it.type == TrackType.VIDEO }
        if (videoTracks.isEmpty() || videoTracks.all { it.clips.isEmpty() }) {
            errors.add("Project has no video clips")
        }

        // Check if all video files exist
        videoTracks.flatMap { it.clips }
            .filterIsInstance<VideoClip>()
            .forEach { clip ->
                if (!File(clip.filePath).exists()) {
                    errors.add("Video file not found: ${clip.fileName}")
                }
            }

        return errors
    }

    private fun calculateOutputDimensions(aspectRatio: AspectRatio, resolution: Resolution): Pair<Int, Int> {
        val targetWidth = resolution.width
        val targetHeight = resolution.height

        val aspectWidth = aspectRatio.width.toFloat()
        val aspectHeight = aspectRatio.height.toFloat()

        return if (targetWidth.toFloat() / targetHeight > aspectWidth / aspectHeight) {
            val width = (targetHeight * aspectWidth / aspectHeight).toInt()
            Pair(width - (width % 2), targetHeight)
        } else {
            val height = (targetWidth * aspectHeight / aspectWidth).toInt()
            Pair(targetWidth, height - (height % 2))
        }
    }

    private fun buildInputArguments(project: VideoProject): String {
        val inputs = mutableListOf<String>()

        project.tracks.flatMap { it.clips }
            .filterIsInstance<VideoClip>()
            .distinctBy { it.filePath }
            .forEach { clip ->
                inputs.add("-i \"${clip.filePath}\"")
            }

        project.tracks.flatMap { it.clips }
            .filterIsInstance<AudioClip>()
            .distinctBy { it.filePath }
            .forEach { clip ->
                inputs.add("-i \"${clip.filePath}\"")
            }

        return inputs.joinToString(" ")
    }

    private fun buildFilterComplex(
        project: VideoProject,
        videoTracks: List<Track>,
        audioTracks: List<Track>,
        textTracks: List<Track>,
        outputWidth: Int,
        outputHeight: Int
    ): String {
        if (videoTracks.isEmpty()) return ""

        val filters = mutableListOf<String>()

        // Basic video processing
        filters.add("[0:v]scale=$outputWidth:$outputHeight:force_original_aspect_ratio=decrease,pad=$outputWidth:$outputHeight:(ow-iw)/2:(oh-ih)/2[v0]")

        return "-filter_complex \"${filters.joinToString(";")}\" -map \"[v0]\" -map 0:a?"
    }

    private fun buildOutputArguments(settings: ExportSettings, width: Int, height: Int): String {
        val videoBitrate = (settings.videoBitrate * settings.quality.bitrateMultiplier).toInt()

        return buildString {
            append("-c:v ${settings.codec.ffmpegCodec} ")
            append("-b:v ${videoBitrate}k ")
            append("-r ${settings.frameRate.fps} ")
            append("-pix_fmt yuv420p ")
            if (settings.includeAudio) {
                append("-c:a aac ")
                append("-b:a ${settings.audioBitrate}k ")
            } else {
                append("-an ")
            }
            append("-movflags +faststart ")
        }
    }

    private fun updateProgress(stats: Statistics, totalDuration: Long, onProgress: (ExportProgress) -> Unit) {
        val timeMs = stats.time.toLong()
        val progress = if (totalDuration > 0) {
            (timeMs.toFloat() / totalDuration).coerceIn(0f, 1f)
        } else {
            0f
        }

        onProgress(ExportProgress(
            progress = progress,
            currentFrame = stats.videoFrameNumber,
            elapsedTime = timeMs,
            currentPhase = ExportPhase.ENCODING
        ))
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000.0
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%06.3f", hours, minutes, seconds)
    }
}
