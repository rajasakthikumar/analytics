package com.videoeditor.capcut.data.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class MediaItem(
    val id: Long,
    val uri: String,
    val path: String,
    val name: String,
    val type: MediaType,
    val duration: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "",
    val thumbnailPath: String? = null,
    val isSelected: Boolean = false
) : Parcelable {
    val uriObject: Uri get() = Uri.parse(uri)
}

@Serializable
enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO,
    GIF
}

@Parcelize
@Serializable
data class MediaFolder(
    val id: Long,
    val name: String,
    val path: String,
    val coverUri: String?,
    val mediaCount: Int,
    val mediaType: MediaType
) : Parcelable

@Parcelize
@Serializable
data class AudioItem(
    val id: Long,
    val uri: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val albumArt: String? = null,
    val isSelected: Boolean = false
) : Parcelable

@Parcelize
@Serializable
data class FontItem(
    val id: String,
    val name: String,
    val displayName: String,
    val fontPath: String?,
    val isSystem: Boolean = true,
    val preview: String = "Aa"
) : Parcelable

@Parcelize
@Serializable
data class StickerItem(
    val id: String,
    val name: String,
    val path: String,
    val category: String,
    val isAnimated: Boolean = false,
    val thumbnailPath: String? = null
) : Parcelable

@Parcelize
@Serializable
data class StickerCategory(
    val id: String,
    val name: String,
    val iconPath: String,
    val stickers: List<StickerItem> = emptyList()
) : Parcelable

@Parcelize
@Serializable
data class EffectItem(
    val id: String,
    val name: String,
    val type: EffectType,
    val category: String,
    val thumbnailPath: String?,
    val parameters: Map<String, Float> = emptyMap(),
    val isAnimated: Boolean = false
) : Parcelable

@Serializable
enum class EffectType {
    BASIC,
    PARTY,
    RETRO,
    CINEMATIC,
    NATURAL,
    DREAMY,
    GLITCH,
    BLUR,
    DISTORTION,
    COLOR
}

@Parcelize
@Serializable
data class TemplateItem(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailPath: String,
    val previewVideoPath: String?,
    val aspectRatio: AspectRatio,
    val duration: Long,
    val category: String,
    val placeholderCount: Int,
    val projectData: String
) : Parcelable

@Parcelize
@Serializable
data class MusicCategory(
    val id: String,
    val name: String,
    val iconResId: Int = 0,
    val tracks: List<MusicTrack> = emptyList()
) : Parcelable

@Parcelize
@Serializable
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val category: String,
    val thumbnailPath: String? = null,
    val isDownloaded: Boolean = false
) : Parcelable

@Parcelize
@Serializable
data class ExportSettings(
    val resolution: Resolution = Resolution.HD_1080P,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val quality: ExportQuality = ExportQuality.HIGH,
    val format: ExportFormat = ExportFormat.MP4,
    val codec: VideoCodec = VideoCodec.H264,
    val audioBitrate: Int = 192,
    val videoBitrate: Int = 8000,
    val outputPath: String? = null,
    val includeAudio: Boolean = true
) : Parcelable

@Serializable
enum class ExportQuality(val displayName: String, val bitrateMultiplier: Float) {
    LOW("Low", 0.5f),
    MEDIUM("Medium", 0.75f),
    HIGH("High", 1.0f),
    ULTRA("Ultra", 1.5f)
}

@Serializable
enum class ExportFormat(val extension: String, val mimeType: String) {
    MP4("mp4", "video/mp4"),
    MOV("mov", "video/quicktime"),
    WEBM("webm", "video/webm"),
    GIF("gif", "image/gif")
}

@Serializable
enum class VideoCodec(val displayName: String, val ffmpegCodec: String) {
    H264("H.264", "libx264"),
    H265("H.265 (HEVC)", "libx265"),
    VP9("VP9", "libvpx-vp9"),
    AV1("AV1", "libaom-av1")
}

@Parcelize
@Serializable
data class ExportProgress(
    val progress: Float = 0f,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val elapsedTime: Long = 0L,
    val estimatedTimeRemaining: Long = 0L,
    val currentPhase: ExportPhase = ExportPhase.PREPARING,
    val outputPath: String? = null,
    val error: String? = null
) : Parcelable

@Serializable
enum class ExportPhase(val displayName: String) {
    PREPARING("Preparing..."),
    PROCESSING_VIDEO("Processing video..."),
    PROCESSING_AUDIO("Processing audio..."),
    APPLYING_EFFECTS("Applying effects..."),
    RENDERING("Rendering..."),
    ENCODING("Encoding..."),
    FINALIZING("Finalizing..."),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

@Parcelize
@Serializable
data class KeyFrame(
    val id: String,
    val time: Long,
    val property: KeyFrameProperty,
    val value: Float,
    val easing: EasingType = EasingType.LINEAR
) : Parcelable

@Serializable
enum class KeyFrameProperty {
    POSITION_X,
    POSITION_Y,
    SCALE_X,
    SCALE_Y,
    ROTATION,
    OPACITY,
    VOLUME,
    BRIGHTNESS,
    CONTRAST,
    SATURATION
}

@Parcelize
@Serializable
data class UndoAction(
    val id: String,
    val timestamp: Long,
    val type: UndoActionType,
    val description: String,
    val previousState: String,
    val newState: String
) : Parcelable

@Serializable
enum class UndoActionType {
    ADD_CLIP,
    REMOVE_CLIP,
    MOVE_CLIP,
    TRIM_CLIP,
    SPLIT_CLIP,
    MODIFY_CLIP,
    ADD_FILTER,
    REMOVE_FILTER,
    ADD_TEXT,
    MODIFY_TEXT,
    ADD_AUDIO,
    MODIFY_AUDIO,
    ADD_TRANSITION,
    REMOVE_TRANSITION,
    MODIFY_ADJUSTMENT
}
