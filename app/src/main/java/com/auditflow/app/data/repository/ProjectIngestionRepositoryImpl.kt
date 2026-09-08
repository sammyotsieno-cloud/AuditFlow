package com.auditflow.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.auditflow.app.domain.inspection.ApkStructureExtractor
import com.auditflow.app.domain.inspection.ArtifactIdentifier
import com.auditflow.app.domain.inspection.ZipStructureExtractor
import com.auditflow.app.domain.model.AcquiredFileRecord
import com.auditflow.app.domain.model.AcquisitionManifest
import com.auditflow.app.domain.model.AcquisitionStatus
import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.ArtifactIdentity
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.RelativePathHelper
import com.auditflow.app.domain.model.RepositorySnapshot
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.repository.ProjectIngestionRepository
import com.auditflow.app.domain.util.GitHubUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlin.math.min
import kotlin.random.Random

/**
 * Real implementation of ProjectIngestionRepository.
 *
 * Repository acquisition invariant:
 *
 *     Acquire once
 *          ↓
 *     Verify once
 *          ↓
 *     Immutable RepositorySnapshot
 *          ↓
 *     Reuse downstream
 *
 * GitHub repository acquisition is pinned to one immutable commit SHA.
 * Every repository blob is acquired and verified against its Git blob SHA
 * before the snapshot can be marked complete.
 *
 * Local directory and local artifact ingestion behavior is preserved.
 */
class ProjectIngestionRepositoryImpl(
    private val context: Context? = null
) : ProjectIngestionRepository {

    private val activeSnapshots = ConcurrentHashMap<String, RepositorySnapshot>()

    private val appContext: Context
        get() = context
            ?: com.auditflow.app.AuditFlowApplication.instanceOrNull?.applicationContext
            ?: throw IllegalStateException(
                "Android Context required for local SAF operations"
            )

    override suspend fun ingestLocalDirectory(
        treeUriString: String,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
        withContext(Dispatchers.IO) {
            try {
                val localContext = appContext
                val treeUri = Uri.parse(treeUriString)

                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    localContext.contentResolver.takePersistableUriPermission(
                        treeUri,
                        takeFlags
                    )
                } catch (ignored: Exception) {
                    // Best effort for persisted URI permissions.
                }

                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException(
                            "Could not extract root document ID from selected URI: $treeUri"
                        )
                    )

                onProgress(10, "Accessing local directory...")

                val rootName =
                    resolveDocumentDisplayName(localContext, treeUri, rootDocId)
                        ?: "local_project"

                val fileNodes = mutableListOf<SourceFileNode>()

                onProgress(25, "Enumerating directory contents...")

                traverseDirectoryTree(
                    context = localContext,
                    treeUri = treeUri,
                    parentDocId = rootDocId,
                    parentPath = "",
                    outFiles = fileNodes
                )

                if (fileNodes.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "The selected directory contains no accessible files."
                        )
                    )
                }

                onProgress(90, "Sorting and validating file hierarchy...")

                val sortedNodes = fileNodes.sortedBy { it.relativePath }

                val totalSize =
                    sortedNodes
                        .filter { !it.isDirectory }
                        .sumOf { it.sizeBytes }

                val fileCount =
                    sortedNodes.count { !it.isDirectory }

                val metadata = ProjectMetadata(
                    name = rootName,
                    pathOrUri = treeUriString,
                    sourceKind = ProjectSourceKind.LOCAL_DIRECTORY,
                    fileCount = fileCount,
                    totalSizeBytes = totalSize,
                    timestampLoadedMillis = System.currentTimeMillis()
                )

                onProgress(100, "Local project ingestion complete.")

                Result.success(Pair(metadata, sortedNodes))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun ingestLocalFile(
        fileUriString: String,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
        withContext(Dispatchers.IO) {
            try {
                val localContext = appContext
                val fileUri = Uri.parse(fileUriString)

                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    localContext.contentResolver.takePersistableUriPermission(
                        fileUri,
                        takeFlags
                    )
                } catch (ignored: Exception) {
                    // Best effort.
                }

                onProgress(5, "Resolving local file...")

                val fileName =
                    queryFileName(localContext, fileUri) ?: "artifact"

                val fileSize =
                    queryFileSize(localContext, fileUri) ?: 0L

                onProgress(15, "Reading artifact header...")

                val headerBytes = ByteArray(8)

                localContext.contentResolver
                    .openInputStream(fileUri)
                    ?.use { stream ->
                        stream.read(headerBytes)
                    }

                val isZip = ArtifactIdentifier.isZipMagic(headerBytes)

                val isApk =
                    fileName.endsWith(".apk", ignoreCase = true) ||
                        (isZip && fileName.lowercase().endsWith(".apk"))

                if (
                    isApk ||
                    isZip ||
                    fileName.endsWith(".zip", ignoreCase = true)
                ) {
                    onProgress(30, "Analyzing artifact structure...")

                    val inputStream =
                        localContext.contentResolver.openInputStream(fileUri)
                            ?: return@withContext Result.failure(
                                IllegalStateException(
                                    "Unable to open input stream for URI: $fileUri"
                                )
                            )

                    val (metadata, nodes) = inputStream.use { stream ->
                        if (isApk) {
                            onProgress(
                                50,
                                "Extracting Android Package (APK) metadata..."
                            )

                            val apkResult =
                                ApkStructureExtractor.extract(
                                    stream,
                                    fileName
                                )

                            val totalSize =
                                if (fileSize > 0) {
                                    fileSize
                                } else {
                                    apkResult.nodes
                                        .filter { !it.isDirectory }
                                        .sumOf { it.sizeBytes }
                                }

                            val meta = ProjectMetadata(
                                name = fileName,
                                pathOrUri = fileUri.toString(),
                                sourceKind = ProjectSourceKind.LOCAL_FILE,
                                fileCount =
                                    apkResult.nodes.count { !it.isDirectory },
                                totalSizeBytes = totalSize,
                                artifactIdentity = ArtifactIdentity.APK,
                                archiveContentIdentity =
                                    ArchiveContentIdentity.APK,
                                apkMetadata = apkResult.metadata,
                                timestampLoadedMillis =
                                    System.currentTimeMillis()
                            )

                            Pair(meta, apkResult.nodes)
                        } else {
                            onProgress(
                                50,
                                "Extracting ZIP archive entries..."
                            )

                            val zipResult =
                                ZipStructureExtractor.extract(
                                    stream,
                                    fileName
                                )

                            val totalSize =
                                if (fileSize > 0) {
                                    fileSize
                                } else {
                                    zipResult.metadata
                                        .totalUncompressedSizeBytes
                                }

                            val artifactIdent =
                                if (
                                    zipResult.metadata.containsApk ||
                                    zipResult.metadata.detectedContentIdentity ==
                                    ArchiveContentIdentity.APK
                                ) {
                                    ArtifactIdentity.APK
                                } else {
                                    ArtifactIdentity.ZIP_ARCHIVE
                                }

                            val meta = ProjectMetadata(
                                name = fileName,
                                pathOrUri = fileUri.toString(),
                                sourceKind = ProjectSourceKind.LOCAL_FILE,
                                fileCount =
                                    zipResult.nodes.count { !it.isDirectory },
                                totalSizeBytes = totalSize,
                                artifactIdentity = artifactIdent,
                                archiveContentIdentity =
                                    zipResult.metadata.detectedContentIdentity,
                                zipMetadata = zipResult.metadata,
                                timestampLoadedMillis =
                                    System.currentTimeMillis()
                            )

                            Pair(meta, zipResult.nodes)
                        }
                    }

                    if (nodes.isEmpty()) {
                        return@withContext Result.failure(
                            IllegalArgumentException(
                                "The selected artifact contains no accessible entries."
                            )
                        )
                    }

                    onProgress(100, "Artifact ingestion complete.")

                    Result.success(Pair(metadata, nodes))
                } else {
                    val node = SourceFileNode(
                        relativePath = fileName,
                        name = fileName,
                        extension =
                            fileName.substringAfterLast('.', ""),
                        sizeBytes = fileSize,
                        isDirectory = false,
                        isReadable = true,
                        pathClassification =
                            PathClassification.ESTABLISHED
                    )

                    val metadata = ProjectMetadata(
                        name = fileName,
                        pathOrUri = fileUri.toString(),
                        sourceKind = ProjectSourceKind.LOCAL_FILE,
                        fileCount = 1,
                        totalSizeBytes = fileSize,
                        artifactIdentity =
                            ArtifactIdentity.UNKNOWN_ARTIFACT,
                        archiveContentIdentity =
                            ArchiveContentIdentity.UNKNOWN,
                        timestampLoadedMillis =
                            System.currentTimeMillis()
                    )

                    onProgress(100, "Single file ingestion complete.")

                    Result.success(Pair(metadata, listOf(node)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun isBinaryExtension(extension: String): Boolean {
        return when (extension.lowercase()) {
            "png",
            "jpg",
            "jpeg",
            "gif",
            "webp",
            "ico",
            "svg",
            "apk",
            "aab",
            "jar",
            "zip",
            "tar",
            "gz",
            "bz2",
            "7z",
            "rar",
            "dex",
            "so",
            "class",
            "pdf",
            "mp3",
            "mp4",
            "wav",
            "ogg",
            "flac",
            "avi",
            "mov",
            "webm",
            "ttf",
            "otf",
            "woff",
            "woff2",
            "exe",
            "dll",
            "bin",
            "db",
            "sqlite",
            "sqlite3" -> true

            else -> false
        }
    }

    override suspend fun acquireRepositorySnapshot(
        repoUrlOrSlug: String,
        branch: String?,
        onProgress: (Int, String) -> Unit
    ): Result<RepositorySnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val repoRef = GitHubUrlParser.parse(repoUrlOrSlug)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException(
                            "Invalid GitHub repository format. Expected 'owner/repo' or 'https://github.com/owner/repo'."
                        )
                    )

                onProgress(
                    5,
                    "Resolving repository identity for ${repoRef.slug}..."
                )

                val repoApiUrl =
                    "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}"

                val repoJson =
                    fetchJsonFromUrlWithRetry(repoApiUrl)

                val repoName =
                    repoJson.optString("name", repoRef.repo)

                val defaultBranch =
                    repoJson.optString("default_branch", "main")

                val targetBranch =
                    branch
                        ?.takeIf { it.isNotBlank() }
                        ?: repoRef.branch?.takeIf { it.isNotBlank() }
                        ?: defaultBranch

                onProgress(
                    12,
                    "Resolving immutable commit SHA for branch '$targetBranch'..."
                )

                val targetCommitSha =
                    resolveTargetCommitSha(
                        repoRef.owner,
                        repoRef.repo,
                        targetBranch
                    )
                        ?: return@withContext Result.failure(
                            IllegalStateException(
                                "Failed to resolve immutable commit SHA for repository '${repoRef.slug}' on branch '$targetBranch'."
                            )
                        )

                onProgress(
                    20,
                    "Acquiring complete Git tree for commit ${targetCommitSha.take(7)}..."
                )

                /*
                 * We deliberately use a correctness-first tree strategy.
                 *
                 * A recursive Git tree response may be truncated.
                 * When that happens, the previous implementation guessed
                 * completeness by inspecting only root-level directories.
                 *
                 * This implementation instead walks the Git tree recursively
                 * from the immutable commit tree and fails closed if any
                 * subtree cannot be completely enumerated.
                 */
                val treeResult =
                    acquireCompleteGitTree(
                        owner = repoRef.owner,
                        repo = repoRef.repo,
                        rootTreeSha = targetCommitSha,
                        onProgress = { progress, message ->
                            onProgress(progress, message)
                        }
                    )

                if (!treeResult.isComplete) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Repository tree acquisition could not prove completeness."
                        )
                    )
                }

                val rawTreeEntries = treeResult.entries

                onProgress(
                    38,
                    "Building acquisition manifest..."
                )

                val fileNodes = mutableListOf<SourceFileNode>()
                val records = mutableMapOf<String, AcquiredFileRecord>()
                val seenPaths = mutableSetOf<String>()

                var duplicateFilesCount = 0
                var fileCount = 0
                var totalSize = 0L

                for (item in rawTreeEntries) {
                    val rawPath =
                        item.optString("path", "")

                    if (rawPath.isBlank()) {
                        continue
                    }

                    val normalizedPath =
                        RelativePathHelper.normalize(rawPath)

                    if (normalizedPath.isBlank()) {
                        continue
                    }

                    if (!seenPaths.add(normalizedPath)) {
                        duplicateFilesCount++
                        continue
                    }

                    val type =
                        item.optString("type", "")

                    val isDirectory =
                        type == "tree"

                    val sizeBytes =
                        item.optLong("size", 0L)

                    val blobSha =
                        item
                            .optString("sha", "")
                            .takeIf { it.isNotBlank() }

                    val name =
                        normalizedPath.substringAfterLast('/')

                    val extension =
                        if (isDirectory) {
                            ""
                        } else {
                            name.substringAfterLast('.', "")
                        }

                    fileNodes.add(
                        SourceFileNode(
                            relativePath = normalizedPath,
                            name = name,
                            extension = extension,
                            sizeBytes = sizeBytes,
                            isDirectory = isDirectory,
                            isReadable = true,
                            mimeType = null,
                            pathClassification =
                                PathClassification.ESTABLISHED
                        )
                    )

                    if (!isDirectory) {
                        fileCount++
                        totalSize += sizeBytes

                        records[normalizedPath] =
                            AcquiredFileRecord(
                                relativePath = normalizedPath,
                                name = name,
                                extension = extension,
                                sizeBytes = sizeBytes,
                                isDirectory = false,
                                blobSha = blobSha,
                                targetCommitSha = targetCommitSha,
                                verificationStatus =
                                    AcquiredFileRecord.VerificationStatus.FAILED,
                                failureReason =
                                    if (blobSha == null) {
                                        "Repository tree did not provide a blob SHA."
                                    } else {
                                        null
                                    }
                            )
                    }
                }

                /*
                 * Every non-directory repository entry is an expected file.
                 *
                 * Directories are intentionally excluded from expected file
                 * content acquisition because they have no blob content.
                 */
                val expectedFilesCount = records.size

                if (expectedFilesCount != fileCount) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Repository manifest accounting mismatch: expected=$expectedFilesCount, fileCount=$fileCount."
                        )
                    )
                }

                if (duplicateFilesCount > 0) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Repository manifest contains $duplicateFilesCount duplicate normalized paths."
                        )
                    )
                }

                onProgress(
                    42,
                    "Acquiring and cryptographically verifying $expectedFilesCount repository files..."
                )

                var acquiredFilesCount = 0
                var verifiedFilesCount = 0
                var failedFilesCount = 0
                var missingFilesCount = 0

                val orderedRecords =
                    records.values.sortedBy { it.relativePath }

                for ((index, originalRecord) in orderedRecords.withIndex()) {
                    val encodedPath =
                        encodeGitHubPath(originalRecord.relativePath)

                    val rawUrl =
                        "https://raw.githubusercontent.com/" +
                            "${repoRef.owner}/${repoRef.repo}/" +
                            "$targetCommitSha/$encodedPath"

                    val acquisitionResult =
                        acquireAndVerifyFileBytes(
                            rawUrl = rawUrl,
                            expectedBlobSha =
                                originalRecord.blobSha,
                            expectedSizeBytes =
                                originalRecord.sizeBytes
                        )

                    when (acquisitionResult) {
                        is FileAcquisitionResult.Success -> {
                            val bytes =
                                acquisitionResult.bytes

                            val binary =
                                isBinaryExtension(
                                    originalRecord.extension
                                ) ||
                                    containsBinarySignature(bytes)

                            val verifiedSha =
                                computeGitBlobSha(bytes)

                            /*
                             * The result must already have passed the expected
                             * SHA comparison before it reaches this branch.
                             */
                            records[originalRecord.relativePath] =
                                originalRecord.copy(
                                    content =
                                        if (binary) {
                                            null
                                        } else {
                                            decodeText(bytes)
                                        },
                                    contentBytes =
                                        if (binary) {
                                            bytes
                                        } else {
                                            null
                                        },
                                    acquiredSizeBytes =
                                        bytes.size.toLong(),
                                    verifiedBlobSha =
                                        verifiedSha,
                                    isBinary = binary,
                                    verificationStatus =
                                        AcquiredFileRecord.VerificationStatus.VERIFIED,
                                    failureReason = null
                                )

                            acquiredFilesCount++
                            verifiedFilesCount++
                        }

                        is FileAcquisitionResult.Missing -> {
                            records[originalRecord.relativePath] =
                                originalRecord.copy(
                                    acquiredSizeBytes = null,
                                    verifiedBlobSha = null,
                                    verificationStatus =
                                        AcquiredFileRecord.VerificationStatus.MISSING,
                                    failureReason =
                                        acquisitionResult.reason
                                )

                            missingFilesCount++
                        }

                        is FileAcquisitionResult.Failure -> {
                            records[originalRecord.relativePath] =
                                originalRecord.copy(
                                    acquiredSizeBytes =
                                        acquisitionResult.acquiredSizeBytes,
                                    verifiedBlobSha =
                                        acquisitionResult.verifiedBlobSha,
                                    verificationStatus =
                                        AcquiredFileRecord.VerificationStatus.FAILED,
                                    failureReason =
                                        acquisitionResult.reason
                                )

                            failedFilesCount++
                        }
                    }

                    val completed =
                        index + 1

                    val progress =
                        42 +
                            (
                                completed.toDouble() /
                                    expectedFilesCount.coerceAtLeast(1)
                            )
                                .times(43)
                                .toInt()

                    onProgress(
                        progress.coerceAtMost(85),
                        "Verified repository files ($completed/$expectedFilesCount)..."
                    )
                }

                onProgress(
                    88,
                    "Performing final repository snapshot verification..."
                )

                val verifiedRecords =
                    records.values.filter {
                        it.verificationStatus ==
                            AcquiredFileRecord.VerificationStatus.VERIFIED
                    }

                val allRecordsAccountedFor =
                    records.size == expectedFilesCount &&
                        acquiredFilesCount ==
                        expectedFilesCount

                val allFilesVerified =
                    verifiedRecords.size ==
                        expectedFilesCount &&
                        records.values.all {
                            it.verificationStatus ==
                                AcquiredFileRecord.VerificationStatus.VERIFIED
                        }

                val allHashesVerified =
                    records.values.all { record ->
                        !record.blobSha.isNullOrBlank() &&
                            !record.verifiedBlobSha.isNullOrBlank() &&
                            record.blobSha.equals(
                                record.verifiedBlobSha,
                                ignoreCase = true
                            )
                    }

                val allSizesVerified =
                    records.values.all { record ->
                        record.acquiredSizeBytes != null &&
                            record.acquiredSizeBytes ==
                            record.sizeBytes
                    }

                val allVersionsConsistent =
                    targetCommitSha.isNotBlank() &&
                        records.values.all {
                            it.targetCommitSha ==
                                targetCommitSha
                        }

                val isContentComplete =
                    allRecordsAccountedFor &&
                        allFilesVerified &&
                        allHashesVerified &&
                        allSizesVerified &&
                        failedFilesCount == 0 &&
                        missingFilesCount == 0

                val isVersionConsistent =
                    allVersionsConsistent

                val acquisitionStatus =
                    if (
                        treeResult.isComplete &&
                        duplicateFilesCount == 0 &&
                        isContentComplete &&
                        isVersionConsistent
                    ) {
                        AcquisitionStatus.COMPLETE
                    } else {
                        AcquisitionStatus.FAILED
                    }

                val manifest =
                    AcquisitionManifest(
                        targetCommitSha = targetCommitSha,
                        targetBranch = targetBranch,
                        expectedFilesCount =
                            expectedFilesCount,
                        acquiredFilesCount =
                            acquiredFilesCount,
                        verifiedFilesCount =
                            verifiedFilesCount,
                        failedFilesCount =
                            failedFilesCount,
                        missingFilesCount =
                            missingFilesCount,
                        duplicateFilesCount =
                            duplicateFilesCount,
                        isTreeComplete =
                            treeResult.isComplete,
                        isContentComplete =
                            isContentComplete,
                        isVersionConsistent =
                            isVersionConsistent,
                        status =
                            acquisitionStatus,
                        records =
                            records.toMap()
                    )

                /*
                 * Fail closed.
                 *
                 * A RepositorySnapshot is not returned as successful unless
                 * every expected file is present, acquired, size-consistent,
                 * hash-verified, version-consistent, and the tree is proven
                 * complete.
                 */
                if (
                    acquisitionStatus !=
                    AcquisitionStatus.COMPLETE
                ) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            buildString {
                                append(
                                    "Repository snapshot verification failed. "
                                )
                                append(
                                    "expected=$expectedFilesCount, "
                                )
                                append(
                                    "acquired=$acquiredFilesCount, "
                                )
                                append(
                                    "verified=$verifiedFilesCount, "
                                )
                                append(
                                    "failed=$failedFilesCount, "
                                )
                                append(
                                    "missing=$missingFilesCount, "
                                )
                                append(
                                    "duplicates=$duplicateFilesCount, "
                                )
                                append(
                                    "treeComplete=${treeResult.isComplete}, "
                                )
                                append(
                                    "contentComplete=$isContentComplete, "
                                )
                                append(
                                    "versionConsistent=$isVersionConsistent."
                                )
                            }
                        )
                    )
                }

                val metadata =
                    ProjectMetadata(
                        name = repoName,
                        pathOrUri = repoRef.webUrl,
                        sourceKind =
                            ProjectSourceKind.GITHUB_REPOSITORY,
                        fileCount = fileCount,
                        totalSizeBytes = totalSize,
                        branchOrTag = targetBranch,
                        targetCommitSha = targetCommitSha,
                        timestampLoadedMillis =
                            System.currentTimeMillis()
                    )

                val sortedNodes =
                    fileNodes.sortedBy { it.relativePath }

                val immutableRecords =
                    records.toMap()

                val snapshot =
                    RepositorySnapshot(
                        metadata = metadata,
                        owner = repoRef.owner,
                        repo = repoRef.repo,
                        targetBranch = targetBranch,
                        targetCommitSha = targetCommitSha,
                        manifest = manifest,
                        files = sortedNodes,
                        acquiredFiles = immutableRecords,
                        isComplete = true
                    )

                /*
                 * Cache only a fully verified snapshot.
                 *
                 * No partial or failed snapshot enters the downstream cache.
                 */
                activeSnapshots[snapshot.metadata.pathOrUri] =
                    snapshot

                activeSnapshots[snapshot.metadata.name] =
                    snapshot

                onProgress(
                    100,
                    "Repository snapshot acquisition complete."
                )

                Result.success(snapshot)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun ingestGitHubRepository(
        repoUrlOrSlug: String,
        branch: String?,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
        withContext(Dispatchers.IO) {
            val snapshotResult =
                acquireRepositorySnapshot(
                    repoUrlOrSlug = repoUrlOrSlug,
                    branch = branch,
                    onProgress = onProgress
                )

            if (snapshotResult.isSuccess) {
                val snapshot =
                    snapshotResult.getOrThrow()

                Result.success(
                    Pair(
                        snapshot.metadata,
                        snapshot.files
                    )
                )
            } else {
                Result.failure(
                    snapshotResult.exceptionOrNull()
                        ?: IllegalStateException(
                            "Snapshot acquisition failed"
                        )
                )
            }
        }

    private fun resolveTargetCommitSha(
        owner: String,
        repo: String,
        targetBranch: String
    ): String? {
        val encodedBranch =
            URLEncoder.encode(
                targetBranch,
                "UTF-8"
            )

        val commitUrl =
            "https://api.github.com/repos/$owner/$repo/commits/$encodedBranch"

        return try {
            val commitJson =
                fetchJsonFromUrlWithRetry(commitUrl)

            commitJson
                .optString("sha", "")
                .takeIf {
                    it.matches(
                        Regex("^[0-9a-fA-F]{40}$")
                    )
                }
        } catch (firstFailure: Exception) {
            val branchUrl =
                "https://api.github.com/repos/$owner/$repo/branches/$encodedBranch"

            try {
                val branchJson =
                    fetchJsonFromUrlWithRetry(branchUrl)

                val sha =
                    branchJson
                        .optJSONObject("commit")
                        ?.optString("sha", "")

                sha?.takeIf {
                    it.matches(
                        Regex("^[0-9a-fA-F]{40}$")
                    )
                }
            } catch (secondFailure: Exception) {
                null
            }
        }
    }

    private data class TreeAcquisitionResult(
        val entries: List<JSONObject>,
        val isComplete: Boolean
    )

    /**
     * Correctness-first Git tree acquisition.
     *
     * The root commit SHA identifies the repository tree.
     *
     * If a recursive response is truncated, the implementation does not guess.
     * It falls back to a non-recursive directory walk and recursively enumerates
     * every subtree.
     */
    private fun acquireCompleteGitTree(
        owner: String,
        repo: String,
        rootTreeSha: String,
        onProgress: (Int, String) -> Unit
    ): TreeAcquisitionResult {
        val entries =
            mutableListOf<JSONObject>()

        val seenPaths =
            mutableSetOf<String>()

        val visitedTreeShas =
            mutableSetOf<String>()

        val rootRecursiveUrl =
            "https://api.github.com/repos/$owner/$repo/git/trees/$rootTreeSha?recursive=1"

        return try {
            val rootJson =
                fetchJsonFromUrlWithRetry(rootRecursiveUrl)

            val rootArray =
                rootJson.optJSONArray("tree")

            if (rootArray == null) {
                return TreeAcquisitionResult(
                    entries = emptyList(),
                    isComplete = false
                )
            }

            val truncated =
                rootJson.optBoolean(
                    "truncated",
                    false
                )

            if (!truncated) {
                for (i in 0 until rootArray.length()) {
                    val item =
                        rootArray.getJSONObject(i)

                    val path =
                        item.optString("path", "")

                    if (
                        path.isNotBlank() &&
                        seenPaths.add(path)
                    ) {
                        entries.add(item)
                    }
                }

                return TreeAcquisitionResult(
                    entries = entries,
                    isComplete = true
                )
            }

            onProgress(
                24,
                "Recursive Git tree response was truncated; switching to complete subtree enumeration..."
            )

            /*
             * The recursive response cannot be trusted as complete.
             * Start again from the root tree using non-recursive traversal.
             */
            val complete =
                enumerateGitTreeRecursively(
                    owner = owner,
                    repo = repo,
                    treeSha = rootTreeSha,
                    pathPrefix = "",
                    output = entries,
                    seenPaths = seenPaths,
                    visitedTreeShas = visitedTreeShas
                )

            TreeAcquisitionResult(
                entries = entries,
                isComplete = complete
            )
        } catch (e: Exception) {
            TreeAcquisitionResult(
                entries = entries,
                isComplete = false
            )
        }
    }

    private fun enumerateGitTreeRecursively(
        owner: String,
        repo: String,
        treeSha: String,
        pathPrefix: String,
        output: MutableList<JSONObject>,
        seenPaths: MutableSet<String>,
        visitedTreeShas: MutableSet<String>
    ): Boolean {
        if (!visitedTreeShas.add(treeSha)) {
            /*
             * A repeated tree SHA is not an incomplete tree by itself.
             * Git trees can legitimately be reused by content identity.
             */
            return true
        }

        val treeUrl =
            "https://api.github.com/repos/$owner/$repo/git/trees/$treeSha"

        val treeJson =
            try {
                fetchJsonFromUrlWithRetry(treeUrl)
            } catch (e: Exception) {
                return false
            }

        /*
         * This endpoint is non-recursive, so a successful response enumerates
         * the immediate children of this exact tree.
         */
        if (
            treeJson.optBoolean(
                "truncated",
                false
            )
        ) {
            return false
        }

        val treeArray =
            treeJson.optJSONArray("tree")
                ?: return false

        for (i in 0 until treeArray.length()) {
            val item =
                treeArray.getJSONObject(i)

            val localPath =
                item.optString("path", "")

            if (localPath.isBlank()) {
                return false
            }

            val fullPath =
                if (pathPrefix.isBlank()) {
                    localPath
                } else {
                    "$pathPrefix/$localPath"
                }

            val type =
                item.optString("type", "")

            val normalizedPath =
                RelativePathHelper.normalize(fullPath)

            if (normalizedPath.isBlank()) {
                return false
            }

            val copy =
                JSONObject(item.toString())

            copy.put(
                "path",
                normalizedPath
            )

            if (!seenPaths.add(normalizedPath)) {
                /*
                 * Duplicate path is deliberately preserved as a duplicate
                 * condition for manifest accounting by returning false here.
                 */
                return false
            }

            output.add(copy)

            if (type == "tree") {
                val childSha =
                    item.optString("sha", "")

                if (
                    childSha.isBlank() ||
                    !enumerateGitTreeRecursively(
                        owner = owner,
                        repo = repo,
                        treeSha = childSha,
                        pathPrefix = normalizedPath,
                        output = output,
                        seenPaths = seenPaths,
                        visitedTreeShas = visitedTreeShas
                    )
                ) {
                    return false
                }
            }
        }

        return true
    }

    private fun acquireAndVerifyFileBytes(
        rawUrl: String,
        expectedBlobSha: String?,
        expectedSizeBytes: Long
    ): FileAcquisitionResult {
        if (expectedBlobSha.isNullOrBlank()) {
            return FileAcquisitionResult.Failure(
                reason = "Missing expected Git blob SHA.",
                acquiredSizeBytes = null,
                verifiedBlobSha = null
            )
        }

        return try {
            val bytes =
                downloadBytesWithRetry(
                    rawUrl
                )

            val actualSize =
                bytes.size.toLong()

            if (actualSize != expectedSizeBytes) {
                return FileAcquisitionResult.Failure(
                    reason =
                        "Acquired byte count mismatch: expected=$expectedSizeBytes, actual=$actualSize.",
                    acquiredSizeBytes = actualSize,
                    verifiedBlobSha =
                        computeGitBlobSha(bytes)
                )
            }

            val actualBlobSha =
                computeGitBlobSha(bytes)

            if (
                !actualBlobSha.equals(
                    expectedBlobSha,
                    ignoreCase = true
                )
            ) {
                return FileAcquisitionResult.Failure(
                    reason =
                        "Git blob SHA mismatch: expected=$expectedBlobSha, actual=$actualBlobSha.",
                    acquiredSizeBytes = actualSize,
                    verifiedBlobSha = actualBlobSha
                )
            }

            FileAcquisitionResult.Success(
                bytes = bytes
            )
        } catch (e: FileMissingException) {
            FileAcquisitionResult.Missing(
                reason = e.message
                    ?: "File not found on remote."
            )
        } catch (e: Exception) {
            FileAcquisitionResult.Failure(
                reason = e.message
                    ?: "File acquisition failed.",
                acquiredSizeBytes = null,
                verifiedBlobSha = null
            )
        }
    }

    private fun downloadBytesWithRetry(
        urlString: String
    ): ByteArray {
        val maxAttempts = 4
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            var connection: HttpURLConnection? = null

            try {
                val url =
                    URL(urlString)

                connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty(
                    "User-Agent",
                    "AuditFlow-Android"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/octet-stream"
                )

                val responseCode =
                    connection.responseCode

                when {
                    responseCode ==
                        HttpURLConnection.HTTP_OK -> {
                        return connection.inputStream
                            .use { it.readBytes() }
                    }

                    responseCode ==
                        HttpURLConnection.HTTP_NOT_FOUND -> {
                        throw FileMissingException(
                            "File not found on remote (HTTP 404)."
                        )
                    }

                    responseCode ==
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        throw IllegalStateException(
                            "GitHub rejected file acquisition (HTTP 401)."
                        )
                    }

                    responseCode ==
                        HttpURLConnection.HTTP_FORBIDDEN -> {
                        val retryAfter =
                            parseRetryAfterMillis(
                                connection
                            )

                        val rateLimited =
                            connection.getHeaderField(
                                "X-RateLimit-Remaining"
                            ) == "0"

                        if (
                            rateLimited ||
                            retryAfter != null
                        ) {
                            lastException =
                                IllegalStateException(
                                    "GitHub rate limit encountered (HTTP 403)."
                                )

                            if (attempt < maxAttempts) {
                                delay(
                                    retryAfter
                                        ?: calculateBackoffMillis(
                                            attempt
                                        )
                                )
                                continue
                            }
                        }

                        throw IllegalStateException(
                            "GitHub access forbidden (HTTP 403)."
                        )
                    }

                    responseCode == 429 ||
                        responseCode in 500..599 -> {
                        lastException =
                            IllegalStateException(
                                "Transient GitHub HTTP $responseCode."
                            )

                        if (attempt < maxAttempts) {
                            delay(
                                parseRetryAfterMillis(
                                    connection
                                )
                                    ?: calculateBackoffMillis(
                                        attempt
                                    )
                            )
                            continue
                        }

                        throw lastException
                            ?: IllegalStateException(
                                "Transient GitHub request failed."
                            )
                    }

                    else -> {
                        throw IllegalStateException(
                            "GitHub raw content returned HTTP $responseCode: ${connection.responseMessage}"
                        )
                    }
                }
            } catch (e: FileMissingException) {
                throw e
            } catch (e: Exception) {
                lastException = e

                if (attempt >= maxAttempts) {
                    throw e
                }

                delay(
                    calculateBackoffMillis(
                        attempt
                    )
                )
            } finally {
                connection?.disconnect()
            }
        }

        throw lastException
            ?: IllegalStateException(
                "GitHub file acquisition failed."
            )
    }

    private fun fetchJsonFromUrlWithRetry(
        urlString: String
    ): JSONObject {
        val maxAttempts = 4
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            var connection: HttpURLConnection? = null

            try {
                val url =
                    URL(urlString)

                connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty(
                    "User-Agent",
                    "AuditFlow-Android"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )

                val responseCode =
                    connection.responseCode

                when {
                    responseCode ==
                        HttpURLConnection.HTTP_OK -> {
                        val content =
                            BufferedReader(
                                InputStreamReader(
                                    connection.inputStream
                                )
                            ).use {
                                it.readText()
                            }

                        return JSONObject(content)
                    }

                    responseCode ==
                        HttpURLConnection.HTTP_NOT_FOUND -> {
                        throw IllegalArgumentException(
                            "GitHub resource not found (HTTP 404)."
                        )
                    }

                    responseCode ==
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        throw IllegalStateException(
                            "GitHub authentication rejected (HTTP 401)."
                        )
                    }

                    responseCode ==
                        HttpURLConnection.HTTP_FORBIDDEN -> {
                        val retryAfter =
                            parseRetryAfterMillis(
                                connection
                            )

                        val rateLimited =
                            connection.getHeaderField(
                                "X-RateLimit-Remaining"
                            ) == "0"

                        if (
                            (rateLimited ||
                                retryAfter != null) &&
                            attempt < maxAttempts
                        ) {
                            lastException =
                                IllegalStateException(
                                    "GitHub API rate limit encountered (HTTP 403)."
                                )

                            delay(
                                retryAfter
                                    ?: calculateBackoffMillis(
                                        attempt
                                    )
                            )

                            continue
                        }

                        throw IllegalStateException(
                            "GitHub API access forbidden or rate limited (HTTP 403)."
                        )
                    }

                    responseCode == 429 ||
                        responseCode in 500..599 -> {
                        lastException =
                            IllegalStateException(
                                "Transient GitHub API HTTP $responseCode."
                            )

                        if (attempt < maxAttempts) {
                            delay(
                                parseRetryAfterMillis(
                                    connection
                                )
                                    ?: calculateBackoffMillis(
                                        attempt
                                    )
                            )

                            continue
                        }

                        throw lastException
                            ?: IllegalStateException(
                                "GitHub API request failed."
                            )
                    }

                    else -> {
                        throw IllegalStateException(
                            "GitHub API returned HTTP $responseCode: ${connection.responseMessage}"
                        )
                    }
                }
            } catch (e: Exception) {
                lastException = e

                if (
                    e is IllegalArgumentException &&
                    e.message?.contains(
                        "404"
                    ) == true
                ) {
                    throw e
                }

                if (attempt >= maxAttempts) {
                    throw e
                }

                delay(
                    calculateBackoffMillis(
                        attempt
                    )
                )
            } finally {
                connection?.disconnect()
            }
        }

        throw lastException
            ?: IllegalStateException(
                "GitHub API request failed."
            )
    }

    private fun calculateBackoffMillis(
        attempt: Int
    ): Long {
        val base =
            500L * (1L shl (attempt - 1).coerceAtMost(5))

        val jitter =
            Random.nextLong(
                0L,
                500L
            )

        return min(
            base + jitter,
            15000L
        )
    }

    private fun parseRetryAfterMillis(
        connection: HttpURLConnection
    ): Long? {
        val value =
            connection.getHeaderField(
                "Retry-After"
            )
                ?: return null

        return value.toLongOrNull()
            ?.times(1000L)
    }

    private fun encodeGitHubPath(
        path: String
    ): String {
        return path
            .split("/")
            .joinToString("/") { segment ->
                URLEncoder
                    .encode(
                        segment,
                        "UTF-8"
                    )
                    .replace(
                        "+",
                        "%20"
                    )
            }
    }

    private fun computeGitBlobSha(
        bytes: ByteArray
    ): String {
        val header =
            "blob ${bytes.size}\u0000"
                .toByteArray(Charsets.UTF_8)

        val payload =
            ByteArrayOutputStream(
                header.size + bytes.size
            )

        payload.write(header)
        payload.write(bytes)

        val digest =
            MessageDigest.getInstance("SHA-1")
                .digest(payload.toByteArray())

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun containsBinarySignature(
        bytes: ByteArray
    ): Boolean {
        if (bytes.isEmpty()) {
            return false
        }

        /*
         * NUL is a strong practical indicator that the file is not ordinary
         * UTF-8 source/text. Extension-based classification remains the primary
         * classifier for known binary formats.
         */
        return bytes.any {
            it == 0.toByte()
        }
    }

    private fun decodeText(
        bytes: ByteArray
    ): String {
        return bytes.toString(
            Charsets.UTF_8
        )
    }

    private sealed class FileAcquisitionResult {
        data class Success(
            val bytes: ByteArray
        ) : FileAcquisitionResult()

        data class Missing(
            val reason: String
        ) : FileAcquisitionResult()

        data class Failure(
            val reason: String,
            val acquiredSizeBytes: Long?,
            val verifiedBlobSha: String?
        ) : FileAcquisitionResult()
    }

    private class FileMissingException(
        message: String
    ) : Exception(message)

    private fun resolveDocumentDisplayName(
        context: Context,
        treeUri: Uri,
        docId: String
    ): String? {
        val docUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                docId
            )

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )

        return try {
            context.contentResolver
                .query(
                    docUri,
                    projection,
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun traverseDirectoryTree(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        parentPath: String,
        outFiles: MutableList<SourceFileNode>
    ) {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocId
            )

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

        try {
            context.contentResolver
                .query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    val idIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID
                        )

                    val nameIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        )

                    val mimeIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        )

                    val sizeIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_SIZE
                        )

                    while (cursor.moveToNext()) {
                        val docId =
                            cursor.getString(idIdx)

                        val name =
                            cursor.getString(nameIdx)
                                ?: continue

                        val mime =
                            cursor.getString(mimeIdx)

                        val size =
                            if (cursor.isNull(sizeIdx)) {
                                0L
                            } else {
                                cursor.getLong(sizeIdx)
                            }

                        val isDir =
                            mime ==
                                DocumentsContract.Document.MIME_TYPE_DIR

                        val rawRelativePath =
                            if (parentPath.isEmpty()) {
                                name
                            } else {
                                "$parentPath/$name"
                            }

                        val normalizedRelativePath =
                            RelativePathHelper.normalize(
                                rawRelativePath
                            )

                        val extension =
                            if (isDir) {
                                ""
                            } else {
                                name.substringAfterLast(
                                    '.',
                                    ""
                                )
                            }

                        outFiles.add(
                            SourceFileNode(
                                relativePath =
                                    normalizedRelativePath,
                                name = name,
                                extension = extension,
                                sizeBytes = size,
                                isDirectory = isDir,
                                isReadable = true,
                                mimeType = mime,
                                pathClassification =
                                    PathClassification.ESTABLISHED
                            )
                        )

                        if (isDir) {
                            traverseDirectoryTree(
                                context = context,
                                treeUri = treeUri,
                                parentDocId = docId,
                                parentPath =
                                    normalizedRelativePath,
                                outFiles = outFiles
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            /*
             * Preserve existing local-ingestion behavior.
             * A local SAF subtree access failure does not fabricate entries.
             */
        }
    }

    override suspend fun readFileContent(
        projectMetadata: ProjectMetadata,
        relativePath: String
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedPath =
                    RelativePathHelper.normalize(
                        relativePath
                    )

                when (projectMetadata.sourceKind) {
                    ProjectSourceKind.GITHUB_REPOSITORY -> {
                        /*
                         * A GitHub RepositorySnapshot is authoritative.
                         *
                         * If a snapshot exists, downstream reads MUST NOT
                         * silently refetch from GitHub.
                         */
                        val cachedSnapshot =
                            activeSnapshots[
                                projectMetadata.pathOrUri
                            ]
                                ?: activeSnapshots[
                                    projectMetadata.name
                                ]

                        if (cachedSnapshot != null) {
                            val record =
                                cachedSnapshot.acquiredFiles[
                                    normalizedPath
                                ]

                            if (record == null) {
                                return@withContext Result.failure(
                                    IllegalStateException(
                                        "File '$normalizedPath' is not present in the authoritative repository snapshot."
                                    )
                                )
                            }

                            if (
                                record.verificationStatus !=
                                AcquiredFileRecord.VerificationStatus.VERIFIED
                            ) {
                                return@withContext Result.failure(
                                    IllegalStateException(
                                        "File '$normalizedPath' is not verified in the authoritative repository snapshot."
                                    )
                                )
                            }

                            if (record.isBinary) {
                                return@withContext Result.failure(
                                    IllegalStateException(
                                        "File '$normalizedPath' is binary and cannot be returned as text."
                                    )
                                )
                            }

                            return@withContext Result.success(
                                record.content ?: ""
                            )
                        }

                        /*
                         * No authoritative snapshot is available.
                         *
                         * For a GitHub project, do not silently create a second
                         * acquisition path. The caller must acquire the snapshot
                         * first.
                         */
                        Result.failure(
                            IllegalStateException(
                                "No authoritative GitHub repository snapshot is available for '${projectMetadata.name}'. Acquire the repository snapshot before reading file content."
                            )
                        )
                    }

                    ProjectSourceKind.LOCAL_DIRECTORY -> {
                        val localContext = appContext

                        val treeUri =
                            Uri.parse(
                                projectMetadata.pathOrUri
                            )

                        val rootDocId =
                            DocumentsContract.getTreeDocumentId(
                                treeUri
                            )
                                ?: return@withContext Result.failure(
                                    IllegalArgumentException(
                                        "Cannot resolve root document ID from '${projectMetadata.pathOrUri}'"
                                    )
                                )

                        val content =
                            readLocalSafFile(
                                context = localContext,
                                treeUri = treeUri,
                                parentDocId = rootDocId,
                                targetRelativePath =
                                    normalizedPath
                            )

                        if (content != null) {
                            Result.success(content)
                        } else {
                            Result.failure(
                                IllegalStateException(
                                    "Cannot open or locate local file '$normalizedPath' in SAF hierarchy"
                                )
                            )
                        }
                    }

                    ProjectSourceKind.LOCAL_FILE -> {
                        val localContext = appContext

                        val fileUri =
                            Uri.parse(
                                projectMetadata.pathOrUri
                            )

                        val inputStream =
                            localContext.contentResolver
                                .openInputStream(fileUri)
                                ?: return@withContext Result.failure(
                                    IllegalStateException(
                                        "Cannot open input stream for '$fileUri'"
                                    )
                                )

                        val content =
                            inputStream.use { stream ->
                                readArchiveEntryContent(
                                    stream,
                                    normalizedPath
                                )
                            }

                        if (content != null) {
                            Result.success(content)
                        } else {
                            Result.failure(
                                IllegalStateException(
                                    "Cannot read or locate entry '$normalizedPath' in artifact"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readArchiveEntryContent(
        stream: InputStream,
        targetPath: String
    ): String? {
        return try {
            val zipIn =
                ZipInputStream(stream)

            var entry =
                zipIn.nextEntry

            while (entry != null) {
                val normalized =
                    RelativePathHelper.normalize(
                        entry.name
                    )

                if (
                    normalized.equals(
                        targetPath,
                        ignoreCase = true
                    )
                ) {
                    val bytes =
                        zipIn.readBytes()

                    return if (
                        bytes.any {
                            it == 0.toByte()
                        }
                    ) {
                        "[Binary Artifact Entry: ${bytes.size} bytes]"
                    } else {
                        String(
                            bytes,
                            Charsets.UTF_8
                        )
                    }
                }

                zipIn.closeEntry()
                entry =
                    zipIn.nextEntry
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun queryFileName(
        context: Context,
        uri: Uri
    ): String? {
        val projection =
            arrayOf(
                OpenableColumns.DISPLAY_NAME
            )

        return try {
            context.contentResolver
                .query(
                    uri,
                    projection,
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
                ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun queryFileSize(
        context: Context,
        uri: Uri
    ): Long? {
        val projection =
            arrayOf(
                OpenableColumns.SIZE
            )

        return try {
            context.contentResolver
                .query(
                    uri,
                    projection,
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (cursor.isNull(0)) {
                            null
                        } else {
                            cursor.getLong(0)
                        }
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun readLocalSafFile(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        targetRelativePath: String
    ): String? {
        val segments =
            RelativePathHelper.extractSegments(
                targetRelativePath
            )

        if (segments.isEmpty()) {
            return null
        }

        var currentDocId =
            parentDocId

        for (i in segments.indices) {
            val segment =
                segments[i]

            val isLeaf =
                i == segments.lastIndex

            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    currentDocId
                )

            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

            var foundNextDocId: String? =
                null

            context.contentResolver
                .query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    val idIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID
                        )

                    val nameIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        )

                    val mimeIdx =
                        cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        )

                    while (cursor.moveToNext()) {
                        val name =
                            cursor.getString(nameIdx)

                        if (
                            name.equals(
                                segment,
                                ignoreCase = true
                            )
                        ) {
                            foundNextDocId =
                                cursor.getString(idIdx)

                            val isDir =
                                cursor.getString(mimeIdx) ==
                                    DocumentsContract.Document.MIME_TYPE_DIR

                            if (
                                isLeaf &&
                                !isDir
                            ) {
                                val docUri =
                                    DocumentsContract.buildDocumentUriUsingTree(
                                        treeUri,
                                        foundNextDocId
                                    )

                                return context.contentResolver
                                    .openInputStream(
                                        docUri
                                    )
                                    ?.bufferedReader()
                                    ?.use {
                                        it.readText()
                                    }
                            }

                            break
                        }
                    }
                }

            if (foundNextDocId == null) {
                return null
            }

            currentDocId =
                foundNextDocId
        }

        return null
    }
}
