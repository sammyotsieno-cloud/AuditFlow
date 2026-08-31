package com.auditflow.app.domain.model

/**
 * Genuine metadata for an ingested source-code project.
 * Contains only verified properties.
 */
data class ProjectMetadata(
    val name: String,
    val pathOrUri: String,
    val sourceKind: ProjectSourceKind,
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val branchOrTag: String? = null,
    val timestampLoadedMillis: Long = System.currentTimeMillis()
)

enum class ProjectSourceKind {
    LOCAL_DIRECTORY,
    GITHUB_REPOSITORY
}
