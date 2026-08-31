package com.auditflow.app.domain.model

/**
 * Deterministic representation of the current Project State in AuditFlow.
 *
 * Core rule:
 * EXISTS ≠ CONNECTED ≠ EXECUTED ≠ VALIDATED ≠ VERIFIED ≠ PRODUCES_EXPECTED_RESULT
 *
 * This state machine strictly prevents claiming a project is loaded or audited
 * when no project has actually been ingested.
 */
sealed interface ProjectState {

    /**
     * Initial truthful state of a newly installed or reset application.
     * No project source files, repository connection, or audit artifacts exist.
     */
    data object NoProject : ProjectState

    /**
     * Represents that a genuine project ingest operation is in progress.
     */
    data class ProjectLoading(
        val source: String,
        val progressPercentage: Int = 0,
        val statusMessage: String = ""
    ) : ProjectState

    /**
     * Represents a genuinely ingested project.
     * Note: In Phase 1A & 1B this does NOT contain fake audit results or synthetic files.
     */
    data class ProjectLoaded(
        val metadata: ProjectMetadata,
        val files: List<SourceFileNode> = emptyList()
    ) : ProjectState

    /**
     * Represents a genuine error during project ingest or verification.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ProjectState
}
