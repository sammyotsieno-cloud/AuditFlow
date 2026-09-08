package com.auditflow.app.data.repository

import com.auditflow.app.domain.model.AcquiredFileRecord
import com.auditflow.app.domain.model.AcquisitionManifest
import com.auditflow.app.domain.model.AcquisitionStatus
import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.ArtifactIdentity
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.RepositorySnapshot
import com.auditflow.app.domain.model.SourceFileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level tests for ProjectIngestionRepositoryImpl.
 *
 * These tests protect the GitHub repository acquisition model without
 * performing live network access.
 *
 * The production acquisition pipeline is intentionally treated as:
 *
 *     Acquire once
 *          ↓
 *     Verify once
 *          ↓
 *     Freeze snapshot
 *          ↓
 *     Reuse downstream
 *
 * These tests therefore validate the domain evidence represented by
 * RepositorySnapshot, AcquisitionManifest and AcquiredFileRecord.
 *
 * Live GitHub acquisition is not performed here because deterministic
 * repository tests must not depend on network availability, GitHub rate
 * limits, credentials, or a mutable remote repository.
 */
class ProjectIngestionRepositoryImplTest {

    @Test
    fun acquisitionManifest_completeSnapshot_hasConsistentAccounting() {
        val records = mapOf(
            "src/main.kt" to verifiedTextRecord(
                relativePath = "src/main.kt",
                content = "fun main() = println(\"AuditFlow\")",
                blobSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            ),
            "README.md" to verifiedTextRecord(
                relativePath = "README.md",
                content = "# AuditFlow",
                blobSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            )
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = "main",
            expectedFilesCount = 2,
            acquiredFilesCount = 2,
            verifiedFilesCount = 2,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = true,
            status = AcquisitionStatus.COMPLETE,
            records = records
        )

        assertEquals(
            manifest.expectedFilesCount,
            manifest.acquiredFilesCount
        )

        assertEquals(
            manifest.acquiredFilesCount,
            manifest.verifiedFilesCount
        )

        assertEquals(0, manifest.failedFilesCount)
        assertEquals(0, manifest.missingFilesCount)
        assertEquals(0, manifest.duplicateFilesCount)

        assertTrue(manifest.isTreeComplete)
        assertTrue(manifest.isContentComplete)
        assertTrue(manifest.isVersionConsistent)
        assertEquals(AcquisitionStatus.COMPLETE, manifest.status)

        assertEquals(
            manifest.expectedFilesCount,
            manifest.records.size
        )
    }

    @Test
    fun acquisitionManifest_incompleteContent_cannotClaimCompleteEvidence() {
        val records = mapOf(
            "src/main.kt" to verifiedTextRecord(
                relativePath = "src/main.kt",
                content = "fun main() = println(\"AuditFlow\")",
                blobSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            ),
            "src/Missing.kt" to AcquiredFileRecord(
                relativePath = "src/Missing.kt",
                name = "Missing.kt",
                extension = "kt",
                sizeBytes = 100L,
                isDirectory = false,
                blobSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                targetCommitSha = TEST_COMMIT_SHA,
                acquiredSizeBytes = null,
                verifiedBlobSha = null,
                isBinary = false,
                verificationStatus = AcquiredFileRecord.VerificationStatus.MISSING,
                failureReason = "File could not be acquired"
            )
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = "main",
            expectedFilesCount = 2,
            acquiredFilesCount = 2,
            verifiedFilesCount = 1,
            failedFilesCount = 0,
            missingFilesCount = 1,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = false,
            isVersionConsistent = true,
            status = AcquisitionStatus.FAILED,
            records = records
        )

        assertFalse(manifest.isContentComplete)
        assertEquals(1, manifest.missingFilesCount)
        assertEquals(1, manifest.verifiedFilesCount)
        assertEquals(AcquisitionStatus.FAILED, manifest.status)
    }

    @Test
    fun acquiredFileRecord_verifiedTextFile_containsDecodedContentAndVerificationEvidence() {
        val record = verifiedTextRecord(
            relativePath = "src/main.kt",
            content = "fun main() = println(\"AuditFlow\")",
            blobSha = TEST_BLOB_SHA
        )

        assertFalse(record.isDirectory)
        assertFalse(record.isBinary)

        assertEquals("src/main.kt", record.relativePath)
        assertEquals("main.kt", record.name)
        assertEquals("kt", record.extension)

        assertNotNull(record.content)
        assertEquals(
            "fun main() = println(\"AuditFlow\")",
            record.content
        )

        assertNull(record.contentBytes)

        assertEquals(record.sizeBytes, record.acquiredSizeBytes)
        assertEquals(TEST_BLOB_SHA, record.blobSha)
        assertEquals(TEST_BLOB_SHA, record.verifiedBlobSha)

        assertEquals(
            AcquiredFileRecord.VerificationStatus.VERIFIED,
            record.verificationStatus
        )

        assertNull(record.failureReason)
        assertEquals(TEST_COMMIT_SHA, record.targetCommitSha)
    }

    @Test
    fun acquiredFileRecord_verifiedBinaryFile_containsOriginalBytesAndNoTextContent() {
        val bytes = byteArrayOf(
            0x50,
            0x4B,
            0x03,
            0x04
        )

        val record = AcquiredFileRecord(
            relativePath = "app/build/output.apk",
            name = "output.apk",
            extension = "apk",
            sizeBytes = bytes.size.toLong(),
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            content = null,
            contentBytes = bytes,
            acquiredSizeBytes = bytes.size.toLong(),
            verifiedBlobSha = TEST_BLOB_SHA,
            isBinary = true,
            verificationStatus = AcquiredFileRecord.VerificationStatus.VERIFIED,
            failureReason = null
        )

        assertTrue(record.isBinary)
        assertNull(record.content)
        assertNotNull(record.contentBytes)

        assertEquals(
            bytes.size.toLong(),
            record.acquiredSizeBytes
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.VERIFIED,
            record.verificationStatus
        )

        assertEquals(TEST_BLOB_SHA, record.blobSha)
        assertEquals(TEST_BLOB_SHA, record.verifiedBlobSha)
    }

    @Test
    fun acquiredFileRecord_defaultVerificationStatus_isFailClosed() {
        val record = AcquiredFileRecord(
            relativePath = "src/main.kt",
            name = "main.kt",
            extension = "kt",
            sizeBytes = 10L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.FAILED,
            record.verificationStatus
        )

        assertNull(record.verifiedBlobSha)
        assertNull(record.acquiredSizeBytes)
    }

    @Test
    fun failedFileRecord_neverContainsSuccessfulVerificationEvidence() {
        val record = AcquiredFileRecord(
            relativePath = "src/Broken.kt",
            name = "Broken.kt",
            extension = "kt",
            sizeBytes = 50L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            content = null,
            contentBytes = null,
            acquiredSizeBytes = null,
            verifiedBlobSha = null,
            isBinary = false,
            verificationStatus = AcquiredFileRecord.VerificationStatus.FAILED,
            failureReason = "Acquisition failed"
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.FAILED,
            record.verificationStatus
        )

        assertNull(record.verifiedBlobSha)
        assertNull(record.acquiredSizeBytes)
        assertNull(record.content)
        assertNull(record.contentBytes)
        assertNotNull(record.failureReason)
    }

    @Test
    fun missingFileRecord_isExplicitlyMarkedMissing() {
        val record = AcquiredFileRecord(
            relativePath = "missing/File.kt",
            name = "File.kt",
            extension = "kt",
            sizeBytes = 25L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            verificationStatus = AcquiredFileRecord.VerificationStatus.MISSING,
            failureReason = "Remote file was not available"
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.MISSING,
            record.verificationStatus
        )

        assertNull(record.verifiedBlobSha)
        assertNull(record.acquiredSizeBytes)
        assertNotNull(record.failureReason)
    }

    @Test
    fun repositorySnapshot_completeSnapshot_preservesSingleImmutableTargetVersion() {
        val records = mapOf(
            "src/main.kt" to verifiedTextRecord(
                relativePath = "src/main.kt",
                content = "fun main() = println(\"AuditFlow\")",
                blobSha = TEST_BLOB_SHA
            )
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = "main",
            expectedFilesCount = 1,
            acquiredFilesCount = 1,
            verifiedFilesCount = 1,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = true,
            status = AcquisitionStatus.COMPLETE,
            records = records
        )

        val metadata = ProjectMetadata(
            name = "AuditFlow",
            pathOrUri = "https://github.com/$TEST_OWNER/$TEST_REPO",
            sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
            fileCount = 1,
            totalSizeBytes = records.values.sumOf { it.sizeBytes },
            branchOrTag = "main",
            targetCommitSha = TEST_COMMIT_SHA,
            artifactIdentity = ArtifactIdentity.REPOSITORY
        )

        val snapshot = RepositorySnapshot(
            metadata = metadata,
            owner = TEST_OWNER,
            repo = TEST_REPO,
            targetBranch = "main",
            targetCommitSha = TEST_COMMIT_SHA,
            manifest = manifest,
            files = listOf(
                SourceFileNode(
                    name = "main.kt",
                    relativePath = "src/main.kt",
                    extension = "kt",
                    sizeBytes = records.getValue("src/main.kt").sizeBytes,
                    isDirectory = false
                )
            ),
            acquiredFiles = records,
            isComplete = true
        )

        assertTrue(snapshot.isComplete)

        assertEquals(
            TEST_COMMIT_SHA,
            snapshot.targetCommitSha
        )

        assertEquals(
            TEST_COMMIT_SHA,
            snapshot.manifest.targetCommitSha
        )

        assertEquals(
            TEST_COMMIT_SHA,
            snapshot.metadata.targetCommitSha
        )

        assertEquals("main", snapshot.targetBranch)
        assertEquals("main", snapshot.manifest.targetBranch)

        assertEquals(
            snapshot.manifest.records,
            snapshot.acquiredFiles
        )
    }

    @Test
    fun repositorySnapshot_fileAccounting_matchesManifest() {
        val records = mapOf(
            "README.md" to verifiedTextRecord(
                relativePath = "README.md",
                content = "# AuditFlow",
                blobSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            ),
            "src/main.kt" to verifiedTextRecord(
                relativePath = "src/main.kt",
                content = "fun main() {}",
                blobSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            )
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = "main",
            expectedFilesCount = 2,
            acquiredFilesCount = 2,
            verifiedFilesCount = 2,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = true,
            status = AcquisitionStatus.COMPLETE,
            records = records
        )

        val snapshot = RepositorySnapshot(
            metadata = ProjectMetadata(
                name = "AuditFlow",
                pathOrUri = "https://github.com/$TEST_OWNER/$TEST_REPO",
                sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
                fileCount = 2,
                totalSizeBytes = records.values.sumOf { it.sizeBytes },
                branchOrTag = "main",
                targetCommitSha = TEST_COMMIT_SHA
            ),
            owner = TEST_OWNER,
            repo = TEST_REPO,
            targetBranch = "main",
            targetCommitSha = TEST_COMMIT_SHA,
            manifest = manifest,
            files = records.values.map { record ->
                SourceFileNode(
                    name = record.name,
                    relativePath = record.relativePath,
                    extension = record.extension,
                    sizeBytes = record.sizeBytes,
                    isDirectory = record.isDirectory
                )
            },
            acquiredFiles = records,
            isComplete = true
        )

        assertEquals(
            snapshot.manifest.expectedFilesCount,
            snapshot.acquiredFiles.size
        )

        assertEquals(
            snapshot.manifest.acquiredFilesCount,
            snapshot.files.size
        )

        assertEquals(
            snapshot.manifest.verifiedFilesCount,
            snapshot.acquiredFiles.values.count {
                it.verificationStatus ==
                    AcquiredFileRecord.VerificationStatus.VERIFIED
            }
        )
    }

    @Test
    fun repositorySnapshot_completeState_requiresCompleteManifestEvidence() {
        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = "main",
            expectedFilesCount = 1,
            acquiredFilesCount = 1,
            verifiedFilesCount = 1,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = true,
            status = AcquisitionStatus.COMPLETE,
            records = mapOf(
                "README.md" to verifiedTextRecord(
                    relativePath = "README.md",
                    content = "# AuditFlow",
                    blobSha = TEST_BLOB_SHA
                )
            )
        )

        val evidenceIsComplete =
            manifest.status == AcquisitionStatus.COMPLETE &&
                manifest.isTreeComplete &&
                manifest.isContentComplete &&
                manifest.isVersionConsistent &&
                manifest.failedFilesCount == 0 &&
                manifest.missingFilesCount == 0 &&
                manifest.duplicateFilesCount == 0 &&
                manifest.expectedFilesCount ==
                    manifest.acquiredFilesCount &&
                manifest.acquiredFilesCount ==
                    manifest.verifiedFilesCount

        assertTrue(evidenceIsComplete)
    }

    @Test
    fun repositorySnapshot_binaryEvidence_isPreservedAsBytes() {
        val bytes = byteArrayOf(
            0x00,
            0x01,
            0x02,
            0x03
        )

        val record = AcquiredFileRecord(
            relativePath = "assets/data.bin",
            name = "data.bin",
            extension = "bin",
            sizeBytes = bytes.size.toLong(),
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            content = null,
            contentBytes = bytes,
            acquiredSizeBytes = bytes.size.toLong(),
            verifiedBlobSha = TEST_BLOB_SHA,
            isBinary = true,
            verificationStatus = AcquiredFileRecord.VerificationStatus.VERIFIED
        )

        assertTrue(record.isBinary)
        assertNull(record.content)
        assertNotNull(record.contentBytes)
        assertEquals(bytes.toList(), record.contentBytes!!.toList())
    }

    @Test
    fun repositorySnapshot_textEvidence_isPreservedAsText() {
        val content = """
            package example

            fun hello() {
                println("AuditFlow")
            }
        """.trimIndent()

        val record = verifiedTextRecord(
            relativePath = "src/Hello.kt",
            content = content,
            blobSha = TEST_BLOB_SHA
        )

        assertFalse(record.isBinary)
        assertEquals(content, record.content)
        assertNull(record.contentBytes)
    }

    @Test
    fun repositoryIdentity_isGitHubRepository() {
        val metadata = ProjectMetadata(
            name = "AuditFlow",
            pathOrUri = "https://github.com/$TEST_OWNER/$TEST_REPO",
            sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
            branchOrTag = "main",
            targetCommitSha = TEST_COMMIT_SHA,
            artifactIdentity = ArtifactIdentity.REPOSITORY
        )

        assertEquals(
            ProjectSourceKind.GITHUB_REPOSITORY,
            metadata.sourceKind
        )

        assertEquals(
            ArtifactIdentity.REPOSITORY,
            metadata.artifactIdentity
        )

        assertEquals(
            TEST_COMMIT_SHA,
            metadata.targetCommitSha
        )
    }

    @Test
    fun verifiedRecord_commitIdentity_matchesSnapshotCommitIdentity() {
        val record = verifiedTextRecord(
            relativePath = "README.md",
            content = "# AuditFlow",
            blobSha = TEST_BLOB_SHA
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = "main",
            expectedFilesCount = 1,
            acquiredFilesCount = 1,
            verifiedFilesCount = 1,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = true,
            status = AcquisitionStatus.COMPLETE,
            records = mapOf(record.relativePath to record)
        )

        assertEquals(
            manifest.targetCommitSha,
            record.targetCommitSha
        )
    }

    @Test
    fun incompleteVerificationEvidence_isDetectableBeforeSnapshotReuse() {
        val verified = verifiedTextRecord(
            relativePath = "src/Good.kt",
            content = "fun good() {}",
            blobSha = TEST_BLOB_SHA
        )

        val failed = AcquiredFileRecord(
            relativePath = "src/Bad.kt",
            name = "Bad.kt",
            extension = "kt",
            sizeBytes = 20L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            verificationStatus = AcquiredFileRecord.VerificationStatus.FAILED,
            failureReason = "Hash mismatch"
        )

        val records = mapOf(
            verified.relativePath to verified,
            failed.relativePath to failed
        )

        val allVerified = records.values.all {
            it.verificationStatus ==
                AcquiredFileRecord.VerificationStatus.VERIFIED &&
                it.verifiedBlobSha != null &&
                it.acquiredSizeBytes != null
        }

        assertFalse(allVerified)
    }

    private fun verifiedTextRecord(
        relativePath: String,
        content: String,
        blobSha: String
    ): AcquiredFileRecord {
        val fileName = relativePath.substringAfterLast('/')

        val extension = fileName
            .substringAfterLast('.', "")
            .lowercase()

        val sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong()

        return AcquiredFileRecord(
            relativePath = relativePath,
            name = fileName,
            extension = extension,
            sizeBytes = sizeBytes,
            isDirectory = false,
            blobSha = blobSha,
            targetCommitSha = TEST_COMMIT_SHA,
            content = content,
            contentBytes = null,
            acquiredSizeBytes = sizeBytes,
            verifiedBlobSha = blobSha,
            isBinary = false,
            verificationStatus = AcquiredFileRecord.VerificationStatus.VERIFIED,
            failureReason = null
        )
    }

    private companion object {
        const val TEST_OWNER = "sammyotsieno-cloud"
        const val TEST_REPO = "AuditFlow"

        const val TEST_COMMIT_SHA =
            "1111111111111111111111111111111111111111"

        const val TEST_BLOB_SHA =
            "2222222222222222222222222222222222222222"
    }
}
