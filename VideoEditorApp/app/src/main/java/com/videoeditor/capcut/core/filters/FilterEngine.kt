package com.videoeditor.capcut.core.filters

import android.graphics.Bitmap
import com.videoeditor.capcut.data.models.FilterType
import com.videoeditor.capcut.data.models.VideoAdjustments
import com.videoeditor.capcut.data.models.VideoFilter

interface FilterEngine {
    suspend fun applyFilter(bitmap: Bitmap, filter: VideoFilter): Bitmap
    suspend fun applyAdjustments(bitmap: Bitmap, adjustments: VideoAdjustments): Bitmap
    suspend fun applyFilterToVideo(inputPath: String, outputPath: String, filter: VideoFilter): Boolean
    suspend fun applyAdjustmentsToVideo(inputPath: String, outputPath: String, adjustments: VideoAdjustments): Boolean
    fun getFilterPreview(filter: FilterType): Bitmap?
    fun getAvailableFilters(): List<FilterType>
    fun getFFmpegFilterString(filter: VideoFilter): String
    fun getFFmpegAdjustmentsString(adjustments: VideoAdjustments): String
}
