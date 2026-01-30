package com.videoeditor.capcut.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videoeditor.capcut.core.utils.FileManager
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fileManager: FileManager
) : AudioEngine {

    companion object {
        private const val TAG = "AudioEngine"
    }

    private var currentSessionId: Long = -1

    override suspend fun extractAudio(
        videoPath: String,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$videoPath\" -vn -acodec libmp3lame -q:a 2 \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun mergeAudioTracks(
        audioPaths: List<String>,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        if (audioPaths.isEmpty()) return@withContext false
        if (audioPaths.size == 1) {
            File(audioPaths[0]).copyTo(File(outputPath), overwrite = true)
            return@withContext true
        }

        val inputs = audioPaths.joinToString(" ") { "-i \"$it\"" }
        val filterComplex = audioPaths.indices.joinToString("") { "[$it:a]" } +
                "amix=inputs=${audioPaths.size}:duration=longest:dropout_transition=0[aout]"

        val command = "-y $inputs -filter_complex \"$filterComplex\" -map \"[aout]\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun mixAudioWithVideo(
        videoPath: String,
        audioPath: String,
        outputPath: String,
        videoVolume: Float,
        audioVolume: Float
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$videoPath\" -i \"$audioPath\" " +
                "-filter_complex \"[0:a]volume=$videoVolume[v];[1:a]volume=$audioVolume[a];[v][a]amix=inputs=2:duration=first[aout]\" " +
                "-map 0:v -map \"[aout]\" -c:v copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun adjustVolume(
        inputPath: String,
        outputPath: String,
        volume: Float
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$inputPath\" -af \"volume=$volume\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun applyFadeIn(
        inputPath: String,
        outputPath: String,
        durationMs: Long
    ): Boolean = withContext(ioDispatcher) {
        val durationSec = durationMs / 1000.0
        val command = "-y -i \"$inputPath\" -af \"afade=t=in:st=0:d=$durationSec\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun applyFadeOut(
        inputPath: String,
        outputPath: String,
        durationMs: Long
    ): Boolean = withContext(ioDispatcher) {
        val totalDuration = getAudioDuration(inputPath)
        val startTime = (totalDuration - durationMs) / 1000.0
        val durationSec = durationMs / 1000.0

        val command = "-y -i \"$inputPath\" -af \"afade=t=out:st=$startTime:d=$durationSec\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun changePitch(
        inputPath: String,
        outputPath: String,
        pitchFactor: Float
    ): Boolean = withContext(ioDispatcher) {
        // Using rubberband filter for pitch shifting
        val semitones = (12 * kotlin.math.log2(pitchFactor.toDouble())).toInt()
        val command = "-y -i \"$inputPath\" -af \"asetrate=44100*$pitchFactor,aresample=44100\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun changeSpeed(
        inputPath: String,
        outputPath: String,
        speed: Float
    ): Boolean = withContext(ioDispatcher) {
        val atempoFilter = buildAtempoChain(speed)
        val command = "-y -i \"$inputPath\" -af \"$atempoFilter\" \"$outputPath\""
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

    override suspend fun trimAudio(
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

    override suspend fun addVoiceover(
        videoPath: String,
        voiceoverPath: String,
        outputPath: String,
        startTimeMs: Long
    ): Boolean = withContext(ioDispatcher) {
        val delay = startTimeMs / 1000.0

        val command = "-y -i \"$videoPath\" -i \"$voiceoverPath\" " +
                "-filter_complex \"[1:a]adelay=${startTimeMs}|${startTimeMs}[delayed];[0:a][delayed]amix=inputs=2:duration=first[aout]\" " +
                "-map 0:v -map \"[aout]\" -c:v copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun removeAudioFromVideo(
        videoPath: String,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$videoPath\" -an -c:v copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun replaceAudio(
        videoPath: String,
        audioPath: String,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$videoPath\" -i \"$audioPath\" -map 0:v -map 1:a -c:v copy -shortest \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun normalizeAudio(
        inputPath: String,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        val command = "-y -i \"$inputPath\" -af \"loudnorm=I=-16:TP=-1.5:LRA=11\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun applyEcho(
        inputPath: String,
        outputPath: String,
        delay: Float,
        decay: Float
    ): Boolean = withContext(ioDispatcher) {
        val delayMs = (delay * 1000).toInt()
        val command = "-y -i \"$inputPath\" -af \"aecho=0.8:0.9:$delayMs:$decay\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun applyReverb(
        inputPath: String,
        outputPath: String,
        intensity: Float
    ): Boolean = withContext(ioDispatcher) {
        val wetLevel = intensity * 0.5f
        val roomSize = 50 + intensity * 50
        val command = "-y -i \"$inputPath\" -af \"aecho=0.8:0.88:60:0.4,aecho=0.8:0.88:40:0.3\" \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun generateWaveform(
        audioPath: String,
        samplesCount: Int
    ): List<Float> = withContext(ioDispatcher) {
        val waveformData = mutableListOf<Float>()

        try {
            val outputFile = File(fileManager.getTempDir(), "waveform_${System.currentTimeMillis()}.raw")

            // Extract raw audio data
            val command = "-y -i \"$audioPath\" -ac 1 -f s16le -ar 8000 \"${outputFile.absolutePath}\""

            if (executeFFmpegCommand(command)) {
                val bytes = outputFile.readBytes()
                val totalSamples = bytes.size / 2
                val samplesPerChunk = maxOf(1, totalSamples / samplesCount)

                for (i in 0 until minOf(samplesCount, totalSamples / samplesPerChunk)) {
                    var maxAmp = 0f
                    for (j in 0 until samplesPerChunk) {
                        val index = (i * samplesPerChunk + j) * 2
                        if (index + 1 < bytes.size) {
                            val sample = (bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xFF)
                            maxAmp = maxOf(maxAmp, kotlin.math.abs(sample.toFloat()))
                        }
                    }
                    waveformData.add(maxAmp / 32768f)
                }

                outputFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating waveform: ${e.message}")
        }

        waveformData
    }

    override suspend fun getAudioDuration(audioPath: String): Long = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(audioPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            0L
        } finally {
            retriever.release()
        }
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

        return if (ReturnCode.isSuccess(session.returnCode)) {
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
