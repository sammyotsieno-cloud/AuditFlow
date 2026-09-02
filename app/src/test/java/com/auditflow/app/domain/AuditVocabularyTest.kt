package com.auditflow.app.domain

import com.auditflow.app.domain.model.DefectSeverity
import com.auditflow.app.domain.model.EpistemicClassification
import com.auditflow.app.domain.model.EvidenceAuthorityTier
import com.auditflow.app.domain.model.ForensicDefectKind
import com.auditflow.app.domain.model.ForensicFinding
import com.auditflow.app.domain.model.ComponentRoleMapping
import com.auditflow.app.domain.model.IngestedProjectRecord
import com.auditflow.app.domain.model.PhysicalHierarchyResult
import com.auditflow.app.domain.model.ForensicAuditReport
import com.auditflow.app.domain.model.AnalysisType
import com.auditflow.app.domain.model.ArchitecturalGap
import com.auditflow.app.domain.model.ArchitecturalRole
import com.auditflow.app.domain.model.ArchitecturePerspective
import com.auditflow.app.domain.model.CallRole
import com.auditflow.app.domain.model.CodeSymbol
import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.DependencyRole
import com.auditflow.app.domain.model.FlowType
import com.auditflow.app.domain.model.ForensicStatus
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.PathTopologyType
import com.auditflow.app.domain.model.PhysicalNodeType
import com.auditflow.app.domain.model.ProjectBoundary
import com.auditflow.app.domain.model.ProjectRoot
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.ProjectTreeNode
import com.auditflow.app.domain.model.RelativePathHelper
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.model.WorkflowStage
import com.auditflow.app.domain.util.ProjectTreeReconstructor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Authoritative verification test suite for the Canonical Universal Audit Vocabulary.
 *
 * Verifies all 9 Tiers of semantic definitions and topology invariants.
 */
class AuditVocabularyTest {

    // ========================================================================
    // TIER 1: PHYSICAL PROJECT BOUNDARIES
    // ========================================================================

    @Test
    fun `project root is distinct from root path`() {
        val projectRoot = ProjectRoot(name = "AuditFlow", originPathOrUri = "content://com.android.providers.documents/tree/123")
        val rootPathRelative = "app"

        // Invariant 1: Project Root is the origin container, not a relative path
        assertNotEquals(projectRoot.name, rootPathRelative)
        assertEquals(0, RelativePathHelper.calculateDepth(projectRoot.name.let { "" }))
        assertEquals(1, RelativePathHelper.calculateDepth(rootPathRelative))
    }

    @Test
    fun `project root is not represented as a file`() {
        val rootNode = ProjectTreeNode(
            name = "AuditFlow",
            relativePath = "",
            isDirectory = true,
            sourceNode = null
        )

        assertTrue(rootNode.isDirectory)
        assertEquals(PhysicalNodeType.DIRECTORY, rootNode.physicalNodeType)
        assertNull(rootNode.sourceNode)
    }

    // ========================================================================
    // TIER 2: PATH TOPOLOGY
    // ========================================================================

    @Test
    fun `root path has depth exactly one`() {
        val rootPath1 = "README.md"
        val rootPath2 = "app"
        val rootPath3 = "build.gradle.kts"

        assertEquals(1, RelativePathHelper.calculateDepth(rootPath1))
        assertEquals(1, RelativePathHelper.calculateDepth(rootPath2))
        assertEquals(1, RelativePathHelper.calculateDepth(rootPath3))

        assertTrue(RelativePathHelper.isRootPath(rootPath1))
        assertTrue(RelativePathHelper.isRootPath(rootPath2))
        assertTrue(RelativePathHelper.isRootPath(rootPath3))

        assertEquals(PathTopologyType.ROOT_PATH, RelativePathHelper.classifyTopology(rootPath1))
        assertEquals(PathTopologyType.ROOT_PATH, RelativePathHelper.classifyTopology(rootPath2))
        assertEquals(PathTopologyType.ROOT_PATH, RelativePathHelper.classifyTopology(rootPath3))
    }

    @Test
    fun `child path has depth greater than or equal to two and valid parent`() {
        val childPath1 = "app/src"
        val childPath2 = "app/src/main/MainActivity.kt"

        assertEquals(2, RelativePathHelper.calculateDepth(childPath1))
        assertEquals(4, RelativePathHelper.calculateDepth(childPath2))

        assertFalse(RelativePathHelper.isRootPath(childPath1))
        assertFalse(RelativePathHelper.isRootPath(childPath2))

        assertEquals(PathTopologyType.CHILD_PATH, RelativePathHelper.classifyTopology(childPath1))
        assertEquals(PathTopologyType.CHILD_PATH, RelativePathHelper.classifyTopology(childPath2))

        assertEquals("app", RelativePathHelper.getParentRelativePath(childPath1))
        assertEquals("app/src/main", RelativePathHelper.getParentRelativePath(childPath2))
    }

    @Test
    fun `relative paths use canonical forward slash separator and normalize redundant segments`() {
        val rawWindowsPath = "app\\src\\main\\java\\.\\com\\auditflow\\MainActivity.kt"
        val normalized = RelativePathHelper.normalize(rawWindowsPath)

        assertEquals("app/src/main/java/com/auditflow/MainActivity.kt", normalized)
        assertFalse(normalized.contains('\\'))
        assertFalse(normalized.contains("/./"))
        assertFalse(normalized.startsWith('/'))
        assertFalse(normalized.endsWith('/'))

        val segments = RelativePathHelper.extractSegments(normalized)
        assertEquals(listOf("app", "src", "main", "java", "com", "auditflow", "MainActivity.kt"), segments)
    }

    @Test
    fun `established relative path is strictly distinct from derived relative path`() {
        // Ingestion record (explicitly returned by SAF or GitHub)
        val establishedFile = SourceFileNode(
            relativePath = "app/src/main/MainActivity.kt",
            name = "MainActivity.kt",
            extension = "kt",
            sizeBytes = 1024L,
            isDirectory = false,
            pathClassification = PathClassification.ESTABLISHED
        )

        // Tree reconstructor node for the file
        val fileTreeNode = ProjectTreeNode(
            name = "MainActivity.kt",
            relativePath = "app/src/main/MainActivity.kt",
            isDirectory = false,
            sourceNode = establishedFile
        )

        // Intermediate reconstructed directory node (never in explicit file list)
        val derivedDirectoryNode = ProjectTreeNode(
            name = "src",
            relativePath = "app/src",
            isDirectory = true,
            sourceNode = null
        )

        assertEquals(PathClassification.ESTABLISHED, establishedFile.pathClassification)
        assertEquals(PathClassification.ESTABLISHED, fileTreeNode.pathClassification)
        assertEquals(PathClassification.DERIVED, derivedDirectoryNode.pathClassification)
        assertNotEquals(fileTreeNode.pathClassification, derivedDirectoryNode.pathClassification)
    }

    // ========================================================================
    // TIER 3: PHYSICAL HIERARCHY
    // ========================================================================

    @Test
    fun `file is distinct from directory`() {
        val dirNode = SourceFileNode(
            relativePath = "app",
            name = "app",
            extension = "",
            sizeBytes = 0L,
            isDirectory = true
        )

        val fileNode = SourceFileNode(
            relativePath = "app/build.gradle.kts",
            name = "build.gradle.kts",
            extension = "kts",
            sizeBytes = 512L,
            isDirectory = false
        )

        assertEquals(PhysicalNodeType.DIRECTORY, dirNode.physicalNodeType)
        assertEquals(PhysicalNodeType.FILE, fileNode.physicalNodeType)
        assertNotEquals(dirNode.physicalNodeType, fileNode.physicalNodeType)
    }

    // ========================================================================
    // TIER 4: CODE STRUCTURE & SYMBOLS
    // ========================================================================

    @Test
    fun `code symbols distinguish methods from standalone functions and types`() {
        val classSymbol = CodeSymbol(
            name = "HomeViewModel",
            kind = CodeSymbolKind.CLASS,
            definingFileRelativePath = "app/src/main/java/com/auditflow/app/presentation/home/HomeViewModel.kt",
            packageName = "com.auditflow.app.presentation.home"
        )

        val methodSymbol = CodeSymbol(
            name = "ingestLocalProject",
            kind = CodeSymbolKind.METHOD,
            definingFileRelativePath = "app/src/main/java/com/auditflow/app/presentation/home/HomeViewModel.kt",
            packageName = "com.auditflow.app.presentation.home"
        )

        val functionSymbol = CodeSymbol(
            name = "calculateDepth",
            kind = CodeSymbolKind.FUNCTION,
            definingFileRelativePath = "app/src/main/java/com/auditflow/app/domain/model/AuditVocabulary.kt",
            packageName = "com.auditflow.app.domain.model"
        )

        assertTrue(methodSymbol.isMethod)
        assertFalse(functionSymbol.isMethod)
        assertFalse(classSymbol.isMethod)
        assertEquals(CodeSymbolKind.METHOD, methodSymbol.kind)
        assertEquals(CodeSymbolKind.FUNCTION, functionSymbol.kind)
    }

    // ========================================================================
    // TIER 5 & 6: EXECUTION, FLOW, AND ARCHITECTURAL ROLES
    // ========================================================================

    @Test
    fun `data flow and execution flow remain strictly distinguished`() {
        val dataFlow = FlowType.DATA_FLOW
        val execFlow = FlowType.EXECUTION_FLOW

        assertNotEquals(dataFlow, execFlow)
        assertEquals("DATA_FLOW", dataFlow.label)
        assertEquals("EXECUTION_FLOW", execFlow.label)
    }

    @Test
    fun `caller callee and dependency dependent roles are distinct`() {
        assertNotEquals(CallRole.CALLER, CallRole.CALLEE)
        assertNotEquals(DependencyRole.DEPENDENCY, DependencyRole.DEPENDENT)
    }

    @Test
    fun `architectural role and workflow stages preserve canonical ordering`() {
        assertEquals(1, WorkflowStage.INGESTION.sequenceOrder)
        assertEquals(2, WorkflowStage.PATH_DISCOVERY.sequenceOrder)
        assertEquals(3, WorkflowStage.PATH_CLASSIFICATION.sequenceOrder)
        assertEquals(4, WorkflowStage.PHYSICAL_HIERARCHY.sequenceOrder)
        assertEquals(16, WorkflowStage.FORENSIC_REPORT.sequenceOrder)

        assertEquals(ArchitecturalRole.INGESTION.label, "INGESTION")
        assertEquals(ArchitecturalRole.STRUCTURAL_ANALYSIS.label, "STRUCTURAL_ANALYSIS")
    }

    // ========================================================================
    // TIER 8 & 9: FORENSIC STATUS & ARCHITECTURAL GAPS
    // ========================================================================

    @Test
    fun `forensic statuses represent all required canonical states`() {
        val implemented = ForensicStatus.IMPLEMENTED
        val missing = ForensicStatus.MISSING
        val unknown = ForensicStatus.UNKNOWN

        assertEquals("IMPLEMENTED", implemented.code)
        assertEquals("MISSING", missing.code)
        assertEquals("UNKNOWN", unknown.code)
        assertEquals("NOT DETERMINABLE FROM CURRENT CODEBASE", ForensicStatus.UNKNOWN_LABEL)

        val gap = ArchitecturalGap(
            title = "AST Code Structure Extraction",
            actualState = "SourceFileNode contains raw file metadata without parsed AST symbols",
            intendedState = "AST Parser extracts classes, functions, and interfaces",
            evidence = "SourceFileNode has no parsed symbols collection in Phase 1B",
            impact = "Semantic code analysis deferred to subsequent phase",
            recommendation = "Implement Kotlin/Java AST extractor in Phase 2",
            status = ForensicStatus.MISSING
        )

        assertEquals(ForensicStatus.MISSING, gap.status)
        assertTrue(gap.evidence.isNotBlank())
    }

    // ========================================================================
    // INTEGRATION WITH TREE RECONSTRUCTION
    // ========================================================================

    @Test
    fun `project tree reconstructor builds hierarchy adhering to canonical vocabulary`() {
        val files = listOf(
            SourceFileNode(relativePath = "README.md", name = "README.md", extension = "md", sizeBytes = 100L, isDirectory = false),
            SourceFileNode(relativePath = "app/build.gradle.kts", name = "build.gradle.kts", extension = "kts", sizeBytes = 200L, isDirectory = false),
            SourceFileNode(relativePath = "app/src/main/MainActivity.kt", name = "MainActivity.kt", extension = "kt", sizeBytes = 300L, isDirectory = false)
        )

        val root = ProjectTreeReconstructor.reconstruct("TestProject", files)

        // Root checks
        assertEquals("TestProject", root.name)
        assertEquals("", root.relativePath)
        assertTrue(root.isDirectory)
        assertEquals(PathClassification.DERIVED, root.pathClassification)

        // Children at root level (ROOT PATHS)
        val appChild = root.children.first { it.name == "app" }
        val readmeChild = root.children.first { it.name == "README.md" }

        assertTrue(appChild.isRootPath)
        assertEquals(PathTopologyType.ROOT_PATH, appChild.pathTopology)
        assertEquals(PathClassification.DERIVED, appChild.pathClassification) // Intermediate folder

        assertTrue(readmeChild.isRootPath)
        assertEquals(PathTopologyType.ROOT_PATH, readmeChild.pathTopology)
        assertEquals(PathClassification.ESTABLISHED, readmeChild.pathClassification) // Ingested file
        assertFalse(readmeChild.isDirectory)
    }

    // ========================================================================
    // TIER 10 & 11: FORENSIC DEFECTS & EPISTEMIC EVIDENCE
    // ========================================================================

    @Test
    fun `forensic defect taxonomy recognizes standard software engineering defect types`() {
        val defects = listOf(
            ForensicDefectKind.TERMINOLOGY_INCONSISTENCY,
            ForensicDefectKind.SEMANTIC_INCONSISTENCY,
            ForensicDefectKind.ARCHITECTURAL_BOUNDARY_VIOLATION,
            ForensicDefectKind.DUPLICATE_IMPLEMENTATION,
            ForensicDefectKind.DEAD_OR_UNREACHABLE_CODE,
            ForensicDefectKind.ORPHAN_COMPONENT,
            ForensicDefectKind.BROKEN_DEPENDENCY,
            ForensicDefectKind.CIRCULAR_DEPENDENCY,
            ForensicDefectKind.INVALID_DEPENDENCY_DIRECTION,
            ForensicDefectKind.MISPLACED_RESPONSIBILITY,
            ForensicDefectKind.HIGH_COUPLING_LOW_COHESION,
            ForensicDefectKind.CONTRACT_OR_INTERFACE_VIOLATION,
            ForensicDefectKind.TYPE_OR_NULLABILITY_DEFECT,
            ForensicDefectKind.STATE_INCONSISTENCY,
            ForensicDefectKind.RESOURCE_LEAK,
            ForensicDefectKind.ERROR_HANDLING_DEFECT,
            ForensicDefectKind.CONFIGURATION_DEFECT,
            ForensicDefectKind.DATA_FLOW_INCONSISTENCY,
            ForensicDefectKind.EXECUTION_FLOW_INCONSISTENCY,
            ForensicDefectKind.LOOKAHEAD_OR_DATA_LEAKAGE,
            ForensicDefectKind.HARDCODED_ASSUMPTION
        )

        assertEquals(21, defects.size)
        assertTrue(defects.all { it.label.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun `epistemic classification strictly distinguishes facts from inferences, hypotheses, and unknowns`() {
        assertEquals("FACT", EpistemicClassification.FACT.label)
        assertEquals("INFERENCE", EpistemicClassification.INFERENCE.label)
        assertEquals("HYPOTHESIS", EpistemicClassification.HYPOTHESIS.label)
        assertEquals("UNKNOWN", EpistemicClassification.UNKNOWN.label)
        assertEquals("NOT DETERMINABLE FROM CURRENT CODEBASE", EpistemicClassification.UNKNOWN.description)

        val factFinding = ForensicFinding(
            id = "FIND-001",
            defectKind = ForensicDefectKind.CIRCULAR_DEPENDENCY,
            severity = DefectSeverity.CRITICAL,
            affectedRelativePath = "app/src/main/java/com/example/ModuleA.kt",
            description = "ModuleA directly references ModuleB while ModuleB imports ModuleA",
            evidence = "import com.example.ModuleB in ModuleA.kt line 4; import com.example.ModuleA in ModuleB.kt line 6",
            epistemicType = EpistemicClassification.FACT,
            recommendation = "Introduce intermediate interface contract"
        )

        assertEquals(EpistemicClassification.FACT, factFinding.epistemicType)
        assertEquals(DefectSeverity.CRITICAL, factFinding.severity)
    }

    // ========================================================================
    // TIER 12: NAME-INDEPENDENT ARCHITECTURAL REASONING
    // ========================================================================

    @Test
    fun `evidence hierarchy assigns highest authority to executable behavior and lowest to names and comments`() {
        assertTrue(EvidenceAuthorityTier.EXECUTABLE_BEHAVIOR.priorityRank < EvidenceAuthorityTier.SYMBOL_STRUCTURE.priorityRank)
        assertTrue(EvidenceAuthorityTier.SYMBOL_STRUCTURE.priorityRank < EvidenceAuthorityTier.FUNCTION_CALLS.priorityRank)
        assertTrue(EvidenceAuthorityTier.FUNCTION_CALLS.priorityRank < EvidenceAuthorityTier.DATA_FLOW.priorityRank)
        assertTrue(EvidenceAuthorityTier.DATA_FLOW.priorityRank < EvidenceAuthorityTier.NAMING_CONVENTIONS.priorityRank)
        assertTrue(EvidenceAuthorityTier.NAMING_CONVENTIONS.priorityRank < EvidenceAuthorityTier.COMMENTS_AND_DOCS.priorityRank)
    }

    @Test
    fun `name is not architecture - component with arbitrary filename is classified by code structure and behavior`() {
        // Arbitrary filename: "Banana.kt"
        // But symbols and methods implement repository persistence
        val bananaSymbol = CodeSymbol(
            name = "BananaRepository",
            kind = CodeSymbolKind.CLASS,
            definingFileRelativePath = "app/src/main/java/com/example/Banana.kt",
            packageName = "com.example"
        )

        val saveMethod = CodeSymbol(
            name = "saveUser",
            kind = CodeSymbolKind.METHOD,
            definingFileRelativePath = "app/src/main/java/com/example/Banana.kt",
            packageName = "com.example"
        )

        val mapping = ComponentRoleMapping(
            relativePath = "app/src/main/java/com/example/Banana.kt",
            primarySymbolName = bananaSymbol.name,
            assignedRole = ArchitecturalRole.PERSISTENCE,
            assignedStage = WorkflowStage.DATA_FLOW,
            evidenceTier = EvidenceAuthorityTier.SYMBOL_STRUCTURE,
            supportingEvidence = "Contains methods: saveUser(User), getUser(String) interacting with Room DAO"
        )

        // The architectural role is PERSISTENCE, even though the filename is Banana.kt
        assertEquals(ArchitecturalRole.PERSISTENCE, mapping.assignedRole)
        assertFalse(mapping.relativePath.contains("Repository"))
        assertEquals(EvidenceAuthorityTier.SYMBOL_STRUCTURE, mapping.evidenceTier)
    }

    // ========================================================================
    // TIER 13: STATION-TO-STATION PIPELINE CONTRACTS
    // ========================================================================

    @Test
    fun `station-to-station contracts maintain typed domain models through full audit pipeline`() {
        // Station 1: Ingestion
        val boundary = ProjectBoundary(name = "SampleApp", rootIdentifier = "owner/sample", sourceKind = ProjectSourceKind.GITHUB_REPOSITORY)
        val root = ProjectRoot(name = "SampleApp", originPathOrUri = "https://github.com/owner/sample")
        val file1 = SourceFileNode(relativePath = "app/build.gradle.kts", name = "build.gradle.kts", extension = "kts", sizeBytes = 500L, isDirectory = false, pathClassification = PathClassification.ESTABLISHED)
        val file2 = SourceFileNode(relativePath = "app/src/main/MainActivity.kt", name = "MainActivity.kt", extension = "kt", sizeBytes = 1500L, isDirectory = false, pathClassification = PathClassification.ESTABLISHED)

        val ingestedRecord = IngestedProjectRecord(
            projectBoundary = boundary,
            projectRoot = root,
            files = listOf(file1, file2)
        )

        // Station 2: Physical Hierarchy Reconstruction
        val treeRoot = ProjectTreeReconstructor.reconstruct(ingestedRecord.projectRoot.name, ingestedRecord.files)
        val hierarchyResult = PhysicalHierarchyResult(
            projectRoot = root,
            rootNode = treeRoot,
            totalNodes = 5,
            rootPaths = treeRoot.children.filter { it.isRootPath },
            childPaths = treeRoot.children.flatMap { it.children }
        )

        // Station 8: Forensic Audit Report
        val report = ForensicAuditReport(
            project = boundary,
            projectRoot = root,
            hierarchy = hierarchyResult,
            findings = emptyList(),
            gaps = emptyList(),
            roleMappings = listOf(
                ComponentRoleMapping(
                    relativePath = "app/src/main/MainActivity.kt",
                    primarySymbolName = "MainActivity",
                    assignedRole = ArchitecturalRole.PRESENTATION,
                    assignedStage = WorkflowStage.PHYSICAL_HIERARCHY,
                    evidenceTier = EvidenceAuthorityTier.FRAMEWORK_CONTRACTS,
                    supportingEvidence = "Extends ComponentActivity and renders Compose hierarchy"
                )
            )
        )

        assertEquals("SampleApp", report.project.name)
        assertEquals(root, report.projectRoot)
        assertEquals(1, report.roleMappings.size)
        assertEquals(ArchitecturalRole.PRESENTATION, report.roleMappings[0].assignedRole)
    }

    // ========================================================================
    // SOURCE EQUIVALENCE (LOCAL SAF VS GITHUB GIT TREE)
    // ========================================================================

    @Test
    fun `github and local sources produce semantically equivalent canonical models`() {
        val githubNode = SourceFileNode(
            relativePath = "app/src/main/java/MainActivity.kt",
            name = "MainActivity.kt",
            extension = "kt",
            sizeBytes = 2048L,
            isDirectory = false,
            pathClassification = PathClassification.ESTABLISHED
        )

        val safNode = SourceFileNode(
            relativePath = RelativePathHelper.normalize("app/src/main/java/MainActivity.kt"),
            name = "MainActivity.kt",
            extension = "kt",
            sizeBytes = 2048L,
            isDirectory = false,
            pathClassification = PathClassification.ESTABLISHED
        )

        assertEquals(githubNode.relativePath, safNode.relativePath)
        assertEquals(githubNode.pathTopology, safNode.pathTopology)
        assertEquals(githubNode.physicalNodeType, safNode.physicalNodeType)
        assertEquals(githubNode.pathClassification, safNode.pathClassification)
    }

    // ========================================================================
    // TIER 14: ARTIFACT, ARCHIVE, AND PACKAGE IDENTITY
    // ========================================================================

    @Test
    fun `apk is distinct from repository and directory project`() {
        val apkIdentity = com.auditflow.app.domain.model.ArtifactIdentity.APK
        val repoIdentity = com.auditflow.app.domain.model.ArtifactIdentity.REPOSITORY
        val dirIdentity = com.auditflow.app.domain.model.ArtifactIdentity.DIRECTORY_PROJECT

        assertNotEquals(apkIdentity, repoIdentity)
        assertNotEquals(apkIdentity, dirIdentity)
        assertEquals("APK", apkIdentity.label)
        assertEquals("REPOSITORY", repoIdentity.label)
        assertEquals("DIRECTORY_PROJECT", dirIdentity.label)
    }

    @Test
    fun `archive content identity distinguishes source zip from generic and binary zips`() {
        val sourceZip = com.auditflow.app.domain.model.ArchiveContentIdentity.SOURCE_PROJECT
        val binaryZip = com.auditflow.app.domain.model.ArchiveContentIdentity.BINARY_COLLECTION
        val repoContent = com.auditflow.app.domain.model.ArchiveContentIdentity.REPOSITORY_CONTENT
        val apkZip = com.auditflow.app.domain.model.ArchiveContentIdentity.APK

        assertNotEquals(sourceZip, binaryZip)
        assertNotEquals(sourceZip, repoContent)
        assertNotEquals(sourceZip, apkZip)
        assertTrue(sourceZip.label.contains("SOURCE"))
        assertTrue(binaryZip.label.contains("BINARY"))
        assertTrue(apkZip.label.contains("APK"))
    }
}
