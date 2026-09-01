package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ArchiveContentIdentity
import com.auditflow.app.domain.model.ArtifactIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtifactIdentifierTest {

    @Test
    fun identifyArtifact_detectsApkByFileNameAndManifest() {
        val zipBytes = createSampleZip(
            listOf(
                "AndroidManifest.xml" to "xmlContent".toByteArray(),
                "classes.dex" to "dexContent".toByteArray(),
                "resources.arsc" to "arscContent".toByteArray()
            )
        )

        val identity = ArtifactIdentifier.identifyArtifact("test-release.apk", zipBytes)
        assertEquals(ArtifactIdentity.APK, identity)
    }

    @Test
    fun identifyArtifact_detectsZipArchive() {
        val zipBytes = createSampleZip(
            listOf(
                "app/src/main/java/Main.kt" to "class Main".toByteArray(),
                "build.gradle.kts" to "plugins {}".toByteArray()
            )
        )

        val identity = ArtifactIdentifier.identifyArtifact("project.zip", zipBytes)
        assertEquals(ArtifactIdentity.ZIP_ARCHIVE, identity)
    }

    @Test
    fun identifyZipContent_detectsSourceProject() {
        val entries = listOf(
            "app/src/main/java/Main.kt",
            "build.gradle.kts",
            "settings.gradle.kts"
        )
        val contentIdentity = ArtifactIdentifier.identifyZipContent(entries)
        assertEquals(ArchiveContentIdentity.ZIP_CONTAINING_SOURCE_PROJECT, contentIdentity)
    }

    @Test
    fun identifyZipContent_detectsCompiledBinaries() {
        val entries = listOf(
            "com/example/Main.class",
            "com/example/Utils.class",
            "META-INF/MANIFEST.MF"
        )
        val contentIdentity = ArtifactIdentifier.identifyZipContent(entries)
        assertEquals(ArchiveContentIdentity.ZIP_CONTAINING_COMPILED_BINARIES, contentIdentity)
    }

    @Test
    fun identifyZipContent_detectsGenericArchive() {
        val entries = listOf(
            "documents/report.pdf",
            "images/photo.png"
        )
        val contentIdentity = ArtifactIdentifier.identifyZipContent(entries)
        assertEquals(ArchiveContentIdentity.ZIP_GENERIC_ARCHIVE, contentIdentity)
    }

    @Test
    fun isZip_validatesMagicBytes() {
        val validZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)
        val invalidZip = byteArrayOf(0x00, 0x01, 0x02, 0x03)

        assertTrue(ArtifactIdentifier.isZip(validZip))
        assertFalse(ArtifactIdentifier.isZip(invalidZip))
    }

    private fun createSampleZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
