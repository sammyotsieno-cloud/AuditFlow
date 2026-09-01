package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.RelativePathHelper
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.model.ZipArchiveMetadata
import com.auditflow.app.domain.model.ZipEntryInfo
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Domain extractor for generic ZIP Archives.
 *
 * Truthful Archive Handling:
 * - ZIP is treated as an archive container, NOT automatically a repository.
 * - Exact entry hierarchy, compression ratios, and internal content identities are preserved.
 * - ZIP Slip path traversal attempts are safely prevented.
 */
object ZipStructureExtractor {

    data class ZipExtractionResult(
        val metadata: ZipArchiveMetadata,
        val nodes: List<SourceFileNode>
    )

    fun extract(inputStream: InputStream, zipFileName: String = "archive.zip"): ZipExtractionResult {
        val zipIn = ZipInputStream(inputStream)
        val fileNodes = mutableListOf<SourceFileNode>()
        val zipEntries = mutableListOf<ZipEntryInfo>()
        val entryPaths = mutableListOf<String>()

        var totalUncompressedSize = 0L
        var totalCompressedSize = 0L

        var entry: ZipEntry? = zipIn.nextEntry

        while (entry != null) {
            val rawName = entry.name
            val normalizedPath = RelativePathHelper.normalize(rawName)

            // Prevent ZIP Slip
            if (!normalizedPath.contains("..") && normalizedPath.isNotBlank()) {
                val isDir = entry.isDirectory
                val name = normalizedPath.substringAfterLast('/')
                val ext = if (isDir) "" else name.substringAfterLast('.', "")
                val uncompressedSize = if (entry.size >= 0) entry.size else 0L
                val compressedSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L

                totalUncompressedSize += uncompressedSize
                totalCompressedSize += compressedSize

                entryPaths.add(normalizedPath)

                zipEntries.add(
                    ZipEntryInfo(
                        archivePath = normalizedPath,
                        isDirectory = isDir,
                        compressedSizeBytes = compressedSize,
                        uncompressedSizeBytes = uncompressedSize,
                        crc = entry.crc,
                        compressionMethod = entry.method
                    )
                )

                fileNodes.add(
                    SourceFileNode(
                        relativePath = normalizedPath,
                        name = name,
                        extension = ext,
                        sizeBytes = uncompressedSize,
                        isDirectory = isDir,
                        isReadable = true,
                        mimeType = null,
                        pathClassification = PathClassification.ESTABLISHED
                    )
                )
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        val contentIdentity = ArtifactIdentifier.classifyArchiveContent(entryPaths)
        val containsApk = entryPaths.any { it.endsWith(".apk", ignoreCase = true) } || contentIdentity == ArchiveContentIdentity.APK
        val containsSource = contentIdentity == ArchiveContentIdentity.SOURCE_PROJECT || contentIdentity == ArchiveContentIdentity.REPOSITORY_CONTENT

        val zipMetadata = ZipArchiveMetadata(
            totalEntries = zipEntries.size,
            totalUncompressedSizeBytes = totalUncompressedSize,
            totalCompressedSizeBytes = totalCompressedSize,
            detectedContentIdentity = contentIdentity,
            containsApk = containsApk,
            containsSourceCode = containsSource,
            entries = zipEntries
        )

        return ZipExtractionResult(
            metadata = zipMetadata,
            nodes = fileNodes.sortedBy { it.relativePath }
        )
    }
}
