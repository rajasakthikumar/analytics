package com.videoeditor.capcut.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.UUID

@Parcelize
@Serializable
data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val resolution: Resolution = Resolution.HD_1080P,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val tracks: List<Track> = emptyList(),
    val duration: Long = 0L,
    val thumbnailPath: String? = null
) : Parcelable

@Parcelize
@Serializable
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val clips: List<Clip> = emptyList(),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val isMuted: Boolean = false
) : Parcelable

@Parcelize
@Serializable
sealed class Clip : Parcelable {
    abstract val id: String
    abstract val startTime: Long
    abstract val endTime: Long
    abstract val trackPosition: Int
    abstract val sourceStartTime: Long
    abstract val sourceEndTime: Long

    val duration: Long get() = endTime - startTime
}

@Parcelize
@Serializable
data class VideoClip(
    override val id: String = UUID.randomUUID().toString(),
    override val startTime: Long,
    override val endTime: Long,
    override val trackPosition: Int = 0,
    override val sourceStartTime: Long = 0L,
    override val sourceEndTime: Long,
    val filePath: String,
    val fileName: String,
    val originalDuration: Long,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val filters: List<VideoFilter> = emptyList(),
    val adjustments: VideoAdjustments = VideoAdjustments(),
    val transform: Transform = Transform(),
    val transitions: ClipTransitions = ClipTransitions()
) : Clip()

@Parcelize
@Serializable
data class AudioClip(
    override val id: String = UUID.randomUUID().toString(),
    override val startTime: Long,
    override val endTime: Long,
    override val trackPosition: Int = 0,
    override val sourceStartTime: Long = 0L,
    override val sourceEndTime: Long,
    val filePath: String,
    val fileName: String,
    val originalDuration: Long,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val fadeIn: Long = 0L,
    val fadeOut: Long = 0L,
    val pitch: Float = 1.0f,
    val isVoiceover: Boolean = false
) : Clip()

@Parcelize
@Serializable
data class TextClip(
    override val id: String = UUID.randomUUID().toString(),
    override val startTime: Long,
    override val endTime: Long,
    override val trackPosition: Int = 0,
    override val sourceStartTime: Long = 0L,
    override val sourceEndTime: Long,
    val text: String,
    val textStyle: TextStyle = TextStyle(),
    val animation: TextAnimation = TextAnimation(),
    val position: Position = Position(),
    val backgroundColor: String? = null,
    val backgroundOpacity: Float = 0f
) : Clip()

@Parcelize
@Serializable
data class StickerClip(
    override val id: String = UUID.randomUUID().toString(),
    override val startTime: Long,
    override val endTime: Long,
    override val trackPosition: Int = 0,
    override val sourceStartTime: Long = 0L,
    override val sourceEndTime: Long,
    val stickerPath: String,
    val position: Position = Position(),
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val animation: StickerAnimation? = null
) : Clip()

@Parcelize
@Serializable
data class ImageClip(
    override val id: String = UUID.randomUUID().toString(),
    override val startTime: Long,
    override val endTime: Long,
    override val trackPosition: Int = 0,
    override val sourceStartTime: Long = 0L,
    override val sourceEndTime: Long,
    val filePath: String,
    val width: Int,
    val height: Int,
    val transform: Transform = Transform(),
    val filters: List<VideoFilter> = emptyList(),
    val adjustments: VideoAdjustments = VideoAdjustments(),
    val animation: ImageAnimation? = null
) : Clip()

@Parcelize
@Serializable
data class VideoFilter(
    val id: String = UUID.randomUUID().toString(),
    val type: FilterType,
    val intensity: Float = 1.0f,
    val parameters: Map<String, Float> = emptyMap()
) : Parcelable

@Parcelize
@Serializable
data class VideoAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f
) : Parcelable

@Parcelize
@Serializable
data class Transform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val opacity: Float = 1f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) : Parcelable

@Parcelize
@Serializable
data class Position(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f
) : Parcelable

@Parcelize
@Serializable
data class TextStyle(
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 48f,
    val fontWeight: Int = 400,
    val fontColor: String = "#FFFFFF",
    val strokeColor: String = "#000000",
    val strokeWidth: Float = 0f,
    val shadowColor: String = "#000000",
    val shadowRadius: Float = 0f,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.2f,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false
) : Parcelable

@Parcelize
@Serializable
data class TextAnimation(
    val enterAnimation: AnimationType = AnimationType.NONE,
    val exitAnimation: AnimationType = AnimationType.NONE,
    val loopAnimation: AnimationType = AnimationType.NONE,
    val enterDuration: Long = 500L,
    val exitDuration: Long = 500L
) : Parcelable

@Parcelize
@Serializable
data class StickerAnimation(
    val type: AnimationType = AnimationType.NONE,
    val duration: Long = 500L,
    val loop: Boolean = false
) : Parcelable

@Parcelize
@Serializable
data class ImageAnimation(
    val type: ImageAnimationType = ImageAnimationType.NONE,
    val startScale: Float = 1f,
    val endScale: Float = 1f,
    val startPosition: Position = Position(),
    val endPosition: Position = Position()
) : Parcelable

@Parcelize
@Serializable
data class ClipTransitions(
    val enterTransition: Transition? = null,
    val exitTransition: Transition? = null
) : Parcelable

@Parcelize
@Serializable
data class Transition(
    val id: String = UUID.randomUUID().toString(),
    val type: TransitionType,
    val duration: Long = 500L,
    val easing: EasingType = EasingType.EASE_IN_OUT
) : Parcelable

@Serializable
enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT,
    STICKER,
    EFFECT
}

@Serializable
enum class AspectRatio(val width: Int, val height: Int, val displayName: String) {
    RATIO_16_9(16, 9, "16:9"),
    RATIO_9_16(9, 16, "9:16"),
    RATIO_1_1(1, 1, "1:1"),
    RATIO_4_3(4, 3, "4:3"),
    RATIO_3_4(3, 4, "3:4"),
    RATIO_4_5(4, 5, "4:5"),
    RATIO_21_9(21, 9, "21:9")
}

@Serializable
enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    SD_480P(854, 480, "480p"),
    HD_720P(1280, 720, "720p"),
    HD_1080P(1920, 1080, "1080p"),
    UHD_2K(2560, 1440, "2K"),
    UHD_4K(3840, 2160, "4K")
}

@Serializable
enum class FrameRate(val fps: Int, val displayName: String) {
    FPS_24(24, "24 fps"),
    FPS_25(25, "25 fps"),
    FPS_30(30, "30 fps"),
    FPS_50(50, "50 fps"),
    FPS_60(60, "60 fps")
}

@Serializable
enum class FilterType(val displayName: String) {
    // Basic Filters
    NONE("None"),
    GRAYSCALE("Grayscale"),
    SEPIA("Sepia"),
    VINTAGE("Vintage"),
    WARM("Warm"),
    COOL("Cool"),
    VIVID("Vivid"),
    DRAMATIC("Dramatic"),

    // Instagram-style Filters
    CLARENDON("Clarendon"),
    GINGHAM("Gingham"),
    MOON("Moon"),
    LARK("Lark"),
    REYES("Reyes"),
    JUNO("Juno"),
    SLUMBER("Slumber"),
    CREMA("Crema"),
    LUDWIG("Ludwig"),
    ADEN("Aden"),
    PERPETUA("Perpetua"),

    // Film Filters
    KODAK("Kodak"),
    FUJI("Fuji"),
    POLAROID("Polaroid"),
    CINEMATIC("Cinematic"),
    NOIR("Noir"),

    // Creative Filters
    GLITCH("Glitch"),
    VHS("VHS"),
    RETRO("Retro"),
    NEON("Neon"),
    COMIC("Comic"),
    SKETCH("Sketch"),
    OIL_PAINT("Oil Paint"),
    WATERCOLOR("Watercolor")
}

@Serializable
enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    DISSOLVE("Dissolve"),
    WIPE_LEFT("Wipe Left"),
    WIPE_RIGHT("Wipe Right"),
    WIPE_UP("Wipe Up"),
    WIPE_DOWN("Wipe Down"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    SLIDE_UP("Slide Up"),
    SLIDE_DOWN("Slide Down"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    SPIN("Spin"),
    FLIP_HORIZONTAL("Flip Horizontal"),
    FLIP_VERTICAL("Flip Vertical"),
    BLUR("Blur"),
    PIXELATE("Pixelate"),
    CIRCLE("Circle"),
    HEART("Heart"),
    STAR("Star"),
    DIAMOND("Diamond"),
    CLOCK("Clock"),
    RIPPLE("Ripple"),
    GLITCH("Glitch"),
    FLASH("Flash"),
    SHAKE("Shake")
}

@Serializable
enum class AnimationType(val displayName: String) {
    NONE("None"),
    FADE_IN("Fade In"),
    FADE_OUT("Fade Out"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    SLIDE_UP("Slide Up"),
    SLIDE_DOWN("Slide Down"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    BOUNCE("Bounce"),
    SHAKE("Shake"),
    ROTATE("Rotate"),
    FLIP("Flip"),
    TYPEWRITER("Typewriter"),
    WAVE("Wave"),
    PULSE("Pulse"),
    SWING("Swing"),
    WOBBLE("Wobble"),
    JELLO("Jello"),
    RUBBER_BAND("Rubber Band"),
    FLASH("Flash"),
    TADA("Tada"),
    HEARTBEAT("Heartbeat")
}

@Serializable
enum class ImageAnimationType(val displayName: String) {
    NONE("None"),
    KEN_BURNS("Ken Burns"),
    PAN_LEFT("Pan Left"),
    PAN_RIGHT("Pan Right"),
    PAN_UP("Pan Up"),
    PAN_DOWN("Pan Down"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    ROTATE("Rotate")
}

@Serializable
enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

@Serializable
enum class EasingType(val displayName: String) {
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In Out"),
    BOUNCE("Bounce"),
    ELASTIC("Elastic"),
    BACK("Back")
}
