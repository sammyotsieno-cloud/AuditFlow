package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ArchiveContentIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipStructureExtractorTest {

    @Test
    fun extract_extractsNodesAndZipMetadata() {
        val zipBytes = createSampleZip(
            listOf(
                "src/Main.kt" to "class Main".toByteArray(),
                "build.gradle.kts" to "plugins {}".toByteArray(),
                "docs/README.md" to "# Docs".toByteArray()
            )
        )

        val result = ZipStructureExtractor.extract(
            inputStream = ByteArrayInputStream(zipBytes),
            zipFileName = "sample.zip"
        )

        val metadata = result.metadata
        val nodes = result.nodes

        assertNotNull(metadata)
        assertEquals(3, metadata.totalEntries)
        assertEquals(ArchiveContentIdentity.REPOSITORY_CONTENT, metadata.detectedContentIdentity)
        assertTrue(metadata.containsSourceCode)

        // Verify nodes
        val relativePaths = nodes.map { it.relativePath }
        assertTrue(relativePaths.contains("src/Main.kt"))
        assertTrue(relativePaths.contains("build.gradle.kts"))
        assertTrue(relativePaths.contains("docs/README.md"))
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
