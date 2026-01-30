package com.videoeditor.capcut.core.export

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.videoeditor.capcut.R
import com.videoeditor.capcut.VideoEditorApplication
import com.videoeditor.capcut.data.models.ExportPhase
import com.videoeditor.capcut.data.models.ExportProgress
import com.videoeditor.capcut.data.models.ExportSettings
import com.videoeditor.capcut.data.models.VideoProject
import com.videoeditor.capcut.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class ExportService : Service() {

    @Inject
    lateinit var exportManager: ExportManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var exportJob: Job? = null

    companion object {
        const val ACTION_START_EXPORT = "com.videoeditor.capcut.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.videoeditor.capcut.CANCEL_EXPORT"
        const val EXTRA_PROJECT_JSON = "project_json"
        const val EXTRA_SETTINGS_JSON = "settings_json"

        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXPORT -> {
                val projectJson = intent.getStringExtra(EXTRA_PROJECT_JSON)
                val settingsJson = intent.getStringExtra(EXTRA_SETTINGS_JSON)

                if (projectJson != null && settingsJson != null) {
                    startExport(projectJson, settingsJson)
                }
            }
            ACTION_CANCEL_EXPORT -> {
                cancelExport()
            }
        }

        return START_NOT_STICKY
    }

    private fun startExport(projectJson: String, settingsJson: String) {
        val json = Json { ignoreUnknownKeys = true }
        val project = json.decodeFromString<VideoProject>(projectJson)
        val settings = json.decodeFromString<ExportSettings>(settingsJson)

        startForeground(NOTIFICATION_ID, createNotification(ExportProgress()))

        exportJob = serviceScope.launch {
            exportManager.exportProject(project, settings) { progress ->
                updateNotification(progress)

                if (progress.currentPhase == ExportPhase.COMPLETED ||
                    progress.currentPhase == ExportPhase.FAILED ||
                    progress.currentPhase == ExportPhase.CANCELLED) {
                    showCompletionNotification(progress)
                    stopSelf()
                }
            }
        }
    }

    private fun cancelExport() {
        exportJob?.cancel()
        serviceScope.launch {
            exportManager.cancelExport()
        }
        stopSelf()
    }

    private fun createNotification(progress: ExportProgress): Notification {
        val cancelIntent = Intent(this, ExportService::class.java).apply {
            action = ACTION_CANCEL_EXPORT
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VideoEditorApplication.EXPORT_CHANNEL_ID)
            .setContentTitle("Exporting Video")
            .setContentText(progress.currentPhase.displayName)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(100, (progress.progress * 100).toInt(), false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateNotification(progress: ExportProgress) {
        val notification = createNotification(progress)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(progress: ExportProgress) {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (progress.currentPhase) {
            ExportPhase.COMPLETED -> "Export Complete"
            ExportPhase.FAILED -> "Export Failed"
            ExportPhase.CANCELLED -> "Export Cancelled"
            else -> "Export"
        }

        val message = when (progress.currentPhase) {
            ExportPhase.COMPLETED -> "Your video has been exported successfully"
            ExportPhase.FAILED -> progress.error ?: "An error occurred during export"
            ExportPhase.CANCELLED -> "Export was cancelled"
            else -> ""
        }

        val notification = NotificationCompat.Builder(this, VideoEditorApplication.COMPLETE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        exportJob?.cancel()
        serviceScope.cancel()
    }
}
