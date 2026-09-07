package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.ArtifactIdentity
import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.ContentAvailabilityState
import com.auditflow.app.domain.model.ParsingStatus
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.RelativePathHelper
import com.auditflow.app.domain.model.SemanticFileType
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.util.GitHubUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

/**
 * Verification test for Bridge 1:
 * GitHub File Inventory (SourceFileNode) -> Actual Content -> FileInspectionStation -> FileInspectionResult.
 */
class GitHubFileInspectionBridgeTest {

    @Test
    fun gitHubMetadata_preservesExactCoordinatesForContentRetrieval() {
        val metadata = ProjectMetadata(
            name = "AuditFlow",
            pathOrUri = "https://github.com/auditflow/app",
            sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
            fileCount = 42,
            totalSizeBytes = 10240L,
            branchOrTag = "develop",
            artifactIdentity = ArtifactIdentity.REPOSITORY
        )

        val repoRef = GitHubUrlParser.parse(metadata.pathOrUri)
            ?: GitHubUrlParser.parse(metadata.name)

        assertNotNull(
            "Coordinates must be resolvable from pathOrUri",
            repoRef
        )

        assertEquals(
            "auditflow",
            repoRef!!.owner
        )

        assertEquals(
            "app",
            repoRef.repo
        )

        val targetBranch =
            metadata.branchOrTag?.takeIf { it.isNotBlank() }
                ?: repoRef.branch
                ?: "main"

        assertEquals(
            "develop",
            targetBranch
        )

        val relativePath =
            "app/src/main/java/com/auditflow/MainActivity.kt"

        val normalizedPath =
            RelativePathHelper.normalize(relativePath)

        val encodedPath =
            normalizedPath.split("/").joinToString("/") { segment ->
                URLEncoder.encode(
                    segment,
                    "UTF-8"
                ).replace(
                    "+",
                    "%20"
                )
            }

        val expectedRawUrl =
            "https://raw.githubusercontent.com/auditflow/app/develop/app/src/main/java/com/auditflow/MainActivity.kt"

        val constructedRawUrl =
            "https://raw.githubusercontent.com/${repoRef.owner}/${repoRef.repo}/$targetBranch/$encodedPath"

        assertEquals(
            expectedRawUrl,
            constructedRawUrl
        )
    }

    @Test
    fun gitHubSourceFileNode_withRetrievedContent_producesCompleteInspectionResult() {
        val sourceNode = SourceFileNode(
            relativePath =
                "app/src/main/java/com/auditflow/SecurityScanner.kt",
            name = "SecurityScanner.kt",
            extension = "kt",
            sizeBytes = 350L,
            isDirectory = false,
            isReadable = true,
            pathClassification = PathClassification.ESTABLISHED
        )

        val retrievedGitHubContent = """
            package com.auditflow.security

            import java.security.MessageDigest
            import javax.crypto.Cipher

            class SecurityScanner {
                fun computeHash(data: ByteArray): ByteArray {
                    return MessageDigest.getInstance("SHA-256").digest(data)
                }
            }
        """.trimIndent()

        val result =
            FileInspectionStation.inspectFile(
                sourceNode,
                retrievedGitHubContent
            )

        assertEquals(
            "app/src/main/java/com/auditflow/SecurityScanner.kt",
            result.relativePath
        )

        assertEquals(
            SemanticFileType.KOTLIN_SOURCE,
            result.semanticFileType
        )

        assertEquals(
            ContentAvailabilityState.AVAILABLE,
            result.contentAvailability
        )

        assertEquals(
            ParsingStatus.PARSED_SUCCESS,
            result.parsingStatus
        )

        assertEquals(
            "com.auditflow.security",
            result.declaredPackage
        )

        assertTrue(
            "Package discrepancy must be detected when relative path and package diverge",
            result.packageDiscrepancy
        )

        assertEquals(
            2,
            result.imports.size
        )

        /*
         * ImportDeclaration has two distinct concepts:
         *
         * importPath:
         *     complete imported path
         *
         * importedSymbolName:
         *     final/simple symbol represented by the import
         *
         * The canonical parser intentionally stores:
         * java.security.MessageDigest -> MessageDigest
         * javax.crypto.Cipher         -> Cipher
         *
         * Therefore verify both the complete path and canonical symbol name.
         */
        assertEquals(
            "java.security.MessageDigest",
            result.imports[0].importPath
        )

        assertEquals(
            "MessageDigest",
            result.imports[0].importedSymbolName
        )

        assertEquals(
            "javax.crypto.Cipher",
            result.imports[1].importPath
        )

        assertEquals(
            "Cipher",
            result.imports[1].importedSymbolName
        )

        assertEquals(
            1,
            result.topLevelSymbols.size
        )

        val classSymbol =
            result.topLevelSymbols[0]

        assertEquals(
            "SecurityScanner",
            classSymbol.name
        )

        assertEquals(
            CodeSymbolKind.CLASS,
            classSymbol.kind
        )

        assertEquals(
            1,
            classSymbol.childSymbols.size
        )

        val funcSymbol =
            classSymbol.childSymbols[0]

        assertEquals(
            "computeHash",
            funcSymbol.name
        )

        assertEquals(
            CodeSymbolKind.FUNCTION,
            funcSymbol.kind
        )

        assertNotNull(
            "SHA-256 must be computed for retrieved content",
            result.contentSha256
        )

        assertTrue(
            result.linesOfCode > 0
        )
    }

    @Test
    fun gitHubSourceFileNode_whenContentRetrievalFails_producesTruthfulUnavailableResult() {
        val sourceNode = SourceFileNode(
            relativePath =
                "app/src/main/java/com/auditflow/RemoteOnly.kt",
            name = "RemoteOnly.kt",
            extension = "kt",
            sizeBytes = 200L,
            isDirectory = false,
            isReadable = true
        )

        val result =
            FileInspectionStation.inspectFile(
                sourceNode,
                null
            )

        assertEquals(
            "app/src/main/java/com/auditflow/RemoteOnly.kt",
            result.relativePath
        )

        assertEquals(
            ContentAvailabilityState.UNAVAILABLE_NOT_FETCHED,
            result.contentAvailability
        )

        assertEquals(
            ParsingStatus.UNAVAILABLE_CONTENT,
            result.parsingStatus
        )

        assertNull(
            result.contentSha256
        )

        assertNull(
            result.declaredPackage
        )

        assertTrue(
            result.imports.isEmpty()
        )

        assertTrue(
            result.topLevelSymbols.isEmpty()
        )

        assertTrue(
            result.parsingErrors.isNotEmpty()
        )
    }

    @Test
    fun gitHubInventory_inspectProject_mapsAllFilesDeterministically() {
        val files = listOf(
            SourceFileNode(
                relativePath = "README.md",
                name = "README.md",
                extension = "md",
                sizeBytes = 100L,
                isDirectory = false
            ),
            SourceFileNode(
                relativePath = "src/Main.kt",
                name = "Main.kt",
                extension = "kt",
                sizeBytes = 150L,
                isDirectory = false
            )
        )

        val contentStore = mapOf(
            "README.md" to "# AuditFlow Repository",
            "src/Main.kt" to "package com.example\nfun main() {}"
        )

        val results =
            files.map { fileNode ->
                val content =
                    contentStore[fileNode.relativePath]

                FileInspectionStation.inspectFile(
                    fileNode,
                    content
                )
            }

        assertEquals(
            2,
            results.size
        )

        val mdResult =
            results[0]

        assertEquals(
            SemanticFileType.MARKDOWN,
            mdResult.semanticFileType
        )

        assertEquals(
            ParsingStatus.SKIPPED_NON_SOURCE,
            mdResult.parsingStatus
        )

        val ktResult =
            results[1]

        assertEquals(
            SemanticFileType.KOTLIN_SOURCE,
            ktResult.semanticFileType
        )

        assertEquals(
            ParsingStatus.PARSED_SUCCESS,
            ktResult.parsingStatus
        )

        assertEquals(
            "com.example",
            ktResult.declaredPackage
        )

        assertEquals(
            1,
            ktResult.topLevelSymbols.size
        )

        assertEquals(
            "main",
            ktResult.topLevelSymbols[0].name
        )
    }
}
