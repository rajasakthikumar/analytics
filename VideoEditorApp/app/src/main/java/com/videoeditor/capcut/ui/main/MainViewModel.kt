package com.videoeditor.capcut.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoeditor.capcut.data.models.VideoProject
import com.videoeditor.capcut.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val recentProjects: StateFlow<List<VideoProject>> = projectRepository
        .getRecentProjects(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjects: StateFlow<List<VideoProject>> = projectRepository
        .getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadProjectCount()
    }

    private fun loadProjectCount() {
        viewModelScope.launch {
            val count = projectRepository.getProjectCount()
            _uiState.update { it.copy(projectCount = count) }
        }
    }

    fun setPermissionsGranted(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            loadProjectCount()
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.duplicateProject(projectId)
            loadProjectCount()
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            projectRepository.getProjectById(projectId)?.let { project ->
                projectRepository.saveProject(project.copy(name = newName))
            }
        }
    }

    fun setSelectedTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}

data class MainUiState(
    val hasPermissions: Boolean = false,
    val isLoading: Boolean = false,
    val projectCount: Int = 0,
    val selectedTab: MainTab = MainTab.HOME,
    val error: String? = null
)

enum class MainTab {
    HOME,
    PROJECTS,
    TEMPLATES,
    SETTINGS
}
