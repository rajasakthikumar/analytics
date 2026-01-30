package com.videoeditor.capcut.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoeditor.capcut.data.models.MediaItem
import com.videoeditor.capcut.data.models.MediaType
import com.videoeditor.capcut.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    init {
        loadMedia()
    }

    fun setMediaType(type: MediaType) {
        _uiState.update { it.copy(selectedType = type) }
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val items = when (_uiState.value.selectedType) {
                MediaType.VIDEO -> mediaRepository.getVideos()
                MediaType.IMAGE -> mediaRepository.getImages()
                else -> emptyList()
            }

            _mediaItems.value = items
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleSelection(item: MediaItem) {
        val currentSelection = _uiState.value.selectedItems.toMutableList()
        val itemId = item.id.toString()

        if (currentSelection.contains(itemId)) {
            currentSelection.remove(itemId)
        } else {
            currentSelection.add(itemId)
        }

        _uiState.update { it.copy(selectedItems = currentSelection) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptyList()) }
    }

    fun getSelectedPaths(): List<String> {
        val selectedIds = _uiState.value.selectedItems
        return _mediaItems.value
            .filter { selectedIds.contains(it.id.toString()) }
            .map { it.path }
    }

    fun getSelectedItems(): List<MediaItem> {
        val selectedIds = _uiState.value.selectedItems
        return _mediaItems.value.filter { selectedIds.contains(it.id.toString()) }
    }
}

data class GalleryUiState(
    val isLoading: Boolean = false,
    val selectedType: MediaType = MediaType.VIDEO,
    val selectedItems: List<String> = emptyList(),
    val error: String? = null
)
