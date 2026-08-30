package com.auditflow.app.presentation.home

import com.auditflow.app.domain.model.ProjectState

/**
 * UI State for the AuditFlow Home screen.
 */
data class HomeUiState(
    val projectState: ProjectState = ProjectState.NoProject,
    val isNotImplementedDialogOpen: Boolean = false,
    val pendingFeatureName: String = "",
    val activePrincipleHover: String? = null
)
