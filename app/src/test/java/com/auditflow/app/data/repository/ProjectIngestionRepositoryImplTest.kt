package com.auditflow.app.data.repository

import com.auditflow.app.domain.model.AcquiredFileRecord
import com.auditflow.app.domain.model.AcquisitionManifest
import com.auditflow.app.domain.model.AcquisitionStatus
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
 * Unit tests for the GitHub acquisition evidence contract produced by
 * ProjectIngestionRepositoryImpl.
 *
 * These tests deliberately do not perform live GitHub network acquisition.
 * Live network behavior depends on repository state, GitHub availability,
 * authentication/rate limits, and external network conditions.
 *
 * The production acquisition invariant is:
 *
 *     Acquire once
 *          ↓
 *     Verify once
 *          ↓
 *     Freeze RepositorySnapshot
 *          ↓
 *     Reuse downstream
 *
 * This test suite protects the evidence required for that invariant:
 *
 * - immutable target commit identity
 * - expected/acquired/verified accounting
 * - complete tree/content state
 * - explicit failure and missing states
 * - Git blob verification evidence
 * - acquired size evidence
 * - text content preservation
 * - binary byte preservation
 * - snapshot/manifest consistency
 *
 * The tests intentionally do not introduce another acquisition mechanism.
 */
class ProjectIngestionRepositoryImplTest {

    @Test
    fun verifiedTextRecord_containsCompleteVerificationEvidence() {
        val content = "fun main() = println(\"AuditFlow\")"

        val record = verifiedTextRecord(
            relativePath = "src/main.kt",
            content = content,
            blobSha = TEST_BLOB_SHA
        )

        assertEquals("src/main.kt", record.relativePath)
        assertEquals("main.kt", record.name)
        assertEquals("kt", record.extension)

        assertFalse(record.isDirectory)
        assertFalse(record.isBinary)

        assertEquals(content, record.content)
        assertNull(record.contentBytes)

        assertEquals(
            record.sizeBytes,
            record.acquiredSizeBytes
        )

        assertEquals(
            TEST_BLOB_SHA,
            record.blobSha
        )

        assertEquals(
            TEST_BLOB_SHA,
            record.verifiedBlobSha
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.VERIFIED,
            record.verificationStatus
        )

        assertNull(record.failureReason)
        assertEquals(TEST_COMMIT_SHA, record.targetCommitSha)
    }

    @Test
    fun verifiedBinaryRecord_preservesOriginalBytes() {
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
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.VERIFIED,
            failureReason = null
        )

        assertTrue(record.isBinary)
        assertNull(record.content)
        assertNotNull(record.contentBytes)

        assertEquals(
            bytes.toList(),
            record.contentBytes!!.toList()
        )

        assertEquals(
            bytes.size.toLong(),
            record.acquiredSizeBytes
        )

        assertEquals(
            TEST_BLOB_SHA,
            record.blobSha
        )

        assertEquals(
            TEST_BLOB_SHA,
            record.verifiedBlobSha
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.VERIFIED,
            record.verificationStatus
        )
    }

    @Test
    fun newAcquiredFileRecord_failsClosedUntilVerificationOccurs() {
        val record = AcquiredFileRecord(
            relativePath = "src/main.kt",
            name = "main.kt",
            extension = "kt",
            sizeBytes = 100L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.FAILED,
            record.verificationStatus
        )

        assertNull(record.acquiredSizeBytes)
        assertNull(record.verifiedBlobSha)
        assertNull(record.failureReason)
    }

    @Test
    fun failedRecord_doesNotContainVerificationEvidence() {
        val record = AcquiredFileRecord(
            relativePath = "src/Broken.kt",
            name = "Broken.kt",
            extension = "kt",
            sizeBytes = 100L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            content = null,
            contentBytes = null,
            acquiredSizeBytes = null,
            verifiedBlobSha = null,
            isBinary = false,
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.FAILED,
            failureReason = "Git blob verification failed"
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.FAILED,
            record.verificationStatus
        )

        assertNull(record.acquiredSizeBytes)
        assertNull(record.verifiedBlobSha)
        assertNotNull(record.failureReason)
    }

    @Test
    fun missingRecord_isExplicitlyMarkedMissing() {
        val record = AcquiredFileRecord(
            relativePath = "src/Missing.kt",
            name = "Missing.kt",
            extension = "kt",
            sizeBytes = 100L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            content = null,
            contentBytes = null,
            acquiredSizeBytes = null,
            verifiedBlobSha = null,
            isBinary = false,
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.MISSING,
            failureReason = "File could not be acquired"
        )

        assertEquals(
            AcquiredFileRecord.VerificationStatus.MISSING,
            record.verificationStatus
        )

        assertNull(record.acquiredSizeBytes)
        assertNull(record.verifiedBlobSha)
        assertNotNull(record.failureReason)
    }

    @Test
    fun completeManifest_hasConsistentAcquisitionAccounting() {
        val records = mapOf(
            "README.md" to verifiedTextRecord(
                relativePath = "README.md",
                content = "# AuditFlow",
                blobSha = TEST_BLOB_SHA_1
            ),
            "src/main.kt" to verifiedTextRecord(
                relativePath = "src/main.kt",
                content = "fun main() {}",
                blobSha = TEST_BLOB_SHA_2
            )
        )

        val manifest = completeManifest(records)

        assertEquals(
            2,
            manifest.expectedFilesCount
        )

        assertEquals(
            2,
            manifest.acquiredFilesCount
        )

        assertEquals(
            2,
            manifest.verifiedFilesCount
        )

        assertEquals(
            0,
            manifest.failedFilesCount
        )

        assertEquals(
            0,
            manifest.missingFilesCount
        )

        assertEquals(
            0,
            manifest.duplicateFilesCount
        )

        assertTrue(manifest.isTreeComplete)
        assertTrue(manifest.isContentComplete)
        assertTrue(manifest.isVersionConsistent)

        assertEquals(
            AcquisitionStatus.COMPLETE,
            manifest.status
        )

        assertEquals(
            manifest.expectedFilesCount,
            manifest.records.size
        )

        assertTrue(
            manifest.records.values.all {
                it.verificationStatus ==
                    AcquiredFileRecord.VerificationStatus.VERIFIED
            }
        )
    }

    @Test
    fun incompleteManifest_explicitlyReportsMissingContent() {
        val verifiedRecord = verifiedTextRecord(
            relativePath = "README.md",
            content = "# AuditFlow",
            blobSha = TEST_BLOB_SHA_1
        )

        val missingRecord = AcquiredFileRecord(
            relativePath = "src/Missing.kt",
            name = "Missing.kt",
            extension = "kt",
            sizeBytes = 100L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA_2,
            targetCommitSha = TEST_COMMIT_SHA,
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.MISSING,
            failureReason = "File could not be acquired"
        )

        val records = mapOf(
            verifiedRecord.relativePath to verifiedRecord,
            missingRecord.relativePath to missingRecord
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = TEST_BRANCH,
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

        assertEquals(2, manifest.expectedFilesCount)
        assertEquals(2, manifest.acquiredFilesCount)
        assertEquals(1, manifest.verifiedFilesCount)
        assertEquals(1, manifest.missingFilesCount)

        assertTrue(manifest.isTreeComplete)
        assertFalse(manifest.isContentComplete)
        assertTrue(manifest.isVersionConsistent)

        assertEquals(
            AcquisitionStatus.FAILED,
            manifest.status
        )
    }

    @Test
    fun completeManifest_requiresEveryFileToBeVerified() {
        val verified = verifiedTextRecord(
            relativePath = "README.md",
            content = "# AuditFlow",
            blobSha = TEST_BLOB_SHA_1
        )

        val failed = AcquiredFileRecord(
            relativePath = "src/Broken.kt",
            name = "Broken.kt",
            extension = "kt",
            sizeBytes = 50L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA_2,
            targetCommitSha = TEST_COMMIT_SHA,
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.FAILED,
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

    @Test
    fun completeManifest_requiresVersionConsistency() {
        val records = mapOf(
            "README.md" to verifiedTextRecord(
                relativePath = "README.md",
                content = "# AuditFlow",
                blobSha = TEST_BLOB_SHA
            )
        )

        val manifest = AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = TEST_BRANCH,
            expectedFilesCount = 1,
            acquiredFilesCount = 1,
            verifiedFilesCount = 1,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = false,
            status = AcquisitionStatus.FAILED,
            records = records
        )

        assertFalse(manifest.isVersionConsistent)
        assertEquals(
            AcquisitionStatus.FAILED,
            manifest.status
        )
    }

    @Test
    fun repositorySnapshot_preservesSingleTargetCommitAcrossAllLayers() {
        val record = verifiedTextRecord(
            relativePath = "README.md",
            content = "# AuditFlow",
            blobSha = TEST_BLOB_SHA
        )

        val records = mapOf(
            record.relativePath to record
        )

        val manifest = completeManifest(records)

        val metadata = ProjectMetadata(
            name = TEST_REPO,
            pathOrUri = "https://github.com/$TEST_OWNER/$TEST_REPO",
            sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
            fileCount = 1,
            totalSizeBytes = record.sizeBytes,
            branchOrTag = TEST_BRANCH,
            targetCommitSha = TEST_COMMIT_SHA,
            artifactIdentity = ArtifactIdentity.REPOSITORY
        )

        val snapshot = RepositorySnapshot(
            metadata = metadata,
            owner = TEST_OWNER,
            repo = TEST_REPO,
            targetBranch = TEST_BRANCH,
            targetCommitSha = TEST_COMMIT_SHA,
            manifest = manifest,
            files = listOf(
                SourceFileNode(
                    relativePath = record.relativePath,
                    name = record.name,
                    extension = record.extension,
                    sizeBytes = record.sizeBytes,
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

        assertEquals(
            TEST_COMMIT_SHA,
            record.targetCommitSha
        )

        assertEquals(
            TEST_BRANCH,
            snapshot.targetBranch
        )

        assertEquals(
            TEST_BRANCH,
            snapshot.manifest.targetBranch
        )
    }

    @Test
    fun repositorySnapshot_manifestAndAcquiredFilesRepresentSameRecords() {
        val records = mapOf(
            "README.md" to verifiedTextRecord(
                relativePath = "README.md",
                content = "# AuditFlow",
                blobSha = TEST_BLOB_SHA_1
            ),
            "src/main.kt" to verifiedTextRecord(
                relativePath = "src/main.kt",
                content = "fun main() {}",
                blobSha = TEST_BLOB_SHA_2
            )
        )

        val manifest = completeManifest(records)

        val snapshot = RepositorySnapshot(
            metadata = ProjectMetadata(
                name = TEST_REPO,
                pathOrUri =
                    "https://github.com/$TEST_OWNER/$TEST_REPO",
                sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
                fileCount = records.size,
                totalSizeBytes =
                    records.values.sumOf { it.sizeBytes },
                branchOrTag = TEST_BRANCH,
                targetCommitSha = TEST_COMMIT_SHA,
                artifactIdentity = ArtifactIdentity.REPOSITORY
            ),
            owner = TEST_OWNER,
            repo = TEST_REPO,
            targetBranch = TEST_BRANCH,
            targetCommitSha = TEST_COMMIT_SHA,
            manifest = manifest,
            files = records.values.map { record ->
                SourceFileNode(
                    relativePath = record.relativePath,
                    name = record.name,
                    extension = record.extension,
                    sizeBytes = record.sizeBytes,
                    isDirectory = false
                )
            },
            acquiredFiles = records,
            isComplete = true
        )

        assertEquals(
            snapshot.manifest.records,
            snapshot.acquiredFiles
        )

        assertEquals(
            snapshot.manifest.expectedFilesCount,
            snapshot.acquiredFiles.size
        )

        assertEquals(
            snapshot.manifest.acquiredFilesCount,
            snapshot.files.size
        )
    }

    @Test
    fun repositorySnapshot_completeEvidence_canBeDeterminedFromManifest() {
        val records = mapOf(
            "README.md" to verifiedTextRecord(
                relativePath = "README.md",
                content = "# AuditFlow",
                blobSha = TEST_BLOB_SHA
            )
        )

        val manifest = completeManifest(records)

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
                    manifest.verifiedFilesCount &&
                manifest.records.values.all {
                    it.verificationStatus ==
                        AcquiredFileRecord.VerificationStatus.VERIFIED &&
                        it.verifiedBlobSha != null &&
                        it.acquiredSizeBytes != null
                }

        assertTrue(evidenceIsComplete)
    }

    @Test
    fun binaryRecord_doesNotExposeTextContentAsRepositorySource() {
        val record = AcquiredFileRecord(
            relativePath = "assets/data.bin",
            name = "data.bin",
            extension = "bin",
            sizeBytes = 4L,
            isDirectory = false,
            blobSha = TEST_BLOB_SHA,
            targetCommitSha = TEST_COMMIT_SHA,
            content = null,
            contentBytes = byteArrayOf(
                0x00,
                0x01,
                0x02,
                0x03
            ),
            acquiredSizeBytes = 4L,
            verifiedBlobSha = TEST_BLOB_SHA,
            isBinary = true,
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.VERIFIED
        )

        assertTrue(record.isBinary)
        assertNull(record.content)
        assertNotNull(record.contentBytes)
    }

    @Test
    fun textRecord_doesNotStoreDuplicateBinaryPayload() {
        val record = verifiedTextRecord(
            relativePath = "src/main.kt",
            content = "fun main() {}",
            blobSha = TEST_BLOB_SHA
        )

        assertFalse(record.isBinary)
        assertNotNull(record.content)
        assertNull(record.contentBytes)
    }

    private fun completeManifest(
        records: Map<String, AcquiredFileRecord>
    ): AcquisitionManifest {
        return AcquisitionManifest(
            targetCommitSha = TEST_COMMIT_SHA,
            targetBranch = TEST_BRANCH,
            expectedFilesCount = records.size,
            acquiredFilesCount = records.size,
            verifiedFilesCount = records.size,
            failedFilesCount = 0,
            missingFilesCount = 0,
            duplicateFilesCount = 0,
            isTreeComplete = true,
            isContentComplete = true,
            isVersionConsistent = true,
            status = AcquisitionStatus.COMPLETE,
            records = records
        )
    }

    private fun verifiedTextRecord(
        relativePath: String,
        content: String,
        blobSha: String
    ): AcquiredFileRecord {
        val name = relativePath.substringAfterLast('/')

        val extension = name
            .substringAfterLast('.', "")
            .lowercase()

        val sizeBytes =
            content.toByteArray(Charsets.UTF_8).size.toLong()

        return AcquiredFileRecord(
            relativePath = relativePath,
            name = name,
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
            verificationStatus =
                AcquiredFileRecord.VerificationStatus.VERIFIED,
            failureReason = null
        )
    }

    private companion object {
        const val TEST_OWNER = "sammyotsieno-cloud"
        const val TEST_REPO = "AuditFlow"
        const val TEST_BRANCH = "main"

        const val TEST_COMMIT_SHA =
            "1111111111111111111111111111111111111111"

        const val TEST_BLOB_SHA =
            "2222222222222222222222222222222222222222"

        const val TEST_BLOB_SHA_1 =
            "3333333333333333333333333333333333333333"

        const val TEST_BLOB_SHA_2 =
            "4444444444444444444444444444444444444444"
    }
}
