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
    val targetCommitSha: String? = null,
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

/**
 * Complete Repository Snapshot Acquisition Models.
 */
enum class AcquisitionStatus {
    NOT_STARTED,
    RESOLVING_VERSION,
    ACQUIRING_TREE,
    ACQUIRING_CONTENT,
    VERIFYING,
    COMPLETE,
    FAILED
}

data class AcquiredFileRecord(
    val relativePath: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val blobSha: String? = null,
    val targetCommitSha: String,
    val content: String? = null,
    val isBinary: Boolean = false,
    val verificationStatus: VerificationStatus = VerificationStatus.VERIFIED,
    val failureReason: String? = null
) {
    enum class VerificationStatus {
        VERIFIED,
        FAILED,
        MISSING
    }
}

data class AcquisitionManifest(
    val targetCommitSha: String,
    val targetBranch: String,
    val expectedFilesCount: Int,
    val acquiredFilesCount: Int,
    val verifiedFilesCount: Int,
    val failedFilesCount: Int,
    val missingFilesCount: Int,
    val duplicateFilesCount: Int,
    val isTreeComplete: Boolean,
    val isContentComplete: Boolean,
    val isVersionConsistent: Boolean,
    val status: AcquisitionStatus,
    val records: Map<String, AcquiredFileRecord>
)

data class RepositorySnapshot(
    val metadata: ProjectMetadata,
    val owner: String,
    val repo: String,
    val targetBranch: String,
    val targetCommitSha: String,
    val manifest: AcquisitionManifest,
    val files: List<SourceFileNode>,
    val acquiredFiles: Map<String, AcquiredFileRecord>,
    val isComplete: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis()
)

