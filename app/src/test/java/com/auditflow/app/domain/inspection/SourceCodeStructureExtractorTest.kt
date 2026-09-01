package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.ContentAvailabilityState
import com.auditflow.app.domain.model.ParsingStatus
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.PhysicalHierarchyResult
import com.auditflow.app.domain.model.PhysicalNodeType
import com.auditflow.app.domain.model.ProjectRoot
import com.auditflow.app.domain.model.SemanticFileType
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.util.ProjectTreeReconstructor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive unit verification of Station 3:
 * FILE CONTENT → CODE STRUCTURE → SYMBOL DISCOVERY
 *
 * Strict Compliance:
 * - NAME IS NOT ARCHITECTURE
 * - SYMBOL HIERARCHY & OWNERSHIP
 * - EVIDENCE-BASED DISCOVERY
 * - EPISTEMIC DISCIPLINE
 */
class SourceCodeStructureExtractorTest {

    // ========================================================================
    // 1. KOTLIN SOURCE INSPECTION & PACKAGE / IMPORTS EXTRACTION
    // ========================================================================

    @Test
    fun `kotlin source package and imports are extracted with exact line numbers and aliases`() {
        val rawSource = """
            package com.auditflow.sample.data
            
            import android.content.Context
            import com.auditflow.domain.model.RiskItem as RiskModel
            import kotlinx.coroutines.*
            
            class RiskRepositoryImpl(private val context: Context) {
                fun getRiskCount(): Int = 42
            }
        """.trimIndent()

        val sourceNode = SourceFileNode(
            relativePath = "app/src/main/java/com/auditflow/sample/data/RiskRepositoryImpl.kt",
            name = "RiskRepositoryImpl.kt",
            extension = "kt",
            sizeBytes = rawSource.toByteArray().size.toLong(),
            isDirectory = false,
            pathClassification = PathClassification.ESTABLISHED
        )

        val result = SourceCodeStructureExtractor.inspect(sourceNode, rawSource)

        assertEquals(ParsingStatus.PARSED_SUCCESS, result.parsingStatus)
        assertEquals(ContentAvailabilityState.AVAILABLE, result.contentAvailability)
        assertEquals("com.auditflow.sample.data", result.declaredPackage)
        assertFalse(result.packageDiscrepancy)
        assertEquals(3, result.imports.size)

        // Import 1: Standard
        assertEquals("android.content.Context", result.imports[0].importPath)
        assertEquals("Context", result.imports[0].importedSymbolName)
        assertFalse(result.imports[0].isWildcard)
        assertNull(result.imports[0].alias)

        // Import 2: Aliased
        assertEquals("com.auditflow.domain.model.RiskItem", result.imports[1].importPath)
        assertEquals("RiskItem", result.imports[1].importedSymbolName)
        assertEquals("RiskModel", result.imports[1].alias)

        // Import 3: Wildcard
        assertEquals("kotlinx.coroutines.*", result.imports[2].importPath)
        assertEquals("*", result.imports[2].importedSymbolName)
        assertTrue(result.imports[2].isWildcard)
    }

    // ========================================================================
    // 2. SYMBOL HIERARCHY, CLASSES, METHODS, AND PROPERTIES
    // ========================================================================

    @Test
    fun `nested symbols retain exact parent ownership and distinguish methods from standalone functions`() {
        val rawSource = """
            package com.example.app
            
            // Top-level standalone function
            fun topLevelCalculator(input: Int): Int {
                return input * 2
            }
            
            // Top-level property
            const val MAX_RETRIES: Int = 3
            
            class OrderManager(val orderId: String) {
                private val retryCount = 0
                
                constructor(orderId: String, initialRetries: Int) : this(orderId)
                
                suspend fun processOrder(urgent: Boolean = false): Boolean {
                    return true
                }
            }
        """.trimIndent()

        val sourceNode = SourceFileNode(
            relativePath = "app/src/main/java/com/example/app/OrderManager.kt",
            name = "OrderManager.kt",
            extension = "kt",
            sizeBytes = rawSource.toByteArray().size.toLong(),
            isDirectory = false
        )

        val result = SourceCodeStructureExtractor.inspect(sourceNode, rawSource)

        assertEquals(ParsingStatus.PARSED_SUCCESS, result.parsingStatus)
        assertEquals(3, result.topLevelSymbols.size)

        // 1. Top-level standalone function
        val topFunc = result.topLevelSymbols[0]
        assertEquals("topLevelCalculator", topFunc.name)
        assertEquals(CodeSymbolKind.FUNCTION, topFunc.kind)
        assertFalse(topFunc.isMethod)
        assertNull(topFunc.containingSymbolName)
        assertEquals(1, topFunc.parameters.size)
        assertEquals("input", topFunc.parameters[0].name)
        assertEquals("Int", topFunc.parameters[0].type)

        // 2. Top-level constant
        val topConst = result.topLevelSymbols[1]
        assertEquals("MAX_RETRIES", topConst.name)
        assertEquals(CodeSymbolKind.CONSTANT, topConst.kind)
        assertNull(topConst.containingSymbolName)

        // 3. Class OrderManager and its owned children
        val orderClass = result.topLevelSymbols[2]
        assertEquals("OrderManager", orderClass.name)
        assertEquals(CodeSymbolKind.CLASS, orderClass.kind)
        assertNull(orderClass.containingSymbolName)

        // Children owned by OrderManager
        val children = orderClass.childSymbols
        assertEquals(3, children.size)

        // Property inside class
        val prop = children.find { it.name == "retryCount" }
        assertNotNull(prop)
        assertEquals(CodeSymbolKind.PROPERTY, prop!!.kind)
        assertEquals("OrderManager", prop.containingSymbolName)
        assertEquals("private", prop.visibility)

        // Constructor inside class
        val ctor = children.find { it.kind == CodeSymbolKind.CONSTRUCTOR }
        assertNotNull(ctor)
        assertEquals("OrderManager", ctor!!.containingSymbolName)
        assertTrue(ctor.isMethod)

        // Method inside class
        val method = children.find { it.name == "processOrder" }
        assertNotNull(method)
        assertEquals(CodeSymbolKind.METHOD, method!!.kind)
        assertTrue(method.isMethod)
        assertTrue(method.isSuspend)
        assertEquals("OrderManager", method.containingSymbolName)
        assertEquals(1, method.parameters.size)
        assertEquals("urgent", method.parameters[0].name)
        assertEquals("Boolean", method.parameters[0].type)
        assertTrue(method.parameters[0].hasDefaultValue)
        assertEquals("Boolean", method.returnType)
    }

    // ========================================================================
    // 3. INTERFACES, DATA CLASSES, ENUMS, OBJECTS, COMPOSABLES
    // ========================================================================

    @Test
    fun `extracts data classes, sealed classes, interfaces, objects, enums, and composables`() {
        val rawSource = """
            package com.example.models
            
            import androidx.compose.runtime.Composable
            
            interface TradeService {
                suspend fun execute(): Boolean
            }
            
            data class TradePayload(val id: String, val amount: Double)
            
            enum class TradeStatus {
                PENDING, EXECUTED, CANCELLED
            }
            
            object TradeConstants {
                val DEFAULT_CURRENCY = "USD"
            }
            
            @Composable
            fun TradeDashboardScreen() {
            }
        """.trimIndent()

        val sourceNode = SourceFileNode(
            relativePath = "app/src/main/java/com/example/models/TradeModels.kt",
            name = "TradeModels.kt",
            extension = "kt",
            sizeBytes = rawSource.toByteArray().size.toLong(),
            isDirectory = false
        )

        val result = SourceCodeStructureExtractor.inspect(sourceNode, rawSource)

        assertEquals(5, result.topLevelSymbols.size)

        // Interface
        assertEquals("TradeService", result.topLevelSymbols[0].name)
        assertEquals(CodeSymbolKind.INTERFACE, result.topLevelSymbols[0].kind)

        // Data class
        assertEquals("TradePayload", result.topLevelSymbols[1].name)
        assertEquals(CodeSymbolKind.DATA_CLASS, result.topLevelSymbols[1].kind)
        assertEquals(2, result.topLevelSymbols[1].parameters.size)

        // Enum class
        assertEquals("TradeStatus", result.topLevelSymbols[2].name)
        assertEquals(CodeSymbolKind.ENUM, result.topLevelSymbols[2].kind)

        // Object
        assertEquals("TradeConstants", result.topLevelSymbols[3].name)
        assertEquals(CodeSymbolKind.OBJECT, result.topLevelSymbols[3].kind)
        assertEquals(1, result.topLevelSymbols[3].childSymbols.size)

        // Composable function
        val composableFunc = result.topLevelSymbols[4]
        assertEquals("TradeDashboardScreen", composableFunc.name)
        assertTrue(composableFunc.isComposable)
        assertEquals(CodeSymbolKind.COMPOSABLE, composableFunc.kind)
    }

    // ========================================================================
    // 4. ABSOLUTE LAW: NAME IS NOT ARCHITECTURE (Banana.kt TEST)
    // ========================================================================

    @Test
    fun `name is not architecture - file named Banana_kt is extracted purely by its real code structure`() {
        val rawSource = """
            package com.example.persistence
            
            import androidx.room.Dao
            import androidx.room.Insert
            
            @Dao
            interface BananaDao {
                @Insert
                suspend fun insertUser(user: UserRecord): Long
            }
            
            class BananaDatabaseHelper(val dbName: String) {
                fun openDatabase(): Boolean = true
            }
        """.trimIndent()

        val sourceNode = SourceFileNode(
            relativePath = "app/src/main/java/com/example/Banana.kt",
            name = "Banana.kt",
            extension = "kt",
            sizeBytes = rawSource.toByteArray().size.toLong(),
            isDirectory = false
        )

        val result = SourceCodeStructureExtractor.inspect(sourceNode, rawSource)

        // Structural truth extracted regardless of filename
        assertEquals(SemanticFileType.KOTLIN_SOURCE, result.semanticFileType)
        assertEquals("com.example.persistence", result.declaredPackage)
        assertEquals(2, result.topLevelSymbols.size)

        val daoInterface = result.topLevelSymbols[0]
        assertEquals("BananaDao", daoInterface.name)
        assertEquals(CodeSymbolKind.INTERFACE, daoInterface.kind)
        assertTrue(daoInterface.annotations.any { it.contains("Dao") })

        val dbHelper = result.topLevelSymbols[1]
        assertEquals("BananaDatabaseHelper", dbHelper.name)
        assertEquals(CodeSymbolKind.CLASS, dbHelper.kind)
        assertEquals(1, dbHelper.childSymbols.size)
        assertEquals("openDatabase", dbHelper.childSymbols[0].name)
        assertTrue(dbHelper.childSymbols[0].isMethod)
    }

    // ========================================================================
    // 5. MISSING AND UNREADABLE CONTENT REPORTING
    // ========================================================================

    @Test
    fun `missing and unreadable content is reported honestly without fabricating symbols`() {
        val sourceNode = SourceFileNode(
            relativePath = "app/src/main/java/com/example/Missing.kt",
            name = "Missing.kt",
            extension = "kt",
            sizeBytes = 1024L,
            isDirectory = false
        )

        // Case 1: Raw content is null (not yet fetched)
        val nullResult = SourceCodeStructureExtractor.inspect(sourceNode, null)
        assertEquals(ContentAvailabilityState.UNAVAILABLE_NOT_FETCHED, nullResult.contentAvailability)
        assertEquals(ParsingStatus.UNAVAILABLE_CONTENT, nullResult.parsingStatus)
        assertTrue(nullResult.topLevelSymbols.isEmpty())
        assertTrue(nullResult.parsingErrors.isNotEmpty())

        // Case 2: Raw content is empty
        val emptyResult = SourceCodeStructureExtractor.inspect(sourceNode, "")
        assertEquals(ContentAvailabilityState.UNAVAILABLE_EMPTY, emptyResult.contentAvailability)
        assertEquals(ParsingStatus.UNAVAILABLE_CONTENT, emptyResult.parsingStatus)
        assertTrue(emptyResult.topLevelSymbols.isEmpty())
    }

    // ========================================================================
    // 6. SYNTAX ANOMALIES AND UNCLOSED BLOCKS
    // ========================================================================

    @Test
    fun `unclosed blocks are handled gracefully with partial status and error notes`() {
        val brokenSource = """
            package com.example.broken
            
            class UnclosedClass {
                fun doWork() {
                    // Mismatched curly brace
        """.trimIndent()

        val sourceNode = SourceFileNode(
            relativePath = "app/src/main/java/com/example/broken/UnclosedClass.kt",
            name = "UnclosedClass.kt",
            extension = "kt",
            sizeBytes = brokenSource.toByteArray().size.toLong(),
            isDirectory = false
        )

        val result = SourceCodeStructureExtractor.inspect(sourceNode, brokenSource)

        assertEquals(ParsingStatus.PARSED_PARTIAL, result.parsingStatus)
        assertTrue(result.parsingErrors.isNotEmpty())
        assertTrue(result.topLevelSymbols.any { it.name == "UnclosedClass" })
    }

    // ========================================================================
    // 7. JAVA AND ANDROID MANIFEST PARSING
    // ========================================================================

    @Test
    fun `java files and android manifest files are inspected into semantic symbols`() {
        // Java
        val javaSource = """
            package com.example.java;
            
            import java.util.List;
            
            public class JavaController {
                public void executeTask() {}
            }
        """.trimIndent()

        val javaNode = SourceFileNode(
            relativePath = "app/src/main/java/com/example/java/JavaController.java",
            name = "JavaController.java",
            extension = "java",
            sizeBytes = javaSource.toByteArray().size.toLong(),
            isDirectory = false
        )

        val javaResult = SourceCodeStructureExtractor.inspect(javaNode, javaSource)
        assertEquals(SemanticFileType.JAVA_SOURCE, javaResult.semanticFileType)
        assertEquals("com.example.java", javaResult.declaredPackage)
        assertEquals(1, javaResult.topLevelSymbols.size)
        assertEquals("JavaController", javaResult.topLevelSymbols[0].name)

        // Android Manifest
        val manifestXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.myapp">
                <application>
                    <activity android:name=".MainActivity" />
                    <service android:name=".BackgroundSyncService" />
                </application>
            </manifest>
        """.trimIndent()

        val manifestNode = SourceFileNode(
            relativePath = "app/src/main/AndroidManifest.xml",
            name = "AndroidManifest.xml",
            extension = "xml",
            sizeBytes = manifestXml.toByteArray().size.toLong(),
            isDirectory = false
        )

        val manifestResult = SourceCodeStructureExtractor.inspect(manifestNode, manifestXml)
        assertEquals(SemanticFileType.ANDROID_MANIFEST, manifestResult.semanticFileType)
        assertEquals("com.example.myapp", manifestResult.declaredPackage)
        assertEquals(2, manifestResult.topLevelSymbols.size)
        assertTrue(manifestResult.topLevelSymbols.any { it.name == ".MainActivity" })
        assertTrue(manifestResult.topLevelSymbols.any { it.name == ".BackgroundSyncService" })
    }

    // ========================================================================
    // 8. BINARY AND NON-SOURCE FILES ARE HONESTLY SKIPPED
    // ========================================================================

    @Test
    fun `binary and image files are marked non-source and not parsed for code symbols`() {
        val imageNode = SourceFileNode(
            relativePath = "app/src/main/res/drawable/ic_logo.png",
            name = "ic_logo.png",
            extension = "png",
            sizeBytes = 4096L,
            isDirectory = false
        )

        val result = SourceCodeStructureExtractor.inspect(imageNode, "binary-bytes")
        assertEquals(SemanticFileType.BINARY_OR_IMAGE, result.semanticFileType)
        assertEquals(ContentAvailabilityState.UNAVAILABLE_BINARY, result.contentAvailability)
        assertEquals(ParsingStatus.SKIPPED_NON_SOURCE, result.parsingStatus)
        assertTrue(result.topLevelSymbols.isEmpty())
    }

    // ========================================================================
    // 9. STATION 3 COORDINATOR PIPELINE INTEGRATION
    // ========================================================================

    @Test
    fun `file inspection station coordinates physical hierarchy with content provider`() {
        val root = ProjectRoot(name = "StationTest", originPathOrUri = "/test")
        val file1 = SourceFileNode(relativePath = "app/src/main/java/com/test/A.kt", name = "A.kt", extension = "kt", sizeBytes = 100L, isDirectory = false)
        val file2 = SourceFileNode(relativePath = "app/src/main/res/drawable/logo.png", name = "logo.png", extension = "png", sizeBytes = 200L, isDirectory = false)

        val treeRoot = ProjectTreeReconstructor.reconstruct(root.name, listOf(file1, file2))
        val hierarchyResult = PhysicalHierarchyResult(
            projectRoot = root,
            rootNode = treeRoot,
            totalNodes = 5,
            rootPaths = treeRoot.children.filter { it.isRootPath },
            childPaths = treeRoot.children.flatMap { it.children }
        )

        val inspectionResults = FileInspectionStation.inspectHierarchy(hierarchyResult) { path ->
            if (path == "app/src/main/java/com/test/A.kt") {
                """
                    package com.test
                    
                    class A {
                        fun work(): Unit = Unit
                    }
                """.trimIndent()
            } else {
                null
            }
        }

        assertEquals(2, inspectionResults.size)
        val kotlinResult = inspectionResults.find { it.relativePath == "app/src/main/java/com/test/A.kt" }
        assertNotNull(kotlinResult)
        assertEquals("com.test", kotlinResult!!.declaredPackage)
        assertEquals(1, kotlinResult.topLevelSymbols.size)
        assertEquals("A", kotlinResult.topLevelSymbols[0].name)
        assertEquals(1, kotlinResult.topLevelSymbols[0].childSymbols.size)
        assertEquals("work", kotlinResult.topLevelSymbols[0].childSymbols[0].name)
    }
}
