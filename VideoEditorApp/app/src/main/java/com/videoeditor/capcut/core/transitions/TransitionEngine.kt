package com.videoeditor.capcut.core.transitions

import android.graphics.Bitmap
import com.videoeditor.capcut.data.models.Transition
import com.videoeditor.capcut.data.models.TransitionType

interface TransitionEngine {
    suspend fun applyTransition(
        clip1Path: String,
        clip2Path: String,
        outputPath: String,
        transition: Transition
    ): Boolean

    suspend fun applyTransitionToClips(
        clipPaths: List<String>,
        transitions: List<Transition>,
        outputPath: String
    ): Boolean

    fun getTransitionPreview(
        frame1: Bitmap,
        frame2: Bitmap,
        transition: TransitionType,
        progress: Float
    ): Bitmap

    fun getAvailableTransitions(): List<TransitionType>

    fun getFFmpegTransitionFilter(transition: Transition): String
}
