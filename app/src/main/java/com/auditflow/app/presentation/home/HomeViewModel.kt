package com.auditflow.app.presentation.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.repository.ProjectIngestionRepository
import com.auditflow.app.domain.repository.ProjectStateRepository
import com.auditflow.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing Home screen presentation state and project ingestion lifecycle.
 *
 * Guaranteed truthful behavior:
 * Initial state is strictly ProjectState.NoProject.
 * No sample repositories, fake metrics, or fabricated scan scores are generated.
 */
class HomeViewModel(
    private val projectStateRepository: ProjectStateRepository,
    private val settingsRepository: SettingsRepository,
    private val projectIngestionRepository: ProjectIngestionRepository
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
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(projectState = ProjectState.NoProject)
    )

    fun ingestLocalProject(treeUri: Uri, context: Context) {
        viewModelScope.launch {
            projectStateRepository.setProjectLoading(
                source = treeUri.toString(),
                progress = 5,
                statusMessage = "Opening local directory..."
            )
            val result = projectIngestionRepository.ingestLocalDirectory(
                treeUri = treeUri,
                context = context,
                onProgress = { progress, msg ->
                    viewModelScope.launch {
                        projectStateRepository.setProjectLoading(
                            source = treeUri.toString(),
                            progress = progress,
                            statusMessage = msg
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { (metadata, files) ->
                    projectStateRepository.setProjectLoaded(metadata, files)
                },
                onFailure = { error ->
                    projectStateRepository.setError(
                        message = error.message ?: "Failed to ingest local project",
                        cause = error
                    )
                }
            )
        }
    }

    fun ingestLocalArtifact(fileUri: Uri, context: Context) {
        viewModelScope.launch {
            projectStateRepository.setProjectLoading(
                source = fileUri.toString(),
                progress = 5,
                statusMessage = "Opening local artifact..."
            )
            val result = projectIngestionRepository.ingestLocalFile(
                fileUri = fileUri,
                context = context,
                onProgress = { progress, msg ->
                    viewModelScope.launch {
                        projectStateRepository.setProjectLoading(
                            source = fileUri.toString(),
                            progress = progress,
                            statusMessage = msg
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { (metadata, files) ->
                    projectStateRepository.setProjectLoaded(metadata, files)
                },
                onFailure = { error ->
                    projectStateRepository.setError(
                        message = error.message ?: "Failed to ingest local artifact",
                        cause = error
                    )
                }
            )
        }
    }

    fun ingestGitHubRepository(repoUrlOrSlug: String, branch: String? = null) {
        viewModelScope.launch {
            projectStateRepository.setProjectLoading(
                source = repoUrlOrSlug,
                progress = 5,
                statusMessage = "Initializing GitHub repository ingestion..."
            )
            val result = projectIngestionRepository.ingestGitHubRepository(
                repoUrlOrSlug = repoUrlOrSlug,
                branch = branch,
                onProgress = { progress, msg ->
                    viewModelScope.launch {
                        projectStateRepository.setProjectLoading(
                            source = repoUrlOrSlug,
                            progress = progress,
                            statusMessage = msg
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { (metadata, files) ->
                    projectStateRepository.setProjectLoaded(metadata, files)
                },
                onFailure = { error ->
                    projectStateRepository.setError(
                        message = error.message ?: "Failed to ingest GitHub repository",
                        cause = error
                    )
                }
            )
        }
    }

    fun onFeatureNotImplemented(featureName: String) {
        _dialogState.value = true to featureName
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

