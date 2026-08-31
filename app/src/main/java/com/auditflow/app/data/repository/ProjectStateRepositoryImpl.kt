package com.auditflow.app.data.repository

import com.auditflow.app.data.local.AuditFlowPreferences
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.repository.ProjectStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real implementation of ProjectStateRepository.
 * Defaults deterministically to ProjectState.NoProject on clean launch.
 */
class ProjectStateRepositoryImpl(
    private val preferences: AuditFlowPreferences
) : ProjectStateRepository {

    private val _projectState = MutableStateFlow<ProjectState>(ProjectState.NoProject)
    override val projectState: StateFlow<ProjectState> = _projectState.asStateFlow()

    init {
        // Initial state is strictly NoProject unless genuine persistence exists
        val persistedKind = preferences.getPersistedStateKind()
        if (persistedKind == AuditFlowPreferences.STATE_KIND_NO_PROJECT) {
            _projectState.value = ProjectState.NoProject
        }
    }

    override suspend fun setNoProject() {
        preferences.setPersistedStateKind(AuditFlowPreferences.STATE_KIND_NO_PROJECT)
        _projectState.value = ProjectState.NoProject
    }

    override suspend fun setProjectLoading(source: String, progress: Int, statusMessage: String) {
        _projectState.value = ProjectState.ProjectLoading(source, progress, statusMessage)
    }

    override suspend fun setProjectLoaded(metadata: ProjectMetadata, files: List<SourceFileNode>) {
        preferences.setPersistedStateKind(AuditFlowPreferences.STATE_KIND_LOADED)
        _projectState.value = ProjectState.ProjectLoaded(metadata, files)
    }

    override suspend fun setError(message: String, cause: Throwable?) {
        _projectState.value = ProjectState.Error(message, cause)
    }
}
