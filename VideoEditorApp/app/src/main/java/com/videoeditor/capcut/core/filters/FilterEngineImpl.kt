package com.videoeditor.capcut.core.filters

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videoeditor.capcut.data.models.FilterType
import com.videoeditor.capcut.data.models.VideoAdjustments
import com.videoeditor.capcut.data.models.VideoFilter
import com.videoeditor.capcut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FilterEngine {

    companion object {
        private const val TAG = "FilterEngine"
    }

    private val gpuImage: GPUImage by lazy { GPUImage(context) }

    override suspend fun applyFilter(bitmap: Bitmap, filter: VideoFilter): Bitmap = withContext(ioDispatcher) {
        gpuImage.setImage(bitmap)
        gpuImage.setFilter(createGPUImageFilter(filter))
        gpuImage.bitmapWithFilterApplied
    }

    override suspend fun applyAdjustments(bitmap: Bitmap, adjustments: VideoAdjustments): Bitmap = withContext(ioDispatcher) {
        gpuImage.setImage(bitmap)
        gpuImage.setFilter(createAdjustmentsFilter(adjustments))
        gpuImage.bitmapWithFilterApplied
    }

    override suspend fun applyFilterToVideo(
        inputPath: String,
        outputPath: String,
        filter: VideoFilter
    ): Boolean = withContext(ioDispatcher) {
        val filterString = getFFmpegFilterString(filter)
        if (filterString.isEmpty()) return@withContext false

        val command = "-y -i \"$inputPath\" -vf \"$filterString\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override suspend fun applyAdjustmentsToVideo(
        inputPath: String,
        outputPath: String,
        adjustments: VideoAdjustments
    ): Boolean = withContext(ioDispatcher) {
        val filterString = getFFmpegAdjustmentsString(adjustments)
        if (filterString.isEmpty()) return@withContext false

        val command = "-y -i \"$inputPath\" -vf \"$filterString\" -c:a copy \"$outputPath\""
        executeFFmpegCommand(command)
    }

    override fun getFilterPreview(filter: FilterType): Bitmap? {
        return null // Would need a preview image to generate
    }

    override fun getAvailableFilters(): List<FilterType> {
        return FilterType.entries.toList()
    }

    override fun getFFmpegFilterString(filter: VideoFilter): String {
        val intensity = filter.intensity
        return when (filter.type) {
            FilterType.NONE -> ""
            FilterType.GRAYSCALE -> "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3"
            FilterType.SEPIA -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
            FilterType.VINTAGE -> "curves=vintage"
            FilterType.WARM -> "colortemperature=temperature=6500"
            FilterType.COOL -> "colortemperature=temperature=8500"
            FilterType.VIVID -> "eq=saturation=1.5:contrast=1.1"
            FilterType.DRAMATIC -> "eq=contrast=1.3:brightness=-0.05:saturation=0.9"
            FilterType.CLARENDON -> "eq=contrast=1.2:brightness=0.1:saturation=1.35"
            FilterType.GINGHAM -> "colorbalance=rs=0.1:gs=0.1:bs=0.1:rm=-0.1:gm=-0.1:bm=-0.1,eq=saturation=0.85"
            FilterType.MOON -> "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3,eq=brightness=0.1:contrast=1.1"
            FilterType.LARK -> "colorbalance=rs=0.1:gs=0.05:bs=-0.1,eq=contrast=1.1:saturation=0.9"
            FilterType.REYES -> "eq=brightness=0.1:contrast=0.9:saturation=0.75,colortemperature=temperature=5500"
            FilterType.JUNO -> "eq=saturation=1.3:contrast=1.1,colorbalance=rs=0.1:gs=0.05:bs=-0.05"
            FilterType.SLUMBER -> "eq=saturation=0.7:brightness=0.05,colorbalance=rs=-0.1:gs=-0.05:bs=0.1"
            FilterType.CREMA -> "eq=saturation=0.8:contrast=0.95,colortemperature=temperature=5000"
            FilterType.LUDWIG -> "eq=saturation=0.85:contrast=1.05,colorbalance=rs=-0.05:gs=0:bs=0.05"
            FilterType.ADEN -> "eq=saturation=0.8:contrast=0.9:brightness=0.05,colortemperature=temperature=5500"
            FilterType.PERPETUA -> "eq=saturation=1.1,colorbalance=rs=-0.05:gs=0.1:bs=0.1"
            FilterType.KODAK -> "curves=r='0/0 0.1/0.13 0.5/0.5 0.9/0.87 1/1':g='0/0 0.1/0.1 0.5/0.5 0.9/0.9 1/1':b='0/0 0.1/0.07 0.5/0.5 0.9/0.93 1/1'"
            FilterType.FUJI -> "colorbalance=rs=0.05:gs=0.1:bs=0.05,eq=saturation=0.95"
            FilterType.POLAROID -> "colortemperature=temperature=5500,eq=saturation=0.9:contrast=1.1,vignette"
            FilterType.CINEMATIC -> "eq=contrast=1.2:saturation=0.85,colorbalance=rs=-0.05:gs=0.05:bs=0.1"
            FilterType.NOIR -> "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3,eq=contrast=1.3"
            FilterType.GLITCH -> "rgbashift=rh=-10:gh=5:bh=10:rv=5:gv=-5:bv=-5"
            FilterType.VHS -> "noise=alls=20:allf=t+u,eq=saturation=0.9:contrast=1.1"
            FilterType.RETRO -> "colortemperature=temperature=4500,eq=saturation=0.8:contrast=0.95"
            FilterType.NEON -> "eq=saturation=2:contrast=1.3:brightness=0.1"
            FilterType.COMIC -> "edgedetect=low=0.1:high=0.4,negate"
            FilterType.SKETCH -> "edgedetect=low=0.1:high=0.3"
            FilterType.OIL_PAINT -> "unsharp=5:5:1.5:5:5:0"
            FilterType.WATERCOLOR -> "gblur=sigma=2,unsharp=5:5:1.5"
        }
    }

    override fun getFFmpegAdjustmentsString(adjustments: VideoAdjustments): String {
        val filters = mutableListOf<String>()

        // EQ filter for basic adjustments
        val eqParams = mutableListOf<String>()
        if (adjustments.brightness != 0f) {
            eqParams.add("brightness=${adjustments.brightness}")
        }
        if (adjustments.contrast != 1f) {
            eqParams.add("contrast=${adjustments.contrast}")
        }
        if (adjustments.saturation != 1f) {
            eqParams.add("saturation=${adjustments.saturation}")
        }
        if (eqParams.isNotEmpty()) {
            filters.add("eq=${eqParams.joinToString(":")}")
        }

        // Color temperature
        if (adjustments.temperature != 0f) {
            val temp = 6500 + (adjustments.temperature * 2000)
            filters.add("colortemperature=temperature=${temp.toInt()}")
        }

        // Vignette
        if (adjustments.vignette > 0f) {
            filters.add("vignette=PI/${4 - adjustments.vignette * 2}")
        }

        // Sharpness
        if (adjustments.sharpness != 0f) {
            val amount = 1 + adjustments.sharpness
            filters.add("unsharp=5:5:$amount:5:5:0")
        }

        // Grain/Noise
        if (adjustments.grain > 0f) {
            val noiseAmount = (adjustments.grain * 30).toInt()
            filters.add("noise=alls=$noiseAmount:allf=t")
        }

        // Exposure (simulated with brightness curve)
        if (adjustments.exposure != 0f) {
            val exp = 1 + adjustments.exposure * 0.5f
            filters.add("curves=all='0/0 0.5/${0.5 * exp} 1/1'")
        }

        return filters.joinToString(",")
    }

    private fun createGPUImageFilter(filter: VideoFilter): GPUImageFilter {
        val intensity = filter.intensity
        return when (filter.type) {
            FilterType.NONE -> GPUImageFilter()
            FilterType.GRAYSCALE -> GPUImageGrayscaleFilter()
            FilterType.SEPIA -> GPUImageSepiaToneFilter().apply { setIntensity(intensity) }
            FilterType.VINTAGE -> GPUImageFilterGroup(listOf(
                GPUImageSepiaToneFilter().apply { setIntensity(0.5f * intensity) },
                GPUImageVignetteFilter()
            ))
            FilterType.WARM -> GPUImageWhiteBalanceFilter().apply {
                setTemperature(6500f + 1000f * intensity)
            }
            FilterType.COOL -> GPUImageWhiteBalanceFilter().apply {
                setTemperature(6500f - 1500f * intensity)
            }
            FilterType.VIVID -> GPUImageSaturationFilter().apply { setSaturation(1f + 0.5f * intensity) }
            FilterType.DRAMATIC -> GPUImageFilterGroup(listOf(
                GPUImageContrastFilter().apply { setContrast(1f + 0.3f * intensity) },
                GPUImageSaturationFilter().apply { setSaturation(1f - 0.1f * intensity) }
            ))
            else -> createAdvancedFilter(filter)
        }
    }

    private fun createAdvancedFilter(filter: VideoFilter): GPUImageFilter {
        val intensity = filter.intensity
        return when (filter.type) {
            FilterType.CLARENDON -> GPUImageFilterGroup(listOf(
                GPUImageContrastFilter().apply { setContrast(1.2f * intensity) },
                GPUImageSaturationFilter().apply { setSaturation(1.35f * intensity) },
                GPUImageBrightnessFilter().apply { setBrightness(0.1f * intensity) }
            ))
            FilterType.NOIR -> GPUImageFilterGroup(listOf(
                GPUImageGrayscaleFilter(),
                GPUImageContrastFilter().apply { setContrast(1.3f * intensity) }
            ))
            FilterType.CINEMATIC -> GPUImageFilterGroup(listOf(
                GPUImageContrastFilter().apply { setContrast(1.2f * intensity) },
                GPUImageSaturationFilter().apply { setSaturation(0.85f) },
                GPUImageVignetteFilter()
            ))
            FilterType.POLAROID -> GPUImageFilterGroup(listOf(
                GPUImageSaturationFilter().apply { setSaturation(0.9f) },
                GPUImageContrastFilter().apply { setContrast(1.1f) },
                GPUImageVignetteFilter()
            ))
            else -> GPUImageFilter()
        }
    }

    private fun createAdjustmentsFilter(adjustments: VideoAdjustments): GPUImageFilter {
        val filters = mutableListOf<GPUImageFilter>()

        if (adjustments.brightness != 0f) {
            filters.add(GPUImageBrightnessFilter().apply {
                setBrightness(adjustments.brightness)
            })
        }

        if (adjustments.contrast != 1f) {
            filters.add(GPUImageContrastFilter().apply {
                setContrast(adjustments.contrast)
            })
        }

        if (adjustments.saturation != 1f) {
            filters.add(GPUImageSaturationFilter().apply {
                setSaturation(adjustments.saturation)
            })
        }

        if (adjustments.exposure != 0f) {
            filters.add(GPUImageExposureFilter().apply {
                setExposure(adjustments.exposure)
            })
        }

        if (adjustments.sharpness != 0f) {
            filters.add(GPUImageSharpenFilter().apply {
                setSharpness(adjustments.sharpness)
            })
        }

        if (adjustments.vignette > 0f) {
            filters.add(GPUImageVignetteFilter().apply {
                setVignetteStart(0.3f)
                setVignetteEnd(0.75f - adjustments.vignette * 0.25f)
            })
        }

        if (adjustments.temperature != 0f) {
            filters.add(GPUImageWhiteBalanceFilter().apply {
                setTemperature(6500f + adjustments.temperature * 2000f)
            })
        }

        return if (filters.isEmpty()) GPUImageFilter() else GPUImageFilterGroup(filters)
    }

    private fun executeFFmpegCommand(command: String): Boolean {
        Log.d(TAG, "Executing FFmpeg command: $command")
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }
}
