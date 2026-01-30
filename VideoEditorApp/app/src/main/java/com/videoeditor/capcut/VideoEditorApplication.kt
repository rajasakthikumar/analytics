package com.videoeditor.capcut

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.arthenica.ffmpegkit.FFmpegKitConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VideoEditorApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupFFmpeg()
        createNotificationChannels()
    }

    private fun setupFFmpeg() {
        // Configure FFmpeg logging
        FFmpegKitConfig.enableLogCallback { log ->
            android.util.Log.d("FFmpeg", log.message)
        }
        FFmpegKitConfig.enableStatisticsCallback { stats ->
            android.util.Log.d("FFmpeg", "Frame: ${stats.videoFrameNumber}, Time: ${stats.time}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Export progress channel
            val exportChannel = NotificationChannel(
                EXPORT_CHANNEL_ID,
                "Video Export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when exporting videos"
                setShowBadge(false)
            }

            // Render complete channel
            val completeChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "Export Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when video export is complete"
            }

            notificationManager.createNotificationChannels(listOf(exportChannel, completeChannel))
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        lateinit var instance: VideoEditorApplication
            private set

        const val EXPORT_CHANNEL_ID = "export_progress"
        const val COMPLETE_CHANNEL_ID = "export_complete"
    }
}
