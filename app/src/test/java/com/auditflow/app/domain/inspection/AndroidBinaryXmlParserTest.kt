package com.auditflow.app.domain.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBinaryXmlParserTest {

    @Test
    fun parse_withInvalidHeader_returnsFallbackManifest() {
        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val manifest = AndroidBinaryXmlParser.parse(invalidBytes)

        assertNotNull(manifest)
        assertEquals("unknown.package", manifest.packageName)
        assertTrue(manifest.permissions.isEmpty())
        assertTrue(manifest.components.isEmpty())
    }

    @Test
    fun parse_withEmptyBytes_returnsFallbackManifest() {
        val emptyBytes = ByteArray(0)
        val manifest = AndroidBinaryXmlParser.parse(emptyBytes)

        assertNotNull(manifest)
        assertEquals("unknown.package", manifest.packageName)
    }
}
