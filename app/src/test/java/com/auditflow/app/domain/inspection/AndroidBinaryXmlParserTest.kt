package com.auditflow.app.domain.inspection

import org.junit.Assert.assertNull
import org.junit.Test

class AndroidBinaryXmlParserTest {

    @Test
    fun parse_withInvalidHeader_returnsNull() {
        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val manifest = AndroidBinaryXmlParser.parse(invalidBytes)

        assertNull(manifest)
    }

    @Test
    fun parse_withEmptyBytes_returnsNull() {
        val emptyBytes = ByteArray(0)
        val manifest = AndroidBinaryXmlParser.parse(emptyBytes)

        assertNull(manifest)
    }
}
