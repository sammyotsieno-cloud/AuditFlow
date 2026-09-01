package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.ArtifactIdentity

/**
 * Domain service responsible for authoritative Artifact Identification.
 *
 * Core Principle:
 * IDENTIFY FIRST. INTERPRET SECOND. NORMALIZE ONLY WHERE VALID. PRESERVE ORIGINAL MEANING.
 *
 * - An APK is NOT a repository.
 * - A ZIP is NOT automatically a repository.
 * - A Local Directory is NOT automatically a repository.
 */
object ArtifactIdentifier {

    // Magic signatures
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // PK\x03\x04
    private val ZIP_EMPTY_MAGIC = byteArrayOf(0x50, 0x4B, 0x05, 0x06) // PK\x05\x06
    private val ZIP_SPANNED_MAGIC = byteArrayOf(0x50, 0x4B, 0x07, 0x08) // PK\x07\x08

    /**
     * Determines whether byte buffer begins with standard ZIP archive magic signature.
     */
    fun isZipMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return (header[0] == ZIP_MAGIC[0] && header[1] == ZIP_MAGIC[1] && header[2] == ZIP_MAGIC[2] && header[3] == ZIP_MAGIC[3]) ||
                (header[0] == ZIP_EMPTY_MAGIC[0] && header[1] == ZIP_EMPTY_MAGIC[1] && header[2] == ZIP_EMPTY_MAGIC[2] && header[3] == ZIP_EMPTY_MAGIC[3]) ||
                (header[0] == ZIP_SPANNED_MAGIC[0] && header[1] == ZIP_SPANNED_MAGIC[1] && header[2] == ZIP_SPANNED_MAGIC[2] && header[3] == ZIP_SPANNED_MAGIC[3])
    }

    /**
     * Identifies artifact from filename and entry paths.
     */
    fun identifyFromEntries(
        fileName: String,
        entryPaths: List<String>
    ): Pair<ArtifactIdentity, ArchiveContentIdentity?> {
        val lowerName = fileName.lowercase()

        // 1. Check if explicitly an APK or contains APK fingerprint
        val hasAndroidManifest = entryPaths.any { it.equals("AndroidManifest.xml", ignoreCase = true) }
        val hasClassesDex = entryPaths.any { it.matches(Regex("(?i)classes\\d*\\.dex")) }
        val hasResourcesArsc = entryPaths.any { it.equals("resources.arsc", ignoreCase = true) }

        if (lowerName.endsWith(".apk") || (hasAndroidManifest && (hasClassesDex || hasResourcesArsc))) {
            return Pair(ArtifactIdentity.APK, ArchiveContentIdentity.APK)
        }

        // 2. Check if ZIP archive
        if (lowerName.endsWith(".zip") || entryPaths.isNotEmpty()) {
            val contentIdentity = classifyArchiveContent(entryPaths)
            return Pair(ArtifactIdentity.ZIP_ARCHIVE, contentIdentity)
        }

        // 3. Unknown
        return Pair(ArtifactIdentity.UNKNOWN_ARTIFACT, ArchiveContentIdentity.UNKNOWN)
    }

    /**
     * Classifies the internal content identity of a ZIP archive based on its entries.
     */
    fun classifyArchiveContent(entryPaths: List<String>): ArchiveContentIdentity {
        if (entryPaths.isEmpty()) return ArchiveContentIdentity.UNKNOWN

        val normalized = entryPaths.map { it.lowercase() }

        val hasAndroidManifest = normalized.any { it == "androidmanifest.xml" || it.endsWith("/androidmanifest.xml") }
        val hasClassesDex = normalized.any { it.matches(Regex(".*classes\\d*\\.dex")) }
        if (hasAndroidManifest && hasClassesDex) {
            return ArchiveContentIdentity.APK
        }

        // Source / repository content indicators
        val sourceCount = normalized.count {
            it.endsWith(".kt") || it.endsWith(".java") || it.endsWith(".kts") ||
                    it.endsWith(".gradle") || it.endsWith(".xml") || it.endsWith(".c") ||
                    it.endsWith(".cpp") || it.endsWith(".py") || it.endsWith(".js") ||
                    it.endsWith(".ts") || it.endsWith(".rs") || it.endsWith(".go")
        }

        val hasGitDirectory = normalized.any { it.startsWith(".git/") || it.contains("/.git/") }
        val hasBuildScript = normalized.any {
            it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") ||
                    it.endsWith("pom.xml") || it.endsWith("settings.gradle.kts") ||
                    it.endsWith("settings.gradle")
        }

        if (hasGitDirectory || (hasBuildScript && sourceCount > 0)) {
            return ArchiveContentIdentity.REPOSITORY_CONTENT
        }

        if (sourceCount > 0 && sourceCount >= (normalized.size / 3).coerceAtLeast(1)) {
            return ArchiveContentIdentity.SOURCE_PROJECT
        }

        val docCount = normalized.count {
            it.endsWith(".md") || it.endsWith(".txt") || it.endsWith(".pdf") ||
                    it.endsWith(".doc") || it.endsWith(".docx") || it.endsWith(".rst")
        }
        if (docCount > 0 && docCount >= (normalized.size / 2).coerceAtLeast(1)) {
            return ArchiveContentIdentity.DOCUMENT_COLLECTION
        }

        val binaryCount = normalized.count {
            it.endsWith(".so") || it.endsWith(".dll") || it.endsWith(".exe") ||
                    it.endsWith(".class") || it.endsWith(".bin") || it.endsWith(".dylib")
        }
        if (binaryCount > 0 && binaryCount >= (normalized.size / 2).coerceAtLeast(1)) {
            return ArchiveContentIdentity.BINARY_COLLECTION
        }

        return if (normalized.isNotEmpty()) ArchiveContentIdentity.MIXED_CONTENT else ArchiveContentIdentity.UNKNOWN
    }
}
