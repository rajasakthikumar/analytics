package com.videoeditor.capcut.core.effects

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videoeditor.capcut.data.models.EffectItem
import com.videoeditor.capcut.data.models.EffectType
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EffectsEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : EffectsEngine {

    companion object {
        private const val TAG = "EffectsEngine"
    }

    private val gpuImage: GPUImage by lazy { GPUImage(context) }

    private val availableEffects: List<EffectItem> = listOf(
        // Basic Effects
        EffectItem("blur_soft", "Soft Blur", EffectType.BLUR, "Basic", null, mapOf("intensity" to 0.5f)),
        EffectItem("blur_gaussian", "Gaussian Blur", EffectType.BLUR, "Basic", null, mapOf("intensity" to 0.7f)),
        EffectItem("blur_motion", "Motion Blur", EffectType.BLUR, "Basic", null, mapOf("intensity" to 0.6f)),
        EffectItem("blur_radial", "Radial Blur", EffectType.BLUR, "Basic", null, mapOf("intensity" to 0.5f)),
        EffectItem("blur_zoom", "Zoom Blur", EffectType.BLUR, "Basic", null, mapOf("intensity" to 0.5f)),

        // Party Effects
        EffectItem("party_strobe", "Strobe", EffectType.PARTY, "Party", null, mapOf("speed" to 0.5f), true),
        EffectItem("party_disco", "Disco", EffectType.PARTY, "Party", null, mapOf("speed" to 0.5f), true),
        EffectItem("party_pulse", "Pulse", EffectType.PARTY, "Party", null, mapOf("speed" to 0.5f), true),
        EffectItem("party_rave", "Rave", EffectType.PARTY, "Party", null, mapOf("intensity" to 0.7f), true),
        EffectItem("party_flash", "Flash", EffectType.PARTY, "Party", null, mapOf("speed" to 0.3f), true),

        // Retro Effects
        EffectItem("retro_vhs", "VHS", EffectType.RETRO, "Retro", null, mapOf("intensity" to 0.7f), true),
        EffectItem("retro_8bit", "8-Bit", EffectType.RETRO, "Retro", null, mapOf("pixelSize" to 8f)),
        EffectItem("retro_oldfilm", "Old Film", EffectType.RETRO, "Retro", null, mapOf("grain" to 0.5f), true),
        EffectItem("retro_crt", "CRT", EffectType.RETRO, "Retro", null, mapOf("scanlines" to 0.5f), true),
        EffectItem("retro_vintage", "Vintage TV", EffectType.RETRO, "Retro", null, mapOf("intensity" to 0.6f), true),

        // Cinematic Effects
        EffectItem("cine_letterbox", "Letterbox", EffectType.CINEMATIC, "Cinematic", null, mapOf("ratio" to 2.35f)),
        EffectItem("cine_shake", "Camera Shake", EffectType.CINEMATIC, "Cinematic", null, mapOf("intensity" to 0.5f), true),
        EffectItem("cine_filmgrain", "Film Grain", EffectType.CINEMATIC, "Cinematic", null, mapOf("grain" to 0.4f), true),
        EffectItem("cine_vignette", "Vignette", EffectType.CINEMATIC, "Cinematic", null, mapOf("intensity" to 0.5f)),
        EffectItem("cine_lens", "Lens Flare", EffectType.CINEMATIC, "Cinematic", null, mapOf("intensity" to 0.6f), true),

        // Glitch Effects
        EffectItem("glitch_rgb", "RGB Split", EffectType.GLITCH, "Glitch", null, mapOf("offset" to 10f), true),
        EffectItem("glitch_digital", "Digital Glitch", EffectType.GLITCH, "Glitch", null, mapOf("intensity" to 0.6f), true),
        EffectItem("glitch_scan", "Scan Lines", EffectType.GLITCH, "Glitch", null, mapOf("lines" to 100f), true),
        EffectItem("glitch_noise", "Noise", EffectType.GLITCH, "Glitch", null, mapOf("intensity" to 0.5f), true),
        EffectItem("glitch_shake", "Glitch Shake", EffectType.GLITCH, "Glitch", null, mapOf("intensity" to 0.7f), true),

        // Natural Effects
        EffectItem("natural_sun", "Sunlight", EffectType.NATURAL, "Natural", null, mapOf("intensity" to 0.5f)),
        EffectItem("natural_rain", "Rain", EffectType.NATURAL, "Natural", null, mapOf("density" to 0.5f), true),
        EffectItem("natural_snow", "Snow", EffectType.NATURAL, "Natural", null, mapOf("density" to 0.5f), true),
        EffectItem("natural_fog", "Fog", EffectType.NATURAL, "Natural", null, mapOf("density" to 0.4f)),
        EffectItem("natural_dust", "Dust Particles", EffectType.NATURAL, "Natural", null, mapOf("density" to 0.3f), true),

        // Dreamy Effects
        EffectItem("dreamy_glow", "Soft Glow", EffectType.DREAMY, "Dreamy", null, mapOf("intensity" to 0.5f)),
        EffectItem("dreamy_bloom", "Bloom", EffectType.DREAMY, "Dreamy", null, mapOf("intensity" to 0.6f)),
        EffectItem("dreamy_haze", "Haze", EffectType.DREAMY, "Dreamy", null, mapOf("intensity" to 0.4f)),
        EffectItem("dreamy_bokeh", "Bokeh", EffectType.DREAMY, "Dreamy", null, mapOf("size" to 0.5f)),
        EffectItem("dreamy_fairy", "Fairy Dust", EffectType.DREAMY, "Dreamy", null, mapOf("density" to 0.4f), true),

        // Color Effects
        EffectItem("color_negative", "Negative", EffectType.COLOR, "Color", null),
        EffectItem("color_thermal", "Thermal", EffectType.COLOR, "Color", null),
        EffectItem("color_xray", "X-Ray", EffectType.COLOR, "Color", null),
        EffectItem("color_posterize", "Posterize", EffectType.COLOR, "Color", null, mapOf("levels" to 4f)),
        EffectItem("color_solarize", "Solarize", EffectType.COLOR, "Color", null, mapOf("threshold" to 0.5f)),

        // Distortion Effects
        EffectItem("distort_wave", "Wave", EffectType.DISTORTION, "Distortion", null, mapOf("amplitude" to 10f), true),
        EffectItem("distort_bulge", "Bulge", EffectType.DISTORTION, "Distortion", null, mapOf("strength" to 0.5f)),
        EffectItem("distort_pinch", "Pinch", EffectType.DISTORTION, "Distortion", null, mapOf("strength" to 0.5f)),
        EffectItem("distort_swirl", "Swirl", EffectType.DISTORTION, "Distortion", null, mapOf("angle" to 45f)),
        EffectItem("distort_stretch", "Stretch", EffectType.DISTORTION, "Distortion", null, mapOf("amount" to 0.5f))
    )

    override suspend fun applyEffect(
        inputPath: String,
        outputPath: String,
        effect: EffectItem,
        startTimeMs: Long,
        endTimeMs: Long
    ): Boolean = withContext(ioDispatcher) {
        val filter = getFFmpegEffectFilter(effect)
        if (filter.isEmpty()) return@withContext false

        val startSec = startTimeMs / 1000.0
        val endSec = endTimeMs / 1000.0

        val command = "-y -i \"$inputPath\" -vf \"$filter:enable='between(t,$startSec,$endSec)'\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun applyMultipleEffects(
        inputPath: String,
        outputPath: String,
        effects: List<EffectWithTiming>
    ): Boolean = withContext(ioDispatcher) {
        if (effects.isEmpty()) return@withContext false

        val filters = effects.map { effectWithTiming ->
            val filter = getFFmpegEffectFilter(effectWithTiming.effect)
            val startSec = effectWithTiming.startTimeMs / 1000.0
            val endSec = effectWithTiming.endTimeMs / 1000.0
            "$filter:enable='between(t,$startSec,$endSec)'"
        }

        val filterComplex = filters.joinToString(",")
        val command = "-y -i \"$inputPath\" -vf \"$filterComplex\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override fun getEffectPreview(bitmap: Bitmap, effect: EffectItem): Bitmap {
        gpuImage.setImage(bitmap)
        gpuImage.setFilter(createGPUImageFilter(effect))
        return gpuImage.bitmapWithFilterApplied
    }

    override fun getAvailableEffects(): List<EffectItem> = availableEffects

    override fun getEffectsByCategory(category: String): List<EffectItem> {
        return availableEffects.filter { it.category == category }
    }

    override fun getFFmpegEffectFilter(effect: EffectItem): String {
        val params = effect.parameters
        return when (effect.id) {
            // Blur Effects
            "blur_soft" -> "boxblur=${((params["intensity"] ?: 0.5f) * 10).toInt()}"
            "blur_gaussian" -> "gblur=sigma=${(params["intensity"] ?: 0.7f) * 10}"
            "blur_motion" -> "avgblur=sizeX=${((params["intensity"] ?: 0.6f) * 20).toInt()}:planes=0xF"
            "blur_radial" -> "zscale=zoom=${1 + (params["intensity"] ?: 0.5f)}"
            "blur_zoom" -> "zoompan=z='1.2':d=1"

            // Party Effects
            "party_strobe" -> "lutyuv=y='if(mod(t*${(params["speed"] ?: 0.5f) * 20},2),val,255-val)'"
            "party_disco" -> "hue=H=${(params["speed"] ?: 0.5f) * 360}*t"
            "party_pulse" -> "fade=t=in:d=0.1,fade=t=out:d=0.1:st=0.1"
            "party_rave" -> "eq=saturation=${1 + (params["intensity"] ?: 0.7f)},hue=H=10*t"
            "party_flash" -> "fade=t=in:st=0:d=${params["speed"] ?: 0.3f}:alpha=1"

            // Retro Effects
            "retro_vhs" -> "noise=alls=${((params["intensity"] ?: 0.7f) * 30).toInt()}:allf=t,eq=saturation=0.9"
            "retro_8bit" -> "pixelize=w=${(params["pixelSize"] ?: 8f).toInt()}:h=${(params["pixelSize"] ?: 8f).toInt()}"
            "retro_oldfilm" -> "noise=alls=${((params["grain"] ?: 0.5f) * 50).toInt()}:allf=t+u,curves=vintage"
            "retro_crt" -> "interlace=scan=1:lowpass=1"
            "retro_vintage" -> "colortemperature=temperature=5500,curves=vintage"

            // Cinematic Effects
            "cine_letterbox" -> "crop=iw:ih/${params["ratio"] ?: 2.35f},pad=iw:iw/${params["ratio"] ?: 2.35f}:(ow-iw)/2:(oh-ih)/2"
            "cine_shake" -> "crop=in_w-${((params["intensity"] ?: 0.5f) * 20).toInt()}:in_h-${((params["intensity"] ?: 0.5f) * 20).toInt()}:${((params["intensity"] ?: 0.5f) * 10).toInt()}*sin(t*10):${((params["intensity"] ?: 0.5f) * 10).toInt()}*cos(t*10)"
            "cine_filmgrain" -> "noise=alls=${((params["grain"] ?: 0.4f) * 40).toInt()}:allf=t"
            "cine_vignette" -> "vignette=PI/${4 - (params["intensity"] ?: 0.5f) * 2}"
            "cine_lens" -> "lenscorrection=k1=${(params["intensity"] ?: 0.6f) * 0.2}:k2=${(params["intensity"] ?: 0.6f) * 0.1}"

            // Glitch Effects
            "glitch_rgb" -> "rgbashift=rh=${(params["offset"] ?: 10f).toInt()}:gh=0:bh=-${(params["offset"] ?: 10f).toInt()}"
            "glitch_digital" -> "noise=alls=${((params["intensity"] ?: 0.6f) * 50).toInt()}:allf=t,rgbashift=rh=5:rv=-5"
            "glitch_scan" -> "eq=saturation=0.9,interlace=scan=1"
            "glitch_noise" -> "noise=alls=${((params["intensity"] ?: 0.5f) * 60).toInt()}:allf=t+u"
            "glitch_shake" -> "crop=in_w-20:in_h-20:10*sin(t*50):10*cos(t*50)"

            // Natural Effects
            "natural_sun" -> "eq=brightness=${(params["intensity"] ?: 0.5f) * 0.2}:saturation=1.1,colortemperature=temperature=6500"
            "natural_fog" -> "gblur=sigma=${(params["density"] ?: 0.4f) * 5},eq=brightness=0.1"
            "natural_rain" -> "noise=alls=20:allf=t"
            "natural_snow" -> "noise=alls=30:allf=t"
            "natural_dust" -> "noise=alls=${((params["density"] ?: 0.3f) * 20).toInt()}:allf=t"

            // Dreamy Effects
            "dreamy_glow" -> "gblur=sigma=${(params["intensity"] ?: 0.5f) * 3},eq=brightness=0.05"
            "dreamy_bloom" -> "gblur=sigma=${(params["intensity"] ?: 0.6f) * 4},eq=brightness=0.1:contrast=1.1"
            "dreamy_haze" -> "eq=brightness=${(params["intensity"] ?: 0.4f) * 0.1}:contrast=0.95,gblur=sigma=1"
            "dreamy_bokeh" -> "boxblur=${((params["size"] ?: 0.5f) * 8).toInt()}"
            "dreamy_fairy" -> "noise=alls=${((params["density"] ?: 0.4f) * 15).toInt()}:allf=t,eq=brightness=0.05"

            // Color Effects
            "color_negative" -> "negate"
            "color_thermal" -> "hue=s=0,eq=contrast=1.5,colormap=preset=viridis"
            "color_xray" -> "negate,eq=contrast=1.3,colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3"
            "color_posterize" -> "posterize=${(params["levels"] ?: 4f).toInt()}"
            "color_solarize" -> "curves=all='0/0 ${params["threshold"] ?: 0.5f}/${1 - (params["threshold"] ?: 0.5f)} 1/1'"

            // Distortion Effects
            "distort_wave" -> "sine"
            "distort_bulge" -> "lenscorrection=k1=${(params["strength"] ?: 0.5f) * 0.3}"
            "distort_pinch" -> "lenscorrection=k1=${-(params["strength"] ?: 0.5f) * 0.3}"
            "distort_swirl" -> "rotate=angle=${(params["angle"] ?: 45f)}*PI/180*sin(t)"
            "distort_stretch" -> "scale=iw*${1 + (params["amount"] ?: 0.5f)}:ih"

            else -> ""
        }
    }

    private fun createGPUImageFilter(effect: EffectItem): GPUImageFilter {
        val params = effect.parameters
        return when (effect.type) {
            EffectType.BLUR -> GPUImageGaussianBlurFilter().apply {
                setBlurSize((params["intensity"] ?: 0.5f) * 2)
            }
            EffectType.GLITCH -> GPUImageFilterGroup(listOf(
                GPUImageContrastFilter().apply { setContrast(1.2f) },
                GPUImageSaturationFilter().apply { setSaturation(1.1f) }
            ))
            EffectType.RETRO -> GPUImageFilterGroup(listOf(
                GPUImageSepiaToneFilter().apply { setIntensity(0.3f) },
                GPUImageVignetteFilter()
            ))
            EffectType.CINEMATIC -> GPUImageFilterGroup(listOf(
                GPUImageContrastFilter().apply { setContrast(1.15f) },
                GPUImageSaturationFilter().apply { setSaturation(0.9f) },
                GPUImageVignetteFilter()
            ))
            EffectType.DREAMY -> GPUImageFilterGroup(listOf(
                GPUImageGaussianBlurFilter().apply { setBlurSize(0.5f) },
                GPUImageBrightnessFilter().apply { setBrightness(0.05f) }
            ))
            EffectType.COLOR -> when (effect.id) {
                "color_negative" -> GPUImageColorInvertFilter()
                "color_posterize" -> GPUImagePosterizeFilter().apply {
                    setColorLevels((params["levels"] ?: 4f).toInt())
                }
                else -> GPUImageFilter()
            }
            EffectType.DISTORTION -> GPUImageSwirlFilter().apply {
                setAngle((params["angle"] ?: 45f) * 0.01f)
            }
            else -> GPUImageFilter()
        }
    }

    private fun executeFFmpegCommand(command: String): Boolean {
        Log.d(TAG, "Executing FFmpeg command: $command")
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }
}
