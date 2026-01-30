package com.videoeditor.capcut.core.text

import android.graphics.Bitmap
import com.videoeditor.capcut.data.models.TextClip
import com.videoeditor.capcut.data.models.TextStyle
import com.videoeditor.capcut.data.models.AnimationType

interface TextRenderer {
    suspend fun renderTextOverlay(
        videoPath: String,
        outputPath: String,
        textClips: List<TextClip>
    ): Boolean

    fun renderTextToBitmap(
        text: String,
        style: TextStyle,
        width: Int,
        height: Int
    ): Bitmap

    fun getAvailableFonts(): List<String>

    fun getFFmpegDrawTextFilter(textClip: TextClip, videoWidth: Int, videoHeight: Int): String

    fun getAnimationFilter(
        animation: AnimationType,
        isEnter: Boolean,
        duration: Long,
        startTime: Long
    ): String
}
