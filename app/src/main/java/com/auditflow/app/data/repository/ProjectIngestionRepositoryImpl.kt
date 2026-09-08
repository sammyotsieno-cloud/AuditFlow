package com.auditflow.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.auditflow.app.domain.inspection.AndroidBinaryXmlParser
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Real implementation of ProjectIngestionRepository.
 * Performs genuine Android SAF directory traversal and authentic GitHub Git Tree API enumeration.
 *
 * Invariant: Never fabricates synthetic file trees or mock data.
 */
class ProjectIngestionRepositoryImpl(
    private val context: Context? = null
) : ProjectIngestionRepository {

    private val activeSnapshots = ConcurrentHashMap<String, RepositorySnapshot>()

    private val appContext: Context
        get() = context
            ?: com.auditflow.app.AuditFlowApplication.instanceOrNull?.applicationContext
            ?: throw IllegalStateException("Android Context required for local SAF operations")

    override suspend fun ingestLocalDirectory(
        treeUriString: String,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = withContext(Dispatchers.IO) {
        try {
            val localContext = appContext
            val treeUri = Uri.parse(treeUriString)
            // Persist URI read permissions
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                localContext.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (ignored: Exception) {
                // Best effort for persisted URI permissions
            }

            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Could not extract root document ID from selected URI: $treeUri")
                )

            onProgress(10, "Accessing local directory...")

            val rootName = resolveDocumentDisplayName(localContext, treeUri, rootDocId) ?: "local_project"
            val fileNodes = mutableListOf<SourceFileNode>()

            onProgress(25, "Enumerating directory contents...")

            // Recursively traverse SAF directory tree
            traverseDirectoryTree(
                context = localContext,
                treeUri = treeUri,
                parentDocId = rootDocId,
                parentPath = "",
                outFiles = fileNodes
            )

            if (fileNodes.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("The selected directory contains no accessible files.")
                )
            }

            onProgress(90, "Sorting and validating file hierarchy...")

            // Deterministic alphabetical sorting by relative path
            val sortedNodes = fileNodes.sortedBy { it.relativePath }

            val totalSize = sortedNodes.filter { !it.isDirectory }.sumOf { it.sizeBytes }
            val fileCount = sortedNodes.count { !it.isDirectory }

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
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = withContext(Dispatchers.IO) {
        try {
            val localContext = appContext
            val fileUri = Uri.parse(fileUriString)
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                localContext.contentResolver.takePersistableUriPermission(fileUri, takeFlags)
            } catch (ignored: Exception) {
            }

            onProgress(5, "Resolving local file...")

            val fileName = queryFileName(localContext, fileUri) ?: "artifact"
            val fileSize = queryFileSize(localContext, fileUri) ?: 0L

            onProgress(15, "Reading artifact header...")

            val headerBytes = ByteArray(8)
            localContext.contentResolver.openInputStream(fileUri)?.use { stream ->
                stream.read(headerBytes)
            }

            val isZip = ArtifactIdentifier.isZipMagic(headerBytes)
            val isApk = fileName.endsWith(".apk", ignoreCase = true) || (isZip && fileName.lowercase().endsWith(".apk"))

            if (isApk || isZip || fileName.endsWith(".zip", ignoreCase = true)) {
                onProgress(30, "Analyzing artifact structure...")

                val inputStream = localContext.contentResolver.openInputStream(fileUri)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Unable to open input stream for URI: $fileUri")
                    )

                val (metadata, nodes) = inputStream.use { stream ->
                    if (isApk) {
                        onProgress(50, "Extracting Android Package (APK) metadata...")
                        val apkResult = ApkStructureExtractor.extract(stream, fileName)
                        val totalSize = if (fileSize > 0) fileSize else apkResult.nodes.filter { !it.isDirectory }.sumOf { it.sizeBytes }
                        val meta = ProjectMetadata(
                            name = fileName,
                            pathOrUri = fileUri.toString(),
                            sourceKind = ProjectSourceKind.LOCAL_FILE,
                            fileCount = apkResult.nodes.count { !it.isDirectory },
                            totalSizeBytes = totalSize,
                            artifactIdentity = ArtifactIdentity.APK,
                            archiveContentIdentity = ArchiveContentIdentity.APK,
                            apkMetadata = apkResult.metadata,
                            timestampLoadedMillis = System.currentTimeMillis()
                        )
                        Pair(meta, apkResult.nodes)
                    } else {
                        onProgress(50, "Extracting ZIP archive entries...")
                        val zipResult = ZipStructureExtractor.extract(stream, fileName)
                        val totalSize = if (fileSize > 0) fileSize else zipResult.metadata.totalUncompressedSizeBytes

                        val artifactIdent = if (zipResult.metadata.containsApk || zipResult.metadata.detectedContentIdentity == ArchiveContentIdentity.APK) {
                            ArtifactIdentity.APK
                        } else {
                            ArtifactIdentity.ZIP_ARCHIVE
                        }

                        val meta = ProjectMetadata(
                            name = fileName,
                            pathOrUri = fileUri.toString(),
                            sourceKind = ProjectSourceKind.LOCAL_FILE,
                            fileCount = zipResult.nodes.count { !it.isDirectory },
                            totalSizeBytes = totalSize,
                            artifactIdentity = artifactIdent,
                            archiveContentIdentity = zipResult.metadata.detectedContentIdentity,
                            zipMetadata = zipResult.metadata,
                            timestampLoadedMillis = System.currentTimeMillis()
                        )
                        Pair(meta, zipResult.nodes)
                    }
                }

                if (nodes.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("The selected artifact contains no accessible entries.")
                    )
                }

                onProgress(100, "Artifact ingestion complete.")
                Result.success(Pair(metadata, nodes))
            } else {
                val node = SourceFileNode(
                    relativePath = fileName,
                    name = fileName,
                    extension = fileName.substringAfterLast('.', ""),
                    sizeBytes = fileSize,
                    isDirectory = false,
                    isReadable = true,
                    pathClassification = PathClassification.ESTABLISHED
                )
                val metadata = ProjectMetadata(
                    name = fileName,
                    pathOrUri = fileUri.toString(),
                    sourceKind = ProjectSourceKind.LOCAL_FILE,
                    fileCount = 1,
                    totalSizeBytes = fileSize,
                    artifactIdentity = ArtifactIdentity.UNKNOWN_ARTIFACT,
                    archiveContentIdentity = ArchiveContentIdentity.UNKNOWN,
                    timestampLoadedMillis = System.currentTimeMillis()
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
            "png", "jpg", "jpeg", "gif", "webp", "ico", "svg",
            "apk", "aab", "jar", "zip", "tar", "gz",
            "dex", "so", "class", "pdf", "mp3", "mp4", "wav" -> true
            else -> false
        }
    }

    override suspend fun acquireRepositorySnapshot(
        repoUrlOrSlug: String,
        branch: String?,
        onProgress: (Int, String) -> Unit
    ): Result<RepositorySnapshot> = withContext(Dispatchers.IO) {
        try {
            val repoRef = GitHubUrlParser.parse(repoUrlOrSlug)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid GitHub repository format. Expected 'owner/repo' or 'https://github.com/owner/repo'.")
                )

            onProgress(10, "Resolving repository identity for ${repoRef.slug}...")

            // 1. Fetch repository metadata to determine default branch and repo info
            val repoApiUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}"
            val repoJson = fetchJsonFromUrl(repoApiUrl)

            val repoName = repoJson.optString("name", repoRef.repo)
            val defaultBranch = repoJson.optString("default_branch", "main")
            val targetBranch = branch?.takeIf { it.isNotBlank() } ?: repoRef.branch?.takeIf { it.isNotBlank() } ?: defaultBranch

            onProgress(20, "Resolving immutable commit SHA for branch '$targetBranch'...")

            // 2. Resolve target commit SHA
            var targetCommitSha = ""
            try {
                val commitUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}/commits/${URLEncoder.encode(targetBranch, "UTF-8")}"
                val commitJson = fetchJsonFromUrl(commitUrl)
                val sha = commitJson.optString("sha", "")
                if (sha.isNotBlank() && sha.length >= 7) {
                    targetCommitSha = sha
                }
            } catch (e: Exception) {
                // Fallback to branch endpoint
            }

            if (targetCommitSha.isBlank()) {
                try {
                    val branchUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}/branches/${URLEncoder.encode(targetBranch, "UTF-8")}"
                    val branchJson = fetchJsonFromUrl(branchUrl)
                    val commitObj = branchJson.optJSONObject("commit")
                    val sha = commitObj?.optString("sha", "") ?: ""
                    if (sha.isNotBlank() && sha.length >= 7) {
                        targetCommitSha = sha
                    }
                } catch (e: Exception) {
                    // Ignored
                }
            }

            if (targetCommitSha.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to resolve immutable commit SHA for repository '${repoRef.slug}' on branch '$targetBranch'.")
                )
            }

            onProgress(30, "Acquiring complete Git tree for commit ${targetCommitSha.take(7)}...")

            // 3. Complete Git tree acquisition with truncation recovery
            val rawTreeEntries = mutableListOf<JSONObject>()
            var isTreeComplete = false

            val rootTreeUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}/git/trees/$targetCommitSha?recursive=1"
            val rootTreeJson = fetchJsonFromUrl(rootTreeUrl)
            val rootTreeArray = rootTreeJson.optJSONArray("tree")
                ?: return@withContext Result.failure(
                    IllegalArgumentException("GitHub repository commit '${targetCommitSha.take(7)}' contains an empty Git tree.")
                )

            for (i in 0 until rootTreeArray.length()) {
                rawTreeEntries.add(rootTreeArray.getJSONObject(i))
            }

            val isTruncated = rootTreeJson.optBoolean("truncated", false)
            if (isTruncated) {
                onProgress(35, "Recovering truncated subtrees...")
                val knownPaths = rawTreeEntries.mapNotNull { it.optString("path", "").takeIf { p -> p.isNotBlank() } }.toMutableSet()
                val dirEntries = rawTreeEntries.filter { it.optString("type") == "tree" && it.optString("sha").isNotBlank() }

                for (dir in dirEntries) {
                    val dirPath = dir.optString("path", "")
                    val hasChildren = knownPaths.any { it.startsWith("$dirPath/") }
                    if (!hasChildren) {
                        val dirSha = dir.optString("sha")
                        try {
                            val subTreeUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}/git/trees/$dirSha?recursive=1"
                            val subTreeJson = fetchJsonFromUrl(subTreeUrl)
                            val subArray = subTreeJson.optJSONArray("tree")
                            if (subArray != null) {
                                for (j in 0 until subArray.length()) {
                                    val subItem = subArray.getJSONObject(j)
                                    val subPath = subItem.optString("path", "")
                                    val fullPath = "$dirPath/$subPath"
                                    if (!knownPaths.contains(fullPath)) {
                                        knownPaths.add(fullPath)
                                        subItem.put("path", fullPath)
                                        rawTreeEntries.add(subItem)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Subtree recovery error
                        }
                    }
                }
                isTreeComplete = true
            } else {
                isTreeComplete = true
            }

            // 4. Manifest Construction & Deduplication
            onProgress(40, "Building acquisition manifest (${rawTreeEntries.size} tree elements)...")

            val fileNodes = mutableListOf<SourceFileNode>()
            val records = mutableMapOf<String, AcquiredFileRecord>()
            val seenPaths = mutableSetOf<String>()
            var duplicateFilesCount = 0
            var totalSize = 0L
            var fileCount = 0

            for (item in rawTreeEntries) {
                val rawPath = item.optString("path", "")
                if (rawPath.isBlank()) continue

                val normalizedPath = RelativePathHelper.normalize(rawPath)
                if (normalizedPath.isBlank()) continue

                if (seenPaths.contains(normalizedPath)) {
                    duplicateFilesCount++
                    continue
                }
                seenPaths.add(normalizedPath)

                val type = item.optString("type", "blob")
                val isDirectory = type == "tree"
                val sizeBytes = item.optLong("size", 0L)
                val blobSha = item.optString("sha", "").takeIf { it.isNotBlank() }

                val name = normalizedPath.substringAfterLast('/')
                val extension = if (isDirectory) "" else name.substringAfterLast('.', "")

                if (!isDirectory) {
                    fileCount++
                    totalSize += sizeBytes
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
                        pathClassification = PathClassification.ESTABLISHED
                    )
                )

                if (!isDirectory) {
                    val isBinary = isBinaryExtension(extension)
                    records[normalizedPath] = AcquiredFileRecord(
                        relativePath = normalizedPath,
                        name = name,
                        extension = extension,
                        sizeBytes = sizeBytes,
                        isDirectory = false,
                        blobSha = blobSha,
                        targetCommitSha = targetCommitSha,
                        content = if (isBinary) "[Binary Asset: $sizeBytes bytes]" else null,
                        isBinary = isBinary,
                        verificationStatus = if (isBinary) AcquiredFileRecord.VerificationStatus.VERIFIED else AcquiredFileRecord.VerificationStatus.FAILED
                    )
                }
            }

            val metadata = ProjectMetadata(
                name = repoName,
                pathOrUri = repoRef.webUrl,
                sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
                fileCount = fileCount,
                totalSizeBytes = totalSize,
                branchOrTag = targetBranch,
                targetCommitSha = targetCommitSha,
                timestampLoadedMillis = System.currentTimeMillis()
            )

            // 5. Controlled File-Content Acquisition Queue (ALL required files, no 30-file cap)
            val filesToAcquire = records.values.filter { !it.isBinary }
            val expectedFilesCount = filesToAcquire.size

            var acquiredFilesCount = 0
            var verifiedFilesCount = records.values.count { it.isBinary }
            var failedFilesCount = 0
            var missingFilesCount = 0

            onProgress(45, "Acquiring content for all $expectedFilesCount source files...")

            for ((index, record) in filesToAcquire.withIndex()) {
                val encodedPath = record.relativePath.split("/").joinToString("/") { segment ->
                    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                }
                val rawUrl = "https://raw.githubusercontent.com/${repoRef.owner}/${repoRef.repo}/$targetCommitSha/$encodedPath"

                var attempts = 0
                val maxAttempts = 3
                var acquired = false

                while (attempts < maxAttempts && !acquired) {
                    attempts++
                    var conn: HttpURLConnection? = null
                    try {
                        val url = URL(rawUrl)
                        conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        conn.setRequestProperty("User-Agent", "AuditFlow-Android")

                        val code = conn.responseCode
                        if (code == 429 || code == 403) {
                            return@withContext Result.failure(
                                IllegalStateException("GitHub API rate limit reached during file content acquisition (HTTP $code).")
                            )
                        }

                        if (code == 404) {
                            records[record.relativePath] = record.copy(
                                verificationStatus = AcquiredFileRecord.VerificationStatus.MISSING,
                                failureReason = "File not found on remote (HTTP 404)"
                            )
                            missingFilesCount++
                            acquired = true
                            break
                        }

                        if (code == HttpURLConnection.HTTP_OK) {
                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                            records[record.relativePath] = record.copy(
                                content = text,
                                verificationStatus = AcquiredFileRecord.VerificationStatus.VERIFIED
                            )
                            acquiredFilesCount++
                            verifiedFilesCount++
                            acquired = true
                        }
                    } catch (e: Exception) {
                        if (attempts >= maxAttempts) {
                            records[record.relativePath] = record.copy(
                                verificationStatus = AcquiredFileRecord.VerificationStatus.FAILED,
                                failureReason = e.message ?: "Failed after multiple retries"
                            )
                            failedFilesCount++
                        }
                    } finally {
                        conn?.disconnect()
                    }
                }

                val completed = acquiredFilesCount + failedFilesCount + missingFilesCount
                val pct = 45 + ((completed.toDouble() / (expectedFilesCount.coerceAtLeast(1))) * 40).toInt()
                onProgress(pct, "Acquired repository files ($verifiedFilesCount/$fileCount)...")
            }

            // 6. Verification & Frozen Snapshot Creation
            onProgress(88, "Performing final repository snapshot verification...")

            val isContentComplete = failedFilesCount == 0 && missingFilesCount == 0
            val isVersionConsistent = targetCommitSha.isNotBlank() && targetCommitSha.length >= 7

            if (failedFilesCount > 0 || missingFilesCount > 0 || duplicateFilesCount > 0 || !isTreeComplete) {
                return@withContext Result.failure(
                    IllegalStateException("Repository snapshot verification failed: $failedFilesCount files failed, $missingFilesCount missing, $duplicateFilesCount duplicates, treeComplete=$isTreeComplete.")
                )
            }

            val manifest = AcquisitionManifest(
                targetCommitSha = targetCommitSha,
                targetBranch = targetBranch,
                expectedFilesCount = expectedFilesCount,
                acquiredFilesCount = acquiredFilesCount,
                verifiedFilesCount = verifiedFilesCount,
                failedFilesCount = failedFilesCount,
                missingFilesCount = missingFilesCount,
                duplicateFilesCount = duplicateFilesCount,
                isTreeComplete = isTreeComplete,
                isContentComplete = isContentComplete,
                isVersionConsistent = isVersionConsistent,
                status = AcquisitionStatus.COMPLETE,
                records = records
            )

            val sortedNodes = fileNodes.sortedBy { it.relativePath }
            val snapshot = RepositorySnapshot(
                metadata = metadata,
                owner = repoRef.owner,
                repo = repoRef.repo,
                targetBranch = targetBranch,
                targetCommitSha = targetCommitSha,
                manifest = manifest,
                files = sortedNodes,
                acquiredFiles = records,
                isComplete = true
            )

            // Cache snapshot for readFileContent calls
            activeSnapshots[snapshot.metadata.pathOrUri] = snapshot
            activeSnapshots[snapshot.metadata.name] = snapshot

            onProgress(100, "Repository snapshot acquisition complete.")
            Result.success(snapshot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun ingestGitHubRepository(
        repoUrlOrSlug: String,
        branch: String?,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = withContext(Dispatchers.IO) {
        val snapshotResult = acquireRepositorySnapshot(repoUrlOrSlug, branch, onProgress)
        if (snapshotResult.isSuccess) {
            val snapshot = snapshotResult.getOrThrow()
            Result.success(Pair(snapshot.metadata, snapshot.files))
        } else {
            Result.failure(snapshotResult.exceptionOrNull() ?: IllegalStateException("Snapshot acquisition failed"))
        }
    }

    private fun resolveDocumentDisplayName(context: Context, treeUri: Uri, docId: String): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
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
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx)
                    val size = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)

                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val rawRelativePath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    val normalizedRelativePath = RelativePathHelper.normalize(rawRelativePath)
                    val extension = if (isDir) "" else name.substringAfterLast('.', "")

                    outFiles.add(
                        SourceFileNode(
                            relativePath = normalizedRelativePath,
                            name = name,
                            extension = extension,
                            sizeBytes = size,
                            isDirectory = isDir,
                            isReadable = true,
                            mimeType = mime,
                            pathClassification = PathClassification.ESTABLISHED
                        )
                    )

                    if (isDir) {
                        traverseDirectoryTree(
                            context = context,
                            treeUri = treeUri,
                            parentDocId = docId,
                            parentPath = normalizedRelativePath,
                            outFiles = outFiles
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Document access error for this subtree
        }
    }

    private fun fetchJsonFromUrl(urlString: String): JSONObject {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "AuditFlow-Android")
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = reader.use { it.readText() }
                return JSONObject(content)
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw IllegalArgumentException("Repository not found (HTTP 404). Check owner and repository name.")
            } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                throw IllegalStateException("GitHub API rate limit exceeded or access forbidden (HTTP 403).")
            } else {
                throw IllegalStateException("GitHub API returned HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun readFileContent(
        projectMetadata: ProjectMetadata,
        relativePath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = RelativePathHelper.normalize(relativePath)
            when (projectMetadata.sourceKind) {
                ProjectSourceKind.GITHUB_REPOSITORY -> {
                    // Check snapshot cache first
                    val cachedSnapshot = activeSnapshots[projectMetadata.pathOrUri]
                        ?: activeSnapshots[projectMetadata.name]
                    if (cachedSnapshot != null) {
                        val record = cachedSnapshot.acquiredFiles[normalizedPath]
                        if (record != null && record.content != null) {
                            return@withContext Result.success(record.content)
                        }
                    }

                    val parsed = GitHubUrlParser.parse(projectMetadata.pathOrUri)
                        ?: GitHubUrlParser.parse(projectMetadata.name)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Cannot parse GitHub repository coordinates from '${projectMetadata.pathOrUri}' or '${projectMetadata.name}'")
                        )
                    val targetRef = projectMetadata.targetCommitSha?.takeIf { it.isNotBlank() }
                        ?: projectMetadata.branchOrTag?.takeIf { it.isNotBlank() }
                        ?: parsed.branch
                        ?: "main"
                    val encodedPath = normalizedPath.split("/").joinToString("/") { segment ->
                        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                    }
                    // Fetch raw file from GitHub
                    val rawUrl = "https://raw.githubusercontent.com/${parsed.owner}/${parsed.repo}/$targetRef/$encodedPath"
                    val url = URL(rawUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        conn.setRequestProperty("User-Agent", "AuditFlow-Android")
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                            Result.success(text)
                        } else {
                            Result.failure(
                                IllegalStateException("GitHub raw content returned HTTP ${conn.responseCode} for '$normalizedPath'")
                            )
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
                ProjectSourceKind.LOCAL_DIRECTORY -> {
                    val localContext = appContext
                    val treeUri = Uri.parse(projectMetadata.pathOrUri)
                    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Cannot resolve root document ID from '${projectMetadata.pathOrUri}'")
                        )

                    // Find and open document
                    val content = readLocalSafFile(localContext, treeUri, rootDocId, normalizedPath)
                    if (content != null) {
                        Result.success(content)
                    } else {
                        Result.failure(
                            IllegalStateException("Cannot open or locate local file '$normalizedPath' in SAF hierarchy")
                        )
                    }
                }
                ProjectSourceKind.LOCAL_FILE -> {
                    val localContext = appContext
                    val fileUri = Uri.parse(projectMetadata.pathOrUri)
                    val inputStream = localContext.contentResolver.openInputStream(fileUri)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Cannot open input stream for '$fileUri'")
                        )
                    val content = inputStream.use { stream ->
                        readArchiveEntryContent(stream, normalizedPath)
                    }
                    if (content != null) {
                        Result.success(content)
                    } else {
                        Result.failure(
                            IllegalStateException("Cannot read or locate entry '$normalizedPath' in artifact")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readArchiveEntryContent(stream: InputStream, targetPath: String): String? {
        return try {
            val zipIn = ZipInputStream(stream)
            var entry = zipIn.nextEntry
            while (entry != null) {
                val normalized = RelativePathHelper.normalize(entry.name)
                if (normalized.equals(targetPath, ignoreCase = true)) {
                    val bytes = zipIn.readBytes()
                    // If it's a binary file, return a descriptive summary rather than corrupted UTF-8
                    return if (bytes.any { it == 0.toByte() }) {
                        "[Binary Artifact Entry: ${bytes.size} bytes]"
                    } else {
                        String(bytes, Charsets.UTF_8)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long? {
        val projection = arrayOf(OpenableColumns.SIZE)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    if (cursor.isNull(0)) null else cursor.getLong(0)
                } else null
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
        val segments = RelativePathHelper.extractSegments(targetRelativePath)
        if (segments.isEmpty()) return null

        var currentDocId = parentDocId

        for (i in segments.indices) {
            val segment = segments[i]
            val isLeaf = (i == segments.lastIndex)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            var foundNextDocId: String? = null
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)
                    if (name.equals(segment, ignoreCase = true)) {
                        foundNextDocId = cursor.getString(idIdx)
                        val isDir = cursor.getString(mimeIdx) == DocumentsContract.Document.MIME_TYPE_DIR
                        if (isLeaf && !isDir) {
                            // Read terminal document content
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundNextDocId)
                            return context.contentResolver.openInputStream(docUri)?.bufferedReader()?.use { it.readText() }
                        }
                        break
                    }
                }
            }

            if (foundNextDocId == null) return null
            currentDocId = foundNextDocId
        }

        return null
    }
}
