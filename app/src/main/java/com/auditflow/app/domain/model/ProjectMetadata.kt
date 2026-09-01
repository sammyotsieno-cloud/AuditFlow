package com.auditflow.app.domain.model

/**
 * Genuine metadata for an ingested source-code project or software artifact.
 * Contains only verified properties.
 */
data class ProjectMetadata(
    val name: String,
    val pathOrUri: String,
    val sourceKind: ProjectSourceKind,
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val branchOrTag: String? = null,
    val timestampLoadedMillis: Long = System.currentTimeMillis(),
    val artifactIdentity: ArtifactIdentity = when (sourceKind) {
        ProjectSourceKind.GITHUB_REPOSITORY -> ArtifactIdentity.REPOSITORY
        ProjectSourceKind.LOCAL_DIRECTORY -> ArtifactIdentity.DIRECTORY_PROJECT
        ProjectSourceKind.LOCAL_FILE -> ArtifactIdentity.UNKNOWN_ARTIFACT
    },
    val archiveContentIdentity: ArchiveContentIdentity? = null,
    val apkMetadata: ApkPackageMetadata? = null,
    val zipMetadata: ZipArchiveMetadata? = null
)

enum class ProjectSourceKind {
    LOCAL_DIRECTORY,
    GITHUB_REPOSITORY,
    LOCAL_FILE
}

