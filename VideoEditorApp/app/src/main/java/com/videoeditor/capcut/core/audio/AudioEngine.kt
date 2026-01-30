package com.videoeditor.capcut.core.audio

import com.videoeditor.capcut.data.models.AudioClip

interface AudioEngine {
    suspend fun extractAudio(videoPath: String, outputPath: String): Boolean
    suspend fun mergeAudioTracks(audioPaths: List<String>, outputPath: String): Boolean
    suspend fun mixAudioWithVideo(videoPath: String, audioPath: String, outputPath: String, videoVolume: Float, audioVolume: Float): Boolean
    suspend fun adjustVolume(inputPath: String, outputPath: String, volume: Float): Boolean
    suspend fun applyFadeIn(inputPath: String, outputPath: String, durationMs: Long): Boolean
    suspend fun applyFadeOut(inputPath: String, outputPath: String, durationMs: Long): Boolean
    suspend fun changePitch(inputPath: String, outputPath: String, pitchFactor: Float): Boolean
    suspend fun changeSpeed(inputPath: String, outputPath: String, speed: Float): Boolean
    suspend fun trimAudio(inputPath: String, outputPath: String, startMs: Long, endMs: Long): Boolean
    suspend fun addVoiceover(videoPath: String, voiceoverPath: String, outputPath: String, startTimeMs: Long): Boolean
    suspend fun removeAudioFromVideo(videoPath: String, outputPath: String): Boolean
    suspend fun replaceAudio(videoPath: String, audioPath: String, outputPath: String): Boolean
    suspend fun normalizeAudio(inputPath: String, outputPath: String): Boolean
    suspend fun applyEcho(inputPath: String, outputPath: String, delay: Float, decay: Float): Boolean
    suspend fun applyReverb(inputPath: String, outputPath: String, intensity: Float): Boolean
    suspend fun generateWaveform(audioPath: String, samplesCount: Int): List<Float>
    suspend fun getAudioDuration(audioPath: String): Long
    fun cancelOperation()
}
