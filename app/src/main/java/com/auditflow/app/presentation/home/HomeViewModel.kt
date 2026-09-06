package com.auditflow.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auditflow.app.domain.inspection.FileInspectionStation
import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.repository.ProjectIngestionRepository
import com.auditflow.app.domain.repository.ProjectStateRepository
import com.auditflow.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _fileInspections = MutableStateFlow<Map<String, FileInspectionResult>>(emptyMap())
    val fileInspections: StateFlow<Map<String, FileInspectionResult>> = _fileInspections.asStateFlow()

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

    fun ingestLocalProject(treeUriString: String) {
        viewModelScope.launch {
            projectStateRepository.setProjectLoading(
                source = treeUriString,
                progress = 5,
                statusMessage = "Opening local directory..."
            )
            val result = projectIngestionRepository.ingestLocalDirectory(
                treeUriString = treeUriString,
                onProgress = { progress, msg ->
                    viewModelScope.launch {
                        projectStateRepository.setProjectLoading(
                            source = treeUriString,
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

    fun ingestLocalArtifact(fileUriString: String) {
        viewModelScope.launch {
            projectStateRepository.setProjectLoading(
                source = fileUriString,
                progress = 5,
                statusMessage = "Opening local artifact..."
            )
            val result = projectIngestionRepository.ingestLocalFile(
                fileUriString = fileUriString,
                onProgress = { progress, msg ->
                    viewModelScope.launch {
                        projectStateRepository.setProjectLoading(
                            source = fileUriString,
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

    /**
     * Bridges live ingested project files to the source inspection machinery.
     * Retrieves actual content using [ProjectIngestionRepository.readFileContent]
     * and inspects code structure via [FileInspectionStation.inspectFile].
     */
    suspend fun inspectFile(sourceNode: SourceFileNode): Result<FileInspectionResult> {
        if (sourceNode.isDirectory) {
            return Result.failure(IllegalArgumentException("Cannot inspect directory '${sourceNode.relativePath}' as source file"))
        }

        val loadedState = (uiState.value.projectState as? ProjectState.ProjectLoaded)
            ?: return Result.failure(IllegalStateException("No project currently loaded"))

        val contentResult = projectIngestionRepository.readFileContent(
            loadedState.metadata,
            sourceNode.relativePath
        )

        return if (contentResult.isSuccess) {
            val rawText = contentResult.getOrNull()
            val inspection = FileInspectionStation.inspectFile(sourceNode, rawText)
            _fileInspections.update { it + (sourceNode.relativePath to inspection) }
            Result.success(inspection)
        } else {
            val inspection = FileInspectionStation.inspectFile(sourceNode, null)
            _fileInspections.update { it + (sourceNode.relativePath to inspection) }
            Result.failure(
                contentResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to read file content for '${sourceNode.relativePath}'")
            )
        }
    }

    fun onResetStateToEmpty() {
        viewModelScope.launch {
            _fileInspections.value = emptyMap()
            projectStateRepository.setNoProject()
        }
    }
}

