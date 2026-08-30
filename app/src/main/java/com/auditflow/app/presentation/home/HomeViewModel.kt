package com.auditflow.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.repository.ProjectStateRepository
import com.auditflow.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing Home screen presentation state and lifecycle.
 *
 * Guaranteed truthful behavior:
 * Initial state is strictly ProjectState.NoProject.
 * No sample repositories, fake metrics, or fabricated scan scores are generated.
 */
class HomeViewModel(
    private val projectStateRepository: ProjectStateRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _dialogState = MutableStateFlow<Pair<Boolean, String>>(false to "")

    val uiState: StateFlow<HomeUiState> = combine(
        projectStateRepository.projectState,
        _dialogState
    ) { state, (isDialogOpen, featureName) ->
        HomeUiState(
            projectState = state,
            isNotImplementedDialogOpen = isDialogOpen,
            pendingFeatureName = featureName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(projectState = ProjectState.NoProject)
    )

    fun onLocalProjectClicked() {
        // In Phase 1A, local project ingestion is not yet implemented
        _dialogState.value = true to "Local Project Ingestion"
    }

    fun onGitHubRepositoryClicked() {
        // In Phase 1A, remote GitHub repository ingestion is not yet implemented
        _dialogState.value = true to "GitHub Repository Ingestion"
    }

    fun onDismissNotImplementedDialog() {
        _dialogState.value = false to ""
    }

    fun onResetStateToEmpty() {
        viewModelScope.launch {
            projectStateRepository.setNoProject()
        }
    }
}
