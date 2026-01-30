package com.videoeditor.capcut.core.text

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videoeditor.capcut.core.utils.FileManager
import com.videoeditor.capcut.data.models.AnimationType
import com.videoeditor.capcut.data.models.TextAlignment
import com.videoeditor.capcut.data.models.TextClip
import com.videoeditor.capcut.data.models.TextStyle
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextRendererImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fileManager: FileManager
) : TextRenderer {

    companion object {
        private const val TAG = "TextRenderer"

        private val SYSTEM_FONTS = listOf(
            "sans-serif",
            "sans-serif-light",
            "sans-serif-thin",
            "sans-serif-condensed",
            "sans-serif-medium",
            "serif",
            "monospace",
            "cursive",
            "casual"
        )
    }

    override suspend fun renderTextOverlay(
        videoPath: String,
        outputPath: String,
        textClips: List<TextClip>
    ): Boolean = withContext(ioDispatcher) {
        if (textClips.isEmpty()) {
            File(videoPath).copyTo(File(outputPath), overwrite = true)
            return@withContext true
        }

        // Get video dimensions
        val (videoWidth, videoHeight) = getVideoDimensions(videoPath)

        // Build filter complex
        val filters = textClips.mapIndexed { index, clip ->
            getFFmpegDrawTextFilter(clip, videoWidth, videoHeight)
        }

        val filterComplex = filters.joinToString(",")

        val command = "-y -i \"$videoPath\" -vf \"$filterComplex\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    private fun getVideoDimensions(videoPath: String): Pair<Int, Int> {
        // Default to 1080p if we can't determine
        return Pair(1920, 1080)
    }

    override fun renderTextToBitmap(
        text: String,
        style: TextStyle,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(style.fontColor)
            textSize = style.fontSize
            typeface = getTypeface(style.fontFamily, style.fontWeight, style.isItalic)
            letterSpacing = style.letterSpacing / 10f

            if (style.strokeWidth > 0) {
                strokeWidth = style.strokeWidth
                this.style = Paint.Style.STROKE
            }

            if (style.shadowRadius > 0) {
                setShadowLayer(
                    style.shadowRadius,
                    style.shadowOffsetX,
                    style.shadowOffsetY,
                    Color.parseColor(style.shadowColor)
                )
            }
        }

        val alignment = when (style.alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, style.lineSpacing)
            .setIncludePad(true)
            .build()

        // Draw stroke first if needed
        if (style.strokeWidth > 0) {
            textPaint.style = Paint.Style.STROKE
            textPaint.color = Color.parseColor(style.strokeColor)
            canvas.save()
            canvas.translate(0f, (height - staticLayout.height) / 2f)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        // Draw fill
        textPaint.style = Paint.Style.FILL
        textPaint.color = Color.parseColor(style.fontColor)
        canvas.save()
        canvas.translate(0f, (height - staticLayout.height) / 2f)
        staticLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun getTypeface(fontFamily: String, weight: Int, isItalic: Boolean): Typeface {
        val style = when {
            weight >= 700 && isItalic -> Typeface.BOLD_ITALIC
            weight >= 700 -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        return try {
            Typeface.create(fontFamily, style)
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, style)
        }
    }

    override fun getAvailableFonts(): List<String> {
        return SYSTEM_FONTS
    }

    override fun getFFmpegDrawTextFilter(
        textClip: TextClip,
        videoWidth: Int,
        videoHeight: Int
    ): String {
        val style = textClip.textStyle
        val position = textClip.position
        val startTime = textClip.startTime / 1000.0
        val endTime = textClip.endTime / 1000.0

        // Calculate position
        val x = (position.x * videoWidth).toInt()
        val y = (position.y * videoHeight).toInt()

        // Escape special characters in text
        val escapedText = escapeTextForFFmpeg(textClip.text)

        // Build font color (ARGB to FFmpeg BGR format)
        val fontColor = style.fontColor.removePrefix("#")
        val borderColor = style.strokeColor.removePrefix("#")
        val shadowColor = style.shadowColor.removePrefix("#")

        val filterBuilder = StringBuilder()
        filterBuilder.append("drawtext=")
        filterBuilder.append("text='$escapedText':")
        filterBuilder.append("fontsize=${style.fontSize.toInt()}:")
        filterBuilder.append("fontcolor=0x$fontColor:")
        filterBuilder.append("x=$x:")
        filterBuilder.append("y=$y:")

        // Add font file if available
        filterBuilder.append("font='${style.fontFamily}':")

        // Border (stroke)
        if (style.strokeWidth > 0) {
            filterBuilder.append("borderw=${style.strokeWidth.toInt()}:")
            filterBuilder.append("bordercolor=0x$borderColor:")
        }

        // Shadow
        if (style.shadowRadius > 0) {
            filterBuilder.append("shadowx=${style.shadowOffsetX.toInt()}:")
            filterBuilder.append("shadowy=${style.shadowOffsetY.toInt()}:")
            filterBuilder.append("shadowcolor=0x$shadowColor@0.5:")
        }

        // Time enable
        filterBuilder.append("enable='between(t,$startTime,$endTime)'")

        // Add animation if present
        val animation = textClip.animation
        if (animation.enterAnimation != AnimationType.NONE) {
            val enterFilter = getAnimationFilter(
                animation.enterAnimation,
                true,
                animation.enterDuration,
                textClip.startTime
            )
            // Animation would be added as additional filter
        }

        return filterBuilder.toString()
    }

    override fun getAnimationFilter(
        animation: AnimationType,
        isEnter: Boolean,
        duration: Long,
        startTime: Long
    ): String {
        val durationSec = duration / 1000.0
        val startSec = startTime / 1000.0
        val endSec = startSec + durationSec

        return when (animation) {
            AnimationType.FADE_IN -> {
                "fade=t=in:st=$startSec:d=$durationSec:alpha=1"
            }
            AnimationType.FADE_OUT -> {
                "fade=t=out:st=$startSec:d=$durationSec:alpha=1"
            }
            AnimationType.SLIDE_LEFT -> {
                if (isEnter) {
                    "overlay=x='if(lt(t,$endSec),W-(t-$startSec)/$durationSec*W,0)':y=0"
                } else {
                    "overlay=x='if(gt(t,$startSec),(t-$startSec)/$durationSec*-W,0)':y=0"
                }
            }
            AnimationType.SLIDE_RIGHT -> {
                if (isEnter) {
                    "overlay=x='if(lt(t,$endSec),-W+(t-$startSec)/$durationSec*W,0)':y=0"
                } else {
                    "overlay=x='if(gt(t,$startSec),(t-$startSec)/$durationSec*W,0)':y=0"
                }
            }
            AnimationType.ZOOM_IN -> {
                "scale=w='if(lt(t,$endSec),iw*(0.5+(t-$startSec)/$durationSec*0.5),iw)':h=-1"
            }
            AnimationType.ZOOM_OUT -> {
                "scale=w='if(gt(t,$startSec),iw*(1-(t-$startSec)/$durationSec*0.5),iw)':h=-1"
            }
            else -> ""
        }
    }

    private fun escapeTextForFFmpeg(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "'\\''")
            .replace("%", "\\%")
            .replace(":", "\\:")
            .replace("\n", "\\n")
    }

    private fun executeFFmpegCommand(command: String): Boolean {
        Log.d(TAG, "Executing FFmpeg command: $command")
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }
}
