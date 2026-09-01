package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ApkPackageMetadata
import com.auditflow.app.domain.model.ApkSigningInformation
import com.auditflow.app.domain.model.DexFileInfo
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.RelativePathHelper
import com.auditflow.app.domain.model.SemanticFileType
import com.auditflow.app.domain.model.SourceFileNode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Domain extractor for Android Application Packages (APK).
 *
 * Guaranteed truthful behavior:
 * - APK is treated as a compiled Android artifact, NOT a source repository.
 * - DEX files, compiled XML, native libraries, and assets are extracted truthfully.
 * - No fake source trees or synthetic code files are invented.
 */
object ApkStructureExtractor {

    data class ApkExtractionResult(
        val metadata: ApkPackageMetadata,
        val nodes: List<SourceFileNode>
    )

    fun extract(inputStream: InputStream, apkFileName: String = "app.apk"): ApkExtractionResult {
        val zipIn = ZipInputStream(inputStream)
        val fileNodes = mutableListOf<SourceFileNode>()
        val dexFiles = mutableListOf<DexFileInfo>()
        val nativeLibraries = mutableListOf<String>()
        var hasResourcesArsc = false
        var resourceCount = 0
        var assetCount = 0
        var manifestBytes: ByteArray? = null
        var certBytes: ByteArray? = null
        var certFileName: String? = null

        var entry: ZipEntry? = zipIn.nextEntry
        val buffer = ByteArray(8192)

        while (entry != null) {
            val rawName = entry.name
            // ZIP Slip protection: ignore entries with '..'
            val normalizedPath = RelativePathHelper.normalize(rawName)
            if (!normalizedPath.contains("..") && normalizedPath.isNotBlank()) {
                val isDir = entry.isDirectory
                val name = normalizedPath.substringAfterLast('/')
                val ext = if (isDir) "" else name.substringAfterLast('.', "")
                val size = if (entry.size >= 0) entry.size else 0L

                // Read content for specific files if needed
                val isManifest = normalizedPath.equals("AndroidManifest.xml", ignoreCase = true)
                val isDex = name.endsWith(".dex", ignoreCase = true)
                val isResArsc = normalizedPath.equals("resources.arsc", ignoreCase = true)
                val isCert = normalizedPath.startsWith("META-INF/", ignoreCase = true) &&
                        (name.endsWith(".RSA", ignoreCase = true) || name.endsWith(".DSA", ignoreCase = true) || name.endsWith(".EC", ignoreCase = true))

                var entryBytes: ByteArray? = null
                if (isManifest || isDex || isCert) {
                    val baos = ByteArrayOutputStream()
                    var bytesRead: Int
                    while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                        baos.write(buffer, 0, bytesRead)
                    }
                    entryBytes = baos.toByteArray()
                }

                if (isManifest && entryBytes != null) {
                    manifestBytes = entryBytes
                }

                if (isDex) {
                    val dexInfo = parseDexHeader(name, entryBytes ?: ByteArray(0), size)
                    dexFiles.add(dexInfo)
                }

                if (isResArsc) {
                    hasResourcesArsc = true
                }

                if (normalizedPath.startsWith("res/")) {
                    if (!isDir) resourceCount++
                }

                if (normalizedPath.startsWith("assets/")) {
                    if (!isDir) assetCount++
                }

                if (normalizedPath.startsWith("lib/") && name.endsWith(".so", ignoreCase = true)) {
                    nativeLibraries.add(normalizedPath)
                }

                if (isCert && entryBytes != null && certBytes == null) {
                    certBytes = entryBytes
                    certFileName = name
                }

                fileNodes.add(
                    SourceFileNode(
                        relativePath = normalizedPath,
                        name = name,
                        extension = ext,
                        sizeBytes = if (entryBytes != null) entryBytes.size.toLong() else size,
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

        // Parse Manifest
        val parsedManifest = if (manifestBytes != null) {
            AndroidBinaryXmlParser.parse(manifestBytes)
        } else null

        // Parse Signing Info
        val signingInfo = extractSigningInfo(certBytes, certFileName)

        val apkMetadata = ApkPackageMetadata(
            applicationId = parsedManifest?.applicationId ?: apkFileName.substringBeforeLast(".apk").ifBlank { "unknown.application" },
            versionCode = parsedManifest?.versionCode,
            versionName = parsedManifest?.versionName,
            minSdk = parsedManifest?.minSdk,
            targetSdk = parsedManifest?.targetSdk,
            permissions = parsedManifest?.permissions ?: emptyList(),
            components = parsedManifest?.components ?: emptyList(),
            dexFiles = dexFiles.sortedBy { it.fileName },
            nativeLibraries = nativeLibraries.sorted(),
            hasResourcesArsc = hasResourcesArsc,
            resourceCount = resourceCount,
            assetCount = assetCount,
            signingInfo = signingInfo
        )

        return ApkExtractionResult(
            metadata = apkMetadata,
            nodes = fileNodes.sortedBy { it.relativePath }
        )
    }

    private fun parseDexHeader(fileName: String, bytes: ByteArray, fallbackSize: Long): DexFileInfo {
        val actualSize = if (bytes.isNotEmpty()) bytes.size.toLong() else fallbackSize
        if (bytes.size < 0x70) {
            return DexFileInfo(fileName = fileName, sizeBytes = actualSize, classCount = 0, methodCount = 0)
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8)
            buffer.get(magic)
            val magicStr = String(magic)

            if (!magicStr.startsWith("dex\n")) {
                return DexFileInfo(fileName = fileName, sizeBytes = actualSize, classCount = 0, methodCount = 0)
            }

            // Offset 0x58 (88): method_ids_size
            buffer.position(0x58)
            val methodIdsSize = buffer.getInt()

            // Offset 0x60 (96): class_defs_size
            buffer.position(0x60)
            val classDefsSize = buffer.getInt()

            DexFileInfo(
                fileName = fileName,
                sizeBytes = actualSize,
                classCount = classDefsSize.coerceAtLeast(0),
                methodCount = methodIdsSize.coerceAtLeast(0)
            )
        } catch (e: Exception) {
            DexFileInfo(fileName = fileName, sizeBytes = actualSize, classCount = 0, methodCount = 0)
        }
    }

    private fun extractSigningInfo(certBytes: ByteArray?, certFileName: String?): ApkSigningInformation? {
        if (certBytes == null || certBytes.isEmpty()) {
            return null
        }

        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = cf.generateCertificates(certBytes.inputStream())
            val x509 = certs.filterIsInstance<X509Certificate>().firstOrNull()

            if (x509 != null) {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(x509.encoded)
                val sha256 = digest.joinToString(":") { String.format("%02X", it) }

                ApkSigningInformation(
                    isSigned = true,
                    schemeVersions = listOf("JAR Signing (v1)"),
                    signers = listOf(x509.subjectX500Principal.name),
                    certificateSubject = x509.subjectX500Principal.name,
                    certificateIssuer = x509.issuerX500Principal.name,
                    certificateSha256 = sha256
                )
            } else {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(certBytes)
                val sha256 = digest.joinToString(":") { String.format("%02X", it) }

                ApkSigningInformation(
                    isSigned = true,
                    schemeVersions = listOf("JAR Signing (v1)"),
                    signers = listOf(certFileName ?: "Certificate"),
                    certificateSha256 = sha256
                )
            }
        } catch (e: Exception) {
            ApkSigningInformation(
                isSigned = true,
                schemeVersions = listOf("Signature present"),
                signers = listOf(certFileName ?: "Unknown signer")
            )
        }
    }
}
