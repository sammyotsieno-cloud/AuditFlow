package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.ArtifactIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactIdentifierTest {

    @Test
    fun identifyArtifact_detectsApkByFileNameAndManifest() {
        val entries = listOf(
            "AndroidManifest.xml",
            "classes.dex",
            "resources.arsc"
        )

        val (identity, content) = ArtifactIdentifier.identifyFromEntries("test-release.apk", entries)
        assertEquals(ArtifactIdentity.APK, identity)
        assertEquals(ArchiveContentIdentity.APK, content)
    }

    @Test
    fun identifyArtifact_detectsZipArchive() {
        val entries = listOf(
            "app/src/main/java/Main.kt",
            "build.gradle.kts"
        )

        val (identity, content) = ArtifactIdentifier.identifyFromEntries("project.zip", entries)
        assertEquals(ArtifactIdentity.ZIP_ARCHIVE, identity)
        assertEquals(ArchiveContentIdentity.REPOSITORY_CONTENT, content)
    }

    @Test
    fun identifyZipContent_detectsSourceProject() {
        val entries = listOf(
            "app/src/main/java/Main.kt",
            "app/src/main/java/Utils.kt",
            "app/src/main/java/Helper.kt"
        )
        val contentIdentity = ArtifactIdentifier.classifyArchiveContent(entries)
        assertEquals(ArchiveContentIdentity.SOURCE_PROJECT, contentIdentity)
    }

    @Test
    fun identifyZipContent_detectsCompiledBinaries() {
        val entries = listOf(
            "lib/arm64/libnative.so",
            "lib/x86/libnative.so",
            "classes.bin"
        )
        val contentIdentity = ArtifactIdentifier.classifyArchiveContent(entries)
        assertEquals(ArchiveContentIdentity.BINARY_COLLECTION, contentIdentity)
    }

    @Test
    fun identifyZipContent_detectsDocumentCollection() {
        val entries = listOf(
            "documents/report.pdf",
            "docs/README.md"
        )
        val contentIdentity = ArtifactIdentifier.classifyArchiveContent(entries)
        assertEquals(ArchiveContentIdentity.DOCUMENT_COLLECTION, contentIdentity)
    }

    @Test
    fun isZip_validatesMagicBytes() {
        val validZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)
        val invalidZip = byteArrayOf(0x00, 0x01, 0x02, 0x03)

        assertTrue(ArtifactIdentifier.isZipMagic(validZip))
        assertFalse(ArtifactIdentifier.isZipMagic(invalidZip))
    }
}
