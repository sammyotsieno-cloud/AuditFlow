package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ApkComponentDeclaration
import com.auditflow.app.domain.model.ApkComponentKind
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin, zero-dependency parser for Android Binary XML (AXML) manifests.
 *
 * Decodes the compiled AndroidManifest.xml format found in Android APKs:
 * - Chunk header validation (RES_XML_TYPE = 0x00080003)
 * - String Pool decoding (UTF-8 and UTF-16 supported)
 * - XML element and attribute extraction (applicationId, version, SDKs, permissions, components)
 */
object AndroidBinaryXmlParser {

    private const val RES_XML_TYPE = 0x00080003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    data class ParsedManifest(
        val applicationId: String,
        val versionCode: Long? = null,
        val versionName: String? = null,
        val minSdk: Int? = null,
        val targetSdk: Int? = null,
        val permissions: List<String> = emptyList(),
        val components: List<ApkComponentDeclaration> = emptyList()
    )

    /**
     * Parses binary AndroidManifest.xml bytes.
     * Returns null if the byte array is not a valid binary XML or cannot be parsed.
     */
    fun parse(bytes: ByteArray): ParsedManifest? {
        if (bytes.size < 8) return null
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val headerType = buffer.getInt()
            if (headerType != RES_XML_TYPE && (headerType and 0xFFFF) != 0x0003) {
                // Not standard AXML
                return null
            }
            val fileSize = buffer.getInt()

            val stringPool = mutableListOf<String>()
            var applicationId = ""
            var versionCode: Long? = null
            var versionName: String? = null
            var minSdk: Int? = null
            var targetSdk: Int? = null
            val permissions = mutableListOf<String>()
            val components = mutableListOf<ApkComponentDeclaration>()

            while (buffer.hasRemaining() && buffer.position() < bytes.size - 4) {
                val chunkStartPos = buffer.position()
                val chunkType = buffer.getShort().toInt() and 0xFFFF
                val headerSize = buffer.getShort().toInt() and 0xFFFF
                val chunkSize = buffer.getInt()

                if (chunkSize <= 0 || chunkStartPos + chunkSize > bytes.size) {
                    break
                }

                when (chunkType) {
                    RES_STRING_POOL_TYPE -> {
                        parseStringPool(buffer, chunkStartPos, stringPool)
                        buffer.position(chunkStartPos + chunkSize)
                    }

                    RES_XML_START_ELEMENT_TYPE -> {
                        val lineNumber = buffer.getInt()
                        val comment = buffer.getInt()
                        val nsIndex = buffer.getInt()
                        val nameIndex = buffer.getInt()
                        val attributeStart = buffer.getShort().toInt() and 0xFFFF
                        val attributeSize = buffer.getShort().toInt() and 0xFFFF
                        val attributeCount = buffer.getShort().toInt() and 0xFFFF
                        val idIndex = buffer.getShort().toInt() and 0xFFFF
                        val classIndex = buffer.getShort().toInt() and 0xFFFF
                        val styleIndex = buffer.getShort().toInt() and 0xFFFF

                        val tagName = getString(stringPool, nameIndex)

                        val attributes = mutableMapOf<String, String>()
                        val rawAttributes = mutableListOf<Pair<String, Any>>()

                        val attrOffset = chunkStartPos + headerSize
                        buffer.position(attrOffset)

                        for (i in 0 until attributeCount) {
                            val attrNsIdx = buffer.getInt()
                            val attrNameIdx = buffer.getInt()
                            val attrRawValIdx = buffer.getInt()
                            val typedValSize = buffer.getShort().toInt() and 0xFFFF
                            val res0 = buffer.get().toInt() and 0xFF
                            val dataType = buffer.get().toInt() and 0xFF
                            val data = buffer.getInt()

                            val attrName = getString(stringPool, attrNameIdx)
                            val attrVal = when (dataType) {
                                TYPE_STRING -> getString(stringPool, data).ifBlank { getString(stringPool, attrRawValIdx) }
                                TYPE_INT_BOOLEAN -> (data != 0).toString()
                                TYPE_INT_DEC, TYPE_INT_HEX -> data.toString()
                                else -> getString(stringPool, attrRawValIdx).ifBlank { data.toString() }
                            }

                            if (attrName.isNotBlank()) {
                                attributes[attrName] = attrVal
                                rawAttributes.add(attrName to (if (dataType == TYPE_INT_BOOLEAN) data != 0 else attrVal))
                            }
                        }

                        // Process element
                        when (tagName.lowercase()) {
                            "manifest" -> {
                                applicationId = attributes["package"] ?: applicationId
                                attributes["versionCode"]?.toLongOrNull()?.let { versionCode = it }
                                attributes["versionName"]?.let { versionName = it }
                            }
                            "uses-sdk" -> {
                                attributes["minSdkVersion"]?.toIntOrNull()?.let { minSdk = it }
                                attributes["targetSdkVersion"]?.toIntOrNull()?.let { targetSdk = it }
                            }
                            "uses-permission" -> {
                                val permName = attributes["name"]
                                if (!permName.isNullOrBlank()) {
                                    permissions.add(permName)
                                }
                            }
                            "activity" -> {
                                val name = attributes["name"]
                                if (!name.isNullOrBlank()) {
                                    val resolvedName = resolveComponentName(applicationId, name)
                                    val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                                    val perm = attributes["permission"]
                                    components.add(
                                        ApkComponentDeclaration(
                                            name = resolvedName,
                                            kind = ApkComponentKind.ACTIVITY,
                                            isExported = exported,
                                            permission = perm
                                        )
                                    )
                                }
                            }
                            "service" -> {
                                val name = attributes["name"]
                                if (!name.isNullOrBlank()) {
                                    val resolvedName = resolveComponentName(applicationId, name)
                                    val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                                    val perm = attributes["permission"]
                                    components.add(
                                        ApkComponentDeclaration(
                                            name = resolvedName,
                                            kind = ApkComponentKind.SERVICE,
                                            isExported = exported,
                                            permission = perm
                                        )
                                    )
                                }
                            }
                            "receiver" -> {
                                val name = attributes["name"]
                                if (!name.isNullOrBlank()) {
                                    val resolvedName = resolveComponentName(applicationId, name)
                                    val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                                    val perm = attributes["permission"]
                                    components.add(
                                        ApkComponentDeclaration(
                                            name = resolvedName,
                                            kind = ApkComponentKind.BROADCAST_RECEIVER,
                                            isExported = exported,
                                            permission = perm
                                        )
                                    )
                                }
                            }
                            "provider" -> {
                                val name = attributes["name"]
                                if (!name.isNullOrBlank()) {
                                    val resolvedName = resolveComponentName(applicationId, name)
                                    val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                                    val perm = attributes["permission"]
                                    components.add(
                                        ApkComponentDeclaration(
                                            name = resolvedName,
                                            kind = ApkComponentKind.CONTENT_PROVIDER,
                                            isExported = exported,
                                            permission = perm
                                        )
                                    )
                                }
                            }
                        }

                        buffer.position(chunkStartPos + chunkSize)
                    }

                    else -> {
                        buffer.position(chunkStartPos + chunkSize)
                    }
                }
            }

            if (applicationId.isBlank()) {
                applicationId = "unknown.application"
            }

            ParsedManifest(
                applicationId = applicationId,
                versionCode = versionCode,
                versionName = versionName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = permissions.distinct(),
                components = components
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStringPool(buffer: ByteBuffer, chunkStart: Int, outStrings: MutableList<String>) {
        val stringCount = buffer.getInt()
        val styleCount = buffer.getInt()
        val flags = buffer.getInt()
        val stringsStart = buffer.getInt()
        val stylesStart = buffer.getInt()

        val isUtf8 = (flags and (1 shl 8)) != 0

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.getInt()
        }

        val stringsBase = chunkStart + stringsStart

        for (i in 0 until stringCount) {
            val offset = stringsBase + stringOffsets[i]
            if (offset >= buffer.limit()) {
                outStrings.add("")
                continue
            }
            buffer.position(offset)

            if (isUtf8) {
                // UTF-8 length: 1 or 2 bytes for char length, 1 or 2 bytes for byte length
                var charLen = buffer.get().toInt() and 0xFF
                if ((charLen and 0x80) != 0) {
                    charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                }
                var byteLen = buffer.get().toInt() and 0xFF
                if ((byteLen and 0x80) != 0) {
                    byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                }
                val strBytes = ByteArray(byteLen.coerceAtLeast(0))
                buffer.get(strBytes)
                outStrings.add(String(strBytes, Charsets.UTF_8))
            } else {
                // UTF-16 length: 2 or 4 bytes
                var charLen = buffer.getShort().toInt() and 0xFFFF
                if ((charLen and 0x8000) != 0) {
                    charLen = ((charLen and 0x7FFF) shl 16) or (buffer.getShort().toInt() and 0xFFFF)
                }
                val chars = CharArray(charLen)
                for (c in 0 until charLen) {
                    chars[c] = buffer.getChar()
                }
                outStrings.add(String(chars))
            }
        }
    }

    private fun getString(stringPool: List<String>, index: Int): String {
        return if (index in stringPool.indices) stringPool[index] else ""
    }

    private fun resolveComponentName(packageName: String, componentName: String): String {
        return when {
            componentName.startsWith(".") -> "$packageName$componentName"
            !componentName.contains(".") -> "$packageName.$componentName"
            else -> componentName
        }
    }
}
