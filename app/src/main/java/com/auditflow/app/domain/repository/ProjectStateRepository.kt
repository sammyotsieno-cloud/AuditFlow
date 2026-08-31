package com.auditflow.app.domain.repository

import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.model.SourceFileNode
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for observing and modifying the active project state.
 */
interface ProjectStateRepository {
    val projectState: StateFlow<ProjectState>

    suspend fun setNoProject()
    suspend fun setProjectLoading(source: String, progress: Int = 0, statusMessage: String = "")
    suspend fun setProjectLoaded(metadata: ProjectMetadata, files: List<SourceFileNode> = emptyList())
    suspend fun setError(message: String, cause: Throwable? = null)
}
