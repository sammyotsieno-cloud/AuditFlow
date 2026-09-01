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
import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.ArtifactIdentity
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.RelativePathHelper
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
import java.util.zip.ZipInputStream

/**
 * Real implementation of ProjectIngestionRepository.
 * Performs genuine Android SAF directory traversal and authentic GitHub Git Tree API enumeration.
 *
 * Invariant: Never fabricates synthetic file trees or mock data.
 */
class ProjectIngestionRepositoryImpl : ProjectIngestionRepository {

    override suspend fun ingestLocalDirectory(
        treeUri: Uri,
        context: Context,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = withContext(Dispatchers.IO) {
        try {
            // Persist URI read permissions
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (ignored: Exception) {
                // Best effort for persisted URI permissions
            }

            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Could not extract root document ID from selected URI: $treeUri")
                )

            onProgress(10, "Accessing local directory...")

            val rootName = resolveDocumentDisplayName(context, treeUri, rootDocId) ?: "local_project"
            val fileNodes = mutableListOf<SourceFileNode>()

            onProgress(25, "Enumerating directory contents...")

            // Recursively traverse SAF directory tree
            traverseDirectoryTree(
                context = context,
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
                pathOrUri = treeUri.toString(),
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
        fileUri: Uri,
        context: Context,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = withContext(Dispatchers.IO) {
        try {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(fileUri, takeFlags)
            } catch (ignored: Exception) {
            }

            onProgress(5, "Resolving local file...")

            val fileName = queryFileName(context, fileUri) ?: "artifact"
            val fileSize = queryFileSize(context, fileUri) ?: 0L

            onProgress(15, "Reading artifact header...")

            val headerBytes = ByteArray(8)
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                stream.read(headerBytes)
            }

            val isZip = ArtifactIdentifier.isZipMagic(headerBytes)
            val isApk = fileName.endsWith(".apk", ignoreCase = true) || (isZip && fileName.lowercase().endsWith(".apk"))

            if (isApk || isZip || fileName.endsWith(".zip", ignoreCase = true)) {
                onProgress(30, "Analyzing artifact structure...")

                val inputStream = context.contentResolver.openInputStream(fileUri)
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

    override suspend fun ingestGitHubRepository(
        repoUrlOrSlug: String,
        branch: String?,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = withContext(Dispatchers.IO) {
        try {
            val repoRef = GitHubUrlParser.parse(repoUrlOrSlug)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid GitHub repository format. Expected 'owner/repo' or 'https://github.com/owner/repo'.")
                )

            onProgress(15, "Connecting to GitHub API for ${repoRef.slug}...")

            // 1. Fetch repository metadata to determine default branch and repo info
            val repoApiUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}"
            val repoJson = fetchJsonFromUrl(repoApiUrl)

            val repoName = repoJson.optString("name", repoRef.repo)
            val defaultBranch = repoJson.optString("default_branch", "main")
            val targetBranch = branch ?: repoRef.branch ?: defaultBranch

            onProgress(45, "Fetching Git tree for branch '$targetBranch'...")

            // 2. Fetch recursive git tree
            val treeApiUrl = "https://api.github.com/repos/${repoRef.owner}/${repoRef.repo}/git/trees/$targetBranch?recursive=1"
            val treeJson = fetchJsonFromUrl(treeApiUrl)

            val treeArray = treeJson.optJSONArray("tree")
                ?: return@withContext Result.failure(
                    IllegalArgumentException("GitHub repository branch '$targetBranch' contains an empty or truncated Git tree.")
                )

            onProgress(75, "Parsing ${treeArray.length()} tree elements...")

            val fileNodes = mutableListOf<SourceFileNode>()
            var totalSize = 0L
            var fileCount = 0

            for (i in 0 until treeArray.length()) {
                val item = treeArray.getJSONObject(i)
                val rawPath = item.optString("path", "")
                if (rawPath.isBlank()) continue

                val normalizedPath = RelativePathHelper.normalize(rawPath)
                if (normalizedPath.isBlank()) continue

                val type = item.optString("type", "blob")
                val isDirectory = type == "tree"
                val sizeBytes = item.optLong("size", 0L)

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
            }

            if (fileNodes.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("GitHub repository '${repoRef.slug}' contains no source files.")
                )
            }

            onProgress(95, "Validating source tree...")

            val sortedNodes = fileNodes.sortedBy { it.relativePath }

            val metadata = ProjectMetadata(
                name = repoName,
                pathOrUri = repoRef.webUrl,
                sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
                fileCount = fileCount,
                totalSizeBytes = totalSize,
                branchOrTag = targetBranch,
                timestampLoadedMillis = System.currentTimeMillis()
            )

            onProgress(100, "GitHub ingestion complete.")
            Result.success(Pair(metadata, sortedNodes))
        } catch (e: Exception) {
            Result.failure(e)
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
        relativePath: String,
        context: Context?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = RelativePathHelper.normalize(relativePath)
            when (projectMetadata.sourceKind) {
                ProjectSourceKind.GITHUB_REPOSITORY -> {
                    val parsed = GitHubUrlParser.parse(projectMetadata.name)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Cannot parse GitHub repository coordinates from '${projectMetadata.name}'")
                        )
                    // Fetch raw file from GitHub
                    val rawUrl = "https://raw.githubusercontent.com/${parsed.owner}/${parsed.repo}/main/$normalizedPath"
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
                    if (context == null) {
                        return@withContext Result.failure(
                            IllegalStateException("Local file inspection requires a valid Android Context")
                        )
                    }
                    val treeUri = Uri.parse(projectMetadata.pathOrUri)
                    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Cannot resolve root document ID from '${projectMetadata.pathOrUri}'")
                        )

                    // Find and open document
                    val content = readLocalSafFile(context, treeUri, rootDocId, normalizedPath)
                    if (content != null) {
                        Result.success(content)
                    } else {
                        Result.failure(
                            IllegalStateException("Cannot open or locate local file '$normalizedPath' in SAF hierarchy")
                        )
                    }
                }
                ProjectSourceKind.LOCAL_FILE -> {
                    if (context == null) {
                        return@withContext Result.failure(
                            IllegalStateException("Local artifact inspection requires a valid Android Context")
                        )
                    }
                    val fileUri = Uri.parse(projectMetadata.pathOrUri)
                    val inputStream = context.contentResolver.openInputStream(fileUri)
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
