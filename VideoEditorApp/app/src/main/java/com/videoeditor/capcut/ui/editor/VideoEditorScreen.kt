package com.videoeditor.capcut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.videoeditor.capcut.data.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    project: VideoProject?,
    uiState: EditorUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAddClip: (Clip) -> Unit,
    onDeleteClip: (String) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit,
    onSplitClip: (String, Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onApplyFilter: (String, VideoFilter) -> Unit,
    onApplyTransition: (String, Transition) -> Unit,
    onAddText: (String, TextStyle) -> Unit,
    onAddAudio: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    var selectedPanel by remember { mutableStateOf(EditorPanel.NONE) }

    Scaffold(
        topBar = {
            EditorTopBar(
                projectName = project?.name ?: "New Project",
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBack = onBack,
                onUndo = onUndo,
                onRedo = onRedo,
                onSave = onSave,
                onExport = onExport
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1A1A1A))
        ) {
            // Video Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                VideoPreview(
                    modifier = Modifier.fillMaxSize()
                )

                // Playback controls overlay
                PlaybackControls(
                    isPlaying = uiState.isPlaying,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }

            // Timeline
            TimelineView(
                project = project,
                currentPosition = uiState.currentPosition,
                selectedClipId = uiState.selectedClipId,
                onSeek = onSeek,
                onSelectClip = onSelectClip,
                onSplitClip = { clipId -> onSplitClip(clipId, uiState.currentPosition) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            // Tool Panels
            if (selectedPanel != EditorPanel.NONE) {
                ToolPanel(
                    panel = selectedPanel,
                    selectedClipId = uiState.selectedClipId,
                    onApplyFilter = onApplyFilter,
                    onApplyTransition = onApplyTransition,
                    onAddText = onAddText,
                    onClose = { selectedPanel = EditorPanel.NONE },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }

            // Bottom Tools
            EditorToolbar(
                selectedPanel = selectedPanel,
                onSelectPanel = { selectedPanel = if (selectedPanel == it) EditorPanel.NONE else it },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Export Progress Dialog
    if (uiState.isExporting) {
        ExportProgressDialog(
            progress = uiState.exportProgress,
            phase = uiState.exportPhase
        )
    }

    // Loading
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                projectName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Outlined.Save, contentDescription = "Save")
            }
            Button(
                onClick = onExport,
                modifier = Modifier.padding(end = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 14.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF2A2A2A)
        )
    )
}

@Composable
private fun VideoPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        // Progress slider
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
            onValueChange = { onSeek((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.Gray
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatDuration(currentPosition),
                color = Color.White,
                fontSize = 12.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSeek(maxOf(0, currentPosition - 5000)) }) {
                    Icon(
                        Icons.Filled.Replay5,
                        contentDescription = "Rewind 5s",
                        tint = Color.White
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = { onSeek(minOf(duration, currentPosition + 5000)) }) {
                    Icon(
                        Icons.Filled.Forward5,
                        contentDescription = "Forward 5s",
                        tint = Color.White
                    )
                }
            }

            Text(
                formatDuration(duration),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TimelineView(
    project: VideoProject?,
    currentPosition: Long,
    selectedClipId: String?,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onSplitClip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF252525))
            .padding(8.dp)
    ) {
        // Time ruler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xFF333333)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val duration = project?.duration ?: 0L
            val markers = (duration / 1000).toInt()
            for (i in 0..markers step 5) {
                Text(
                    "${i}s",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Video track
        project?.tracks?.filter { it.type == TrackType.VIDEO }?.forEach { track ->
            TrackRow(
                track = track,
                selectedClipId = selectedClipId,
                duration = project.duration,
                onSelectClip = onSelectClip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Audio track
        project?.tracks?.filter { it.type == TrackType.AUDIO }?.forEach { track ->
            TrackRow(
                track = track,
                selectedClipId = selectedClipId,
                duration = project.duration,
                onSelectClip = onSelectClip,
                isAudio = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }

        // Playhead indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        ) {
            val progress = if ((project?.duration ?: 0L) > 0) {
                currentPosition.toFloat() / project!!.duration
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .offset(x = (progress * 300).dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    selectedClipId: String?,
    duration: Long,
    onSelectClip: (String?) -> Unit,
    isAudio: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF333333))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start
        ) {
            track.clips.forEach { clip ->
                val widthFraction = if (duration > 0) {
                    clip.duration.toFloat() / duration
                } else 0f

                val startOffset = if (duration > 0) {
                    clip.startTime.toFloat() / duration
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(widthFraction)
                        .offset(x = (startOffset * 300).dp)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (clip.id == selectedClipId) {
                                MaterialTheme.colorScheme.primary
                            } else if (isAudio) {
                                Color(0xFF4CAF50)
                            } else {
                                Color(0xFF6366F1)
                            }
                        )
                        .clickable { onSelectClip(clip.id) }
                        .padding(4.dp)
                ) {
                    when (clip) {
                        is VideoClip -> {
                            Text(
                                clip.fileName,
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is AudioClip -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    clip.fileName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        is TextClip -> {
                            Text(
                                clip.text,
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    selectedPanel: EditorPanel,
    onSelectPanel: (EditorPanel) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF2A2A2A)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            val tools = listOf(
                ToolItem(EditorPanel.TRIM, Icons.Outlined.ContentCut, "Trim"),
                ToolItem(EditorPanel.FILTERS, Icons.Outlined.FilterVintage, "Filters"),
                ToolItem(EditorPanel.EFFECTS, Icons.Outlined.AutoFixHigh, "Effects"),
                ToolItem(EditorPanel.TEXT, Icons.Outlined.TextFields, "Text"),
                ToolItem(EditorPanel.AUDIO, Icons.Outlined.MusicNote, "Audio"),
                ToolItem(EditorPanel.SPEED, Icons.Outlined.Speed, "Speed"),
                ToolItem(EditorPanel.TRANSITIONS, Icons.Outlined.SwapHoriz, "Transitions"),
                ToolItem(EditorPanel.ADJUSTMENTS, Icons.Outlined.Tune, "Adjust")
            )

            items(tools) { tool ->
                ToolButton(
                    icon = tool.icon,
                    label = tool.label,
                    isSelected = selectedPanel == tool.panel,
                    onClick = { onSelectPanel(tool.panel) }
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ToolPanel(
    panel: EditorPanel,
    selectedClipId: String?,
    onApplyFilter: (String, VideoFilter) -> Unit,
    onApplyTransition: (String, Transition) -> Unit,
    onAddText: (String, TextStyle) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF2A2A2A)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Panel header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    panel.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Panel content
            when (panel) {
                EditorPanel.FILTERS -> FiltersPanel(
                    onSelectFilter = { filter ->
                        selectedClipId?.let { onApplyFilter(it, filter) }
                    }
                )
                EditorPanel.TRANSITIONS -> TransitionsPanel(
                    onSelectTransition = { transition ->
                        selectedClipId?.let { onApplyTransition(it, transition) }
                    }
                )
                EditorPanel.TEXT -> TextPanel(
                    onAddText = onAddText
                )
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${panel.name} panel", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersPanel(
    onSelectFilter: (VideoFilter) -> Unit
) {
    val filters = FilterType.entries.take(12)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filters) { filterType ->
            FilterItem(
                filterType = filterType,
                onClick = { onSelectFilter(VideoFilter(type = filterType)) }
            )
        }
    }
}

@Composable
private fun FilterItem(
    filterType: FilterType,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF444444))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            filterType.displayName,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TransitionsPanel(
    onSelectTransition: (Transition) -> Unit
) {
    val transitions = TransitionType.entries.take(12)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(transitions) { transitionType ->
            TransitionItem(
                transitionType = transitionType,
                onClick = { onSelectTransition(Transition(type = transitionType)) }
            )
        }
    }
}

@Composable
private fun TransitionItem(
    transitionType: TransitionType,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF444444)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            transitionType.displayName,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TextPanel(
    onAddText: (String, TextStyle) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter text...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (textInput.isNotEmpty()) {
                    onAddText(textInput, TextStyle())
                    textInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Text")
        }
    }
}

@Composable
private fun ExportProgressDialog(
    progress: Float,
    phase: ExportPhase
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Exporting Video") },
        text = {
            Column {
                Text(phase.displayName)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { }
    )
}

private data class ToolItem(
    val panel: EditorPanel,
    val icon: ImageVector,
    val label: String
)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
