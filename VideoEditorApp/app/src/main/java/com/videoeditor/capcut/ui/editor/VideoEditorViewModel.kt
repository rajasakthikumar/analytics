package com.videoeditor.capcut.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videoeditor.capcut.core.engine.VideoEngine
import com.videoeditor.capcut.core.export.ExportManager
import com.videoeditor.capcut.core.filters.FilterEngine
import com.videoeditor.capcut.data.models.*
import com.videoeditor.capcut.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val videoEngine: VideoEngine,
    private val filterEngine: FilterEngine,
    private val exportManager: ExportManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _project = MutableStateFlow<VideoProject?>(null)
    val project: StateFlow<VideoProject?> = _project.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var playbackJob: Job? = null

    private val undoStack = mutableListOf<VideoProject>()
    private val redoStack = mutableListOf<VideoProject>()

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.update {
                        it.copy(
                            isPlaying = playbackState == Player.STATE_READY && isPlaying,
                            isBuffering = playbackState == Player.STATE_BUFFERING
                        )
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        startPlaybackTracking()
                    } else {
                        stopPlaybackTracking()
                    }
                }
            })
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            projectRepository.getProjectById(projectId)?.let { project ->
                _project.value = project
                loadProjectMedia(project)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun createProjectFromMedia(mediaPaths: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val clips = mediaPaths.mapIndexed { index, path ->
                val videoInfo = videoEngine.loadVideo(path)
                VideoClip(
                    id = UUID.randomUUID().toString(),
                    startTime = if (index == 0) 0L else _project.value?.duration ?: 0L,
                    endTime = (if (index == 0) 0L else _project.value?.duration ?: 0L) + videoInfo.duration,
                    sourceStartTime = 0L,
                    sourceEndTime = videoInfo.duration,
                    filePath = path,
                    fileName = path.substringAfterLast("/"),
                    originalDuration = videoInfo.duration,
                    width = videoInfo.width,
                    height = videoInfo.height,
                    rotation = videoInfo.rotation
                )
            }

            val totalDuration = clips.sumOf { it.duration }

            val newProject = VideoProject(
                id = UUID.randomUUID().toString(),
                name = "New Project",
                tracks = listOf(
                    Track(
                        id = UUID.randomUUID().toString(),
                        type = TrackType.VIDEO,
                        clips = clips
                    )
                ),
                duration = totalDuration
            )

            _project.value = newProject
            loadProjectMedia(newProject)

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadProjectMedia(project: VideoProject) {
        val videoClips = project.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .filterIsInstance<VideoClip>()

        if (videoClips.isNotEmpty()) {
            val mediaItems = videoClips.map { clip ->
                MediaItem.Builder()
                    .setUri(Uri.parse(clip.filePath))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceStartTime)
                            .setEndPositionMs(clip.sourceEndTime)
                            .build()
                    )
                    .build()
            }

            exoPlayer?.apply {
                setMediaItems(mediaItems)
                prepare()
            }
        }

        _uiState.update {
            it.copy(
                duration = project.duration,
                currentPosition = 0L
            )
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _uiState.update { it.copy(currentPosition = position) }
    }

    private fun startPlaybackTracking() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _uiState.update { it.copy(currentPosition = player.currentPosition) }
                }
                delay(50)
            }
        }
    }

    private fun stopPlaybackTracking() {
        playbackJob?.cancel()
    }

    fun selectClip(clipId: String?) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun addClip(clip: Clip) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                if (track.type == TrackType.VIDEO && clip is VideoClip) {
                    track.copy(clips = track.clips + clip)
                } else {
                    track
                }
            }
            val updatedProject = project.copy(
                tracks = updatedTracks,
                duration = calculateDuration(updatedTracks)
            )
            _project.value = updatedProject
            loadProjectMedia(updatedProject)
        }
    }

    fun deleteClip(clipId: String) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                track.copy(clips = track.clips.filter { it.id != clipId })
            }
            val updatedProject = project.copy(
                tracks = updatedTracks,
                duration = calculateDuration(updatedTracks)
            )
            _project.value = updatedProject
            loadProjectMedia(updatedProject)
        }
    }

    fun trimClip(clipId: String, newStart: Long, newEnd: Long) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId && clip is VideoClip) {
                        clip.copy(
                            sourceStartTime = newStart,
                            sourceEndTime = newEnd,
                            endTime = clip.startTime + (newEnd - newStart)
                        )
                    } else {
                        clip
                    }
                })
            }
            val updatedProject = project.copy(
                tracks = updatedTracks,
                duration = calculateDuration(updatedTracks)
            )
            _project.value = updatedProject
            loadProjectMedia(updatedProject)
        }
    }

    fun splitClip(clipId: String, splitPosition: Long) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex >= 0) {
                    val clip = track.clips[clipIndex]
                    if (clip is VideoClip && splitPosition > clip.startTime && splitPosition < clip.endTime) {
                        val relativePosition = splitPosition - clip.startTime + clip.sourceStartTime

                        val firstPart = clip.copy(
                            id = UUID.randomUUID().toString(),
                            sourceEndTime = relativePosition,
                            endTime = splitPosition
                        )

                        val secondPart = clip.copy(
                            id = UUID.randomUUID().toString(),
                            sourceStartTime = relativePosition,
                            startTime = splitPosition
                        )

                        val newClips = track.clips.toMutableList()
                        newClips.removeAt(clipIndex)
                        newClips.add(clipIndex, firstPart)
                        newClips.add(clipIndex + 1, secondPart)

                        track.copy(clips = newClips)
                    } else {
                        track
                    }
                } else {
                    track
                }
            }
            val updatedProject = project.copy(
                tracks = updatedTracks,
                duration = calculateDuration(updatedTracks)
            )
            _project.value = updatedProject
            loadProjectMedia(updatedProject)
        }
    }

    fun applyFilter(clipId: String, filter: VideoFilter) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId && clip is VideoClip) {
                        clip.copy(filters = clip.filters + filter)
                    } else {
                        clip
                    }
                })
            }
            _project.value = project.copy(tracks = updatedTracks)
        }
    }

    fun applyTransition(clipId: String, transition: Transition) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId && clip is VideoClip) {
                        clip.copy(transitions = clip.transitions.copy(exitTransition = transition))
                    } else {
                        clip
                    }
                })
            }
            _project.value = project.copy(tracks = updatedTracks)
        }
    }

    fun addTextOverlay(text: String, style: TextStyle) {
        saveToUndoStack()
        _project.value?.let { project ->
            val currentPosition = _uiState.value.currentPosition
            val textClip = TextClip(
                id = UUID.randomUUID().toString(),
                startTime = currentPosition,
                endTime = currentPosition + 3000L,
                sourceStartTime = 0L,
                sourceEndTime = 3000L,
                text = text,
                textStyle = style
            )

            val textTrack = project.tracks.find { it.type == TrackType.TEXT }
            val updatedTracks = if (textTrack != null) {
                project.tracks.map { track ->
                    if (track.type == TrackType.TEXT) {
                        track.copy(clips = track.clips + textClip)
                    } else {
                        track
                    }
                }
            } else {
                project.tracks + Track(
                    id = UUID.randomUUID().toString(),
                    type = TrackType.TEXT,
                    clips = listOf(textClip)
                )
            }

            _project.value = project.copy(tracks = updatedTracks)
        }
    }

    fun addAudioClip(audioPath: String) {
        saveToUndoStack()
        viewModelScope.launch {
            val duration = 10000L // Get actual duration from audio file

            val audioClip = AudioClip(
                id = UUID.randomUUID().toString(),
                startTime = _uiState.value.currentPosition,
                endTime = _uiState.value.currentPosition + duration,
                sourceStartTime = 0L,
                sourceEndTime = duration,
                filePath = audioPath,
                fileName = audioPath.substringAfterLast("/"),
                originalDuration = duration
            )

            _project.value?.let { project ->
                val audioTrack = project.tracks.find { it.type == TrackType.AUDIO }
                val updatedTracks = if (audioTrack != null) {
                    project.tracks.map { track ->
                        if (track.type == TrackType.AUDIO) {
                            track.copy(clips = track.clips + audioClip)
                        } else {
                            track
                        }
                    }
                } else {
                    project.tracks + Track(
                        id = UUID.randomUUID().toString(),
                        type = TrackType.AUDIO,
                        clips = listOf(audioClip)
                    )
                }

                _project.value = project.copy(tracks = updatedTracks)
            }
        }
    }

    fun setSpeed(clipId: String, speed: Float) {
        saveToUndoStack()
        _project.value?.let { project ->
            val updatedTracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId && clip is VideoClip) {
                        val newDuration = ((clip.sourceEndTime - clip.sourceStartTime) / speed).toLong()
                        clip.copy(
                            speed = speed,
                            endTime = clip.startTime + newDuration
                        )
                    } else {
                        clip
                    }
                })
            }
            val updatedProject = project.copy(
                tracks = updatedTracks,
                duration = calculateDuration(updatedTracks)
            )
            _project.value = updatedProject
            loadProjectMedia(updatedProject)
        }
    }

    private fun saveToUndoStack() {
        _project.value?.let {
            undoStack.add(it)
            redoStack.clear()
            if (undoStack.size > 50) {
                undoStack.removeAt(0)
            }
            updateUndoRedoState()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            _project.value?.let { redoStack.add(it) }
            _project.value = undoStack.removeLast()
            _project.value?.let { loadProjectMedia(it) }
            updateUndoRedoState()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            _project.value?.let { undoStack.add(it) }
            _project.value = redoStack.removeLast()
            _project.value?.let { loadProjectMedia(it) }
            updateUndoRedoState()
        }
    }

    private fun updateUndoRedoState() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun saveProject() {
        viewModelScope.launch {
            _project.value?.let { project ->
                _uiState.update { it.copy(isSaving = true) }
                projectRepository.saveProject(project)
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun exportProject() {
        viewModelScope.launch {
            _project.value?.let { project ->
                _uiState.update { it.copy(isExporting = true, exportProgress = 0f) }

                val settings = ExportSettings(
                    resolution = project.resolution,
                    frameRate = project.frameRate
                )

                exportManager.exportProject(project, settings) { progress ->
                    _uiState.update {
                        it.copy(
                            exportProgress = progress.progress,
                            exportPhase = progress.currentPhase
                        )
                    }
                }.onSuccess { outputPath ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportedPath = outputPath
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            error = error.message
                        )
                    }
                }
            }
        }
    }

    private fun calculateDuration(tracks: List<Track>): Long {
        return tracks.flatMap { it.clips }.maxOfOrNull { it.endTime } ?: 0L
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}

data class EditorUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val selectedClipId: String? = null,
    val selectedPanel: EditorPanel = EditorPanel.NONE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val exportProgress: Float = 0f,
    val exportPhase: ExportPhase = ExportPhase.PREPARING,
    val exportedPath: String? = null,
    val error: String? = null
)

enum class EditorPanel {
    NONE,
    TRIM,
    FILTERS,
    EFFECTS,
    TEXT,
    AUDIO,
    SPEED,
    TRANSITIONS,
    ADJUSTMENTS
}
