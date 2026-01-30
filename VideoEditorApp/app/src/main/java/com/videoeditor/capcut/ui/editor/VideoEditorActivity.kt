package com.videoeditor.capcut.ui.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.videoeditor.capcut.ui.theme.VideoEditorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoEditorActivity : ComponentActivity() {

    private val viewModel: VideoEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load project or create new one
        val projectId = intent.getStringExtra("project_id")
        val mediaItems = intent.getStringArrayListExtra("media_items")

        if (projectId != null) {
            viewModel.loadProject(projectId)
        } else if (!mediaItems.isNullOrEmpty()) {
            viewModel.createProjectFromMedia(mediaItems)
        }

        setContent {
            VideoEditorTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val project by viewModel.project.collectAsState()

                    VideoEditorScreen(
                        project = project,
                        uiState = uiState,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onAddClip = { viewModel.addClip(it) },
                        onDeleteClip = { viewModel.deleteClip(it) },
                        onTrimClip = { clipId, start, end -> viewModel.trimClip(clipId, start, end) },
                        onSplitClip = { clipId, position -> viewModel.splitClip(clipId, position) },
                        onSelectClip = { viewModel.selectClip(it) },
                        onApplyFilter = { clipId, filter -> viewModel.applyFilter(clipId, filter) },
                        onApplyTransition = { clipId, transition -> viewModel.applyTransition(clipId, transition) },
                        onAddText = { text, style -> viewModel.addTextOverlay(text, style) },
                        onAddAudio = { viewModel.addAudioClip(it) },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onSave = { viewModel.saveProject() },
                        onExport = { viewModel.exportProject() },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.pausePlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releasePlayer()
    }
}
