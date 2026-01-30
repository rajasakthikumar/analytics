package com.videoeditor.capcut.core.effects

import android.graphics.Bitmap
import com.videoeditor.capcut.data.models.EffectItem
import com.videoeditor.capcut.data.models.EffectType

interface EffectsEngine {
    suspend fun applyEffect(
        inputPath: String,
        outputPath: String,
        effect: EffectItem,
        startTimeMs: Long,
        endTimeMs: Long
    ): Boolean

    suspend fun applyMultipleEffects(
        inputPath: String,
        outputPath: String,
        effects: List<EffectWithTiming>
    ): Boolean

    fun getEffectPreview(bitmap: Bitmap, effect: EffectItem): Bitmap

    fun getAvailableEffects(): List<EffectItem>

    fun getEffectsByCategory(category: String): List<EffectItem>

    fun getFFmpegEffectFilter(effect: EffectItem): String
}

data class EffectWithTiming(
    val effect: EffectItem,
    val startTimeMs: Long,
    val endTimeMs: Long
)
