package com.videoeditor.capcut.core.transitions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videoeditor.capcut.core.utils.FileManager
import com.videoeditor.capcut.data.models.EasingType
import com.videoeditor.capcut.data.models.Transition
import com.videoeditor.capcut.data.models.TransitionType
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class TransitionEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fileManager: FileManager
) : TransitionEngine {

    companion object {
        private const val TAG = "TransitionEngine"
    }

    override suspend fun applyTransition(
        clip1Path: String,
        clip2Path: String,
        outputPath: String,
        transition: Transition
    ): Boolean = withContext(ioDispatcher) {
        val transitionDuration = transition.duration / 1000.0
        val filter = getFFmpegTransitionFilter(transition)

        val command = buildString {
            append("-y -i \"$clip1Path\" -i \"$clip2Path\" ")
            append("-filter_complex \"")
            append("[0:v][1:v]xfade=transition=${getXfadeTransition(transition.type)}:")
            append("duration=$transitionDuration:offset=0[v];")
            append("[0:a][1:a]acrossfade=d=$transitionDuration[a]\" ")
            append("-map \"[v]\" -map \"[a]\" \"$outputPath\"")
        }

        executeFFmpegCommand(command)
    }

    override suspend fun applyTransitionToClips(
        clipPaths: List<String>,
        transitions: List<Transition>,
        outputPath: String
    ): Boolean = withContext(ioDispatcher) {
        if (clipPaths.size < 2) {
            if (clipPaths.isNotEmpty()) {
                File(clipPaths[0]).copyTo(File(outputPath), overwrite = true)
                return@withContext true
            }
            return@withContext false
        }

        // Process clips in pairs with transitions
        var currentOutput = clipPaths[0]
        val tempFiles = mutableListOf<String>()

        for (i in 1 until clipPaths.size) {
            val transition = transitions.getOrNull(i - 1) ?: Transition(
                type = TransitionType.FADE,
                duration = 500L
            )

            val tempOutput = "${fileManager.getTempDir()}/transition_${System.currentTimeMillis()}_$i.mp4"
            tempFiles.add(tempOutput)

            val success = applyTransition(currentOutput, clipPaths[i], tempOutput, transition)
            if (!success) {
                tempFiles.forEach { File(it).delete() }
                return@withContext false
            }

            currentOutput = tempOutput
        }

        // Move final output
        File(currentOutput).copyTo(File(outputPath), overwrite = true)

        // Clean up temp files
        tempFiles.forEach { File(it).delete() }

        true
    }

    override fun getTransitionPreview(
        frame1: Bitmap,
        frame2: Bitmap,
        transition: TransitionType,
        progress: Float
    ): Bitmap {
        val width = frame1.width
        val height = frame1.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (transition) {
            TransitionType.FADE, TransitionType.DISSOLVE -> {
                canvas.drawBitmap(frame1, 0f, 0f, paint)
                paint.alpha = (progress * 255).toInt()
                canvas.drawBitmap(frame2, 0f, 0f, paint)
            }

            TransitionType.WIPE_LEFT -> {
                val splitX = width * (1 - progress)
                canvas.save()
                canvas.clipRect(splitX, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(frame2, 0f, 0f, paint)
                canvas.restore()
                canvas.save()
                canvas.clipRect(0f, 0f, splitX, height.toFloat())
                canvas.drawBitmap(frame1, 0f, 0f, paint)
                canvas.restore()
            }

            TransitionType.WIPE_RIGHT -> {
                val splitX = width * progress
                canvas.save()
                canvas.clipRect(0f, 0f, splitX, height.toFloat())
                canvas.drawBitmap(frame2, 0f, 0f, paint)
                canvas.restore()
                canvas.save()
                canvas.clipRect(splitX, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(frame1, 0f, 0f, paint)
                canvas.restore()
            }

            TransitionType.SLIDE_LEFT -> {
                val offset = width * progress
                canvas.drawBitmap(frame1, -offset, 0f, paint)
                canvas.drawBitmap(frame2, width - offset, 0f, paint)
            }

            TransitionType.SLIDE_RIGHT -> {
                val offset = width * progress
                canvas.drawBitmap(frame1, offset, 0f, paint)
                canvas.drawBitmap(frame2, -width + offset, 0f, paint)
            }

            TransitionType.ZOOM_IN -> {
                val scale = 1 + progress * 0.5f
                val scaledWidth = width * scale
                val scaledHeight = height * scale
                val left = (width - scaledWidth) / 2
                val top = (height - scaledHeight) / 2

                paint.alpha = ((1 - progress) * 255).toInt()
                canvas.save()
                canvas.scale(scale, scale, width / 2f, height / 2f)
                canvas.drawBitmap(frame1, 0f, 0f, paint)
                canvas.restore()

                paint.alpha = (progress * 255).toInt()
                canvas.drawBitmap(frame2, 0f, 0f, paint)
            }

            TransitionType.ZOOM_OUT -> {
                val scale = 1 + (1 - progress) * 0.5f
                paint.alpha = ((1 - progress) * 255).toInt()
                canvas.drawBitmap(frame1, 0f, 0f, paint)

                paint.alpha = (progress * 255).toInt()
                canvas.save()
                canvas.scale(1 / scale, 1 / scale, width / 2f, height / 2f)
                canvas.drawBitmap(frame2, 0f, 0f, paint)
                canvas.restore()
            }

            TransitionType.CIRCLE -> {
                canvas.drawBitmap(frame1, 0f, 0f, paint)
                val radius = kotlin.math.sqrt((width * width + height * height).toFloat()) * progress / 2
                val path = android.graphics.Path()
                path.addCircle(width / 2f, height / 2f, radius, android.graphics.Path.Direction.CW)
                canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(frame2, 0f, 0f, paint)
                canvas.restore()
            }

            else -> {
                // Default to crossfade
                canvas.drawBitmap(frame1, 0f, 0f, paint)
                paint.alpha = (progress * 255).toInt()
                canvas.drawBitmap(frame2, 0f, 0f, paint)
            }
        }

        return result
    }

    override fun getAvailableTransitions(): List<TransitionType> {
        return TransitionType.entries.toList()
    }

    override fun getFFmpegTransitionFilter(transition: Transition): String {
        val duration = transition.duration / 1000.0
        return when (transition.type) {
            TransitionType.FADE -> "fade"
            TransitionType.DISSOLVE -> "dissolve"
            TransitionType.WIPE_LEFT -> "wipeleft"
            TransitionType.WIPE_RIGHT -> "wiperight"
            TransitionType.WIPE_UP -> "wipeup"
            TransitionType.WIPE_DOWN -> "wipedown"
            TransitionType.SLIDE_LEFT -> "slideleft"
            TransitionType.SLIDE_RIGHT -> "slideright"
            TransitionType.SLIDE_UP -> "slideup"
            TransitionType.SLIDE_DOWN -> "slidedown"
            TransitionType.ZOOM_IN -> "zoomin"
            TransitionType.CIRCLE -> "circleopen"
            TransitionType.DIAMOND -> "diagtl"
            TransitionType.CLOCK -> "radial"
            TransitionType.PIXELATE -> "pixelize"
            TransitionType.BLUR -> "fadeblack"
            TransitionType.FLASH -> "fadewhite"
            else -> "fade"
        }
    }

    private fun getXfadeTransition(type: TransitionType): String {
        return when (type) {
            TransitionType.FADE -> "fade"
            TransitionType.DISSOLVE -> "dissolve"
            TransitionType.WIPE_LEFT -> "wipeleft"
            TransitionType.WIPE_RIGHT -> "wiperight"
            TransitionType.WIPE_UP -> "wipeup"
            TransitionType.WIPE_DOWN -> "wipedown"
            TransitionType.SLIDE_LEFT -> "slideleft"
            TransitionType.SLIDE_RIGHT -> "slideright"
            TransitionType.SLIDE_UP -> "slideup"
            TransitionType.SLIDE_DOWN -> "slidedown"
            TransitionType.ZOOM_IN -> "zoomin"
            TransitionType.ZOOM_OUT -> "zoomin" // FFmpeg doesn't have zoomout, use zoomin reversed
            TransitionType.CIRCLE -> "circleopen"
            TransitionType.DIAMOND -> "diagtl"
            TransitionType.CLOCK -> "radial"
            TransitionType.PIXELATE -> "pixelize"
            TransitionType.BLUR -> "fadeblack"
            TransitionType.FLASH -> "fadewhite"
            TransitionType.RIPPLE -> "smoothleft"
            TransitionType.GLITCH -> "horzclose"
            TransitionType.SHAKE -> "vertopen"
            TransitionType.STAR -> "diagtr"
            TransitionType.HEART -> "circleclose"
            TransitionType.SPIN -> "circlecrop"
            TransitionType.FLIP_HORIZONTAL -> "horzopen"
            TransitionType.FLIP_VERTICAL -> "vertopen"
            TransitionType.NONE -> "fade"
        }
    }

    private fun applyEasing(progress: Float, easing: EasingType): Float {
        return when (easing) {
            EasingType.LINEAR -> progress
            EasingType.EASE_IN -> progress * progress
            EasingType.EASE_OUT -> 1 - (1 - progress) * (1 - progress)
            EasingType.EASE_IN_OUT -> {
                if (progress < 0.5f) {
                    2 * progress * progress
                } else {
                    1 - (-2 * progress + 2) * (-2 * progress + 2) / 2
                }
            }
            EasingType.BOUNCE -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                var p = progress
                when {
                    p < 1 / d1 -> n1 * p * p
                    p < 2 / d1 -> {
                        p -= 1.5f / d1
                        n1 * p * p + 0.75f
                    }
                    p < 2.5 / d1 -> {
                        p -= 2.25f / d1
                        n1 * p * p + 0.9375f
                    }
                    else -> {
                        p -= 2.625f / d1
                        n1 * p * p + 0.984375f
                    }
                }
            }
            EasingType.ELASTIC -> {
                if (progress == 0f || progress == 1f) progress
                else {
                    val p = 0.3f
                    val s = p / 4
                    (kotlin.math.pow(2.0, -10.0 * progress) *
                            sin((progress - s) * (2 * Math.PI) / p) + 1).toFloat()
                }
            }
            EasingType.BACK -> {
                val c1 = 1.70158f
                val c3 = c1 + 1
                1 + c3 * kotlin.math.pow((progress - 1).toDouble(), 3.0).toFloat() +
                        c1 * kotlin.math.pow((progress - 1).toDouble(), 2.0).toFloat()
            }
        }
    }

    private fun executeFFmpegCommand(command: String): Boolean {
        Log.d(TAG, "Executing FFmpeg command: $command")
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }
}
