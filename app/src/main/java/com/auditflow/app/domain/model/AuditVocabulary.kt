package com.auditflow.app.domain.model

/**
 * ============================================================================
 * AUDITFLOW CANONICAL UNIVERSAL AUDIT VOCABULARY
 * ============================================================================
 *
 * System-wide semantic definitions authority.
 *
 * All AuditFlow components (ingestion, reconstruction, code inspection,
 * call-graph mapping, data-flow analysis, and reporting) must reference
 * this single authoritative vocabulary contract.
 *
 * CORE PRINCIPLES:
 * - PROJECT ROOT ≠ ROOT PATH
 * - ESTABLISHED RELATIVE PATH ≠ DERIVED RELATIVE PATH
 * - PHYSICAL HIERARCHY ≠ CODE STRUCTURE
 * - FILE ≠ FUNCTION
 * - FUNCTION ≠ METHOD (METHOD ⊂ FUNCTION)
 * - CALLER ≠ CALLEE
 * - DEPENDENCY ≠ DEPENDENT
 * - DATA FLOW ≠ EXECUTION FLOW
 * - ARCHITECTURAL ROLE ≠ WORKFLOW STAGE
 * - ACTUAL ARCHITECTURE ≠ INTENDED ARCHITECTURE
 * - COMPILES ≠ CORRECT
 * - EXISTS ≠ IMPLEMENTED
 * - IMPLEMENTED ≠ CONNECTED
 * - CONNECTED ≠ VERIFIED
 */

// ============================================================================
// TIER 1 — PHYSICAL PROJECT BOUNDARIES
// ============================================================================

/**
 * Canonical model representing the audited PROJECT.
 *
 * A Project is the highest logical boundary representing the complete
 * audited software repository or source collection.
 */
data class ProjectBoundary(
    val name: String,
    val rootIdentifier: String,
    val sourceKind: ProjectSourceKind
)

/**
 * Canonical model representing the PROJECT ROOT.
 *
 * The physical origin point of the Project.
 * - has no parent within the project
 * - is NOT a file
 * - is NOT a relative path
 * - is the origin from which relative paths are evaluated
 */
data class ProjectRoot(
    val name: String,
    val originPathOrUri: String
) {
    init {
        require(name.isNotBlank()) { "Project Root name must not be blank" }
    }
}

// ============================================================================
// TIER 2 — PATH TOPOLOGY
// ============================================================================

/**
 * Path origin classification distinguishing source-provided paths from reconstructed paths.
 */
enum class PathClassification(val label: String, val description: String) {
    /**
     * An established relative path explicitly supplied by the ingestion source
     * (e.g. GitHub Git Tree API, SAF Cursor).
     */
    ESTABLISHED("ESTABLISHED", "Path explicitly provided by the physical ingestion source"),

    /**
     * A derived relative path reconstructed by AuditFlow to represent intermediate
     * directory containers necessary for hierarchy representation.
     * Derived paths MUST NEVER be represented as explicitly ingested paths.
     */
    DERIVED("DERIVED", "Path reconstructed by AuditFlow from descendant paths")
}

/**
 * Relative path hierarchy position topology.
 */
enum class PathTopologyType(val label: String, val description: String) {
    /**
     * A relative path whose immediate parent is the Project Root (Depth = 1).
     * Examples: "app", "README.md", "build.gradle.kts".
     */
    ROOT_PATH("ROOT_PATH", "Direct child path of the Project Root (depth = 1)"),

    /**
     * A relative path whose immediate parent is another relative path within the project (Depth >= 2).
     * Examples: "app/src", "app/src/main/MainActivity.kt".
     */
    CHILD_PATH("CHILD_PATH", "Descendant child path beneath another relative path")
}

/**
 * Canonical utility and value helpers for Relative Path operations.
 */
object RelativePathHelper {
    const val PATH_SEPARATOR = "/"

    /**
     * Normalizes a raw path string to canonical POSIX relative path form:
     * - Replaces backslashes with forward slashes
     * - Trims leading and trailing slashes
     * - Removes blank and redundant "." segments
     */
    fun normalize(rawPath: String): String {
        return rawPath
            .replace('\\', '/')
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .joinToString(PATH_SEPARATOR)
    }

    /**
     * Decomposes a normalized relative path into individual [PATH SEGMENT] elements.
     */
    fun extractSegments(relativePath: String): List<String> {
        val normalized = normalize(relativePath)
        return if (normalized.isBlank()) emptyList() else normalized.split(PATH_SEPARATOR)
    }

    /**
     * Calculates the path depth relative to Project Root.
     * Depth 1 = ROOT PATH, Depth >= 2 = CHILD PATH.
     */
    fun calculateDepth(relativePath: String): Int {
        return extractSegments(relativePath).size
    }

    /**
     * Determines whether a relative path is a ROOT PATH (depth = 1).
     */
    fun isRootPath(relativePath: String): Boolean {
        return calculateDepth(relativePath) == 1
    }

    /**
     * Determines the parent relative path of a given relative path.
     * Returns empty string if the parent is the Project Root.
     */
    fun getParentRelativePath(relativePath: String): String {
        val segments = extractSegments(relativePath)
        return if (segments.size <= 1) "" else segments.dropLast(1).joinToString(PATH_SEPARATOR)
    }

    /**
     * Classifies path topology based on relative path depth.
     */
    fun classifyTopology(relativePath: String): PathTopologyType {
        val depth = calculateDepth(relativePath)
        return if (depth <= 1) PathTopologyType.ROOT_PATH else PathTopologyType.CHILD_PATH
    }
}

// ============================================================================
// TIER 3 — PHYSICAL HIERARCHY
// ============================================================================

/**
 * Physical node classification.
 */
enum class PhysicalNodeType(val label: String, val description: String) {
    /**
     * Non-terminal physical container capable of containing child paths.
     */
    DIRECTORY("DIRECTORY", "Non-terminal physical container node"),

    /**
     * Terminal physical source node. Does NOT contain filesystem children.
     */
    FILE("FILE", "Terminal physical source file node")
}

// ============================================================================
// TIER 4 — CODE STRUCTURE & LOGICAL SYMBOLS
// ============================================================================

/**
 * Semantic file types distinguishing physical terminal files by their language and format.
 */
enum class SemanticFileType(val label: String, val isSourceCode: Boolean) {
    KOTLIN_SOURCE("KOTLIN_SOURCE", isSourceCode = true),
    JAVA_SOURCE("JAVA_SOURCE", isSourceCode = true),
    GRADLE_KOTLIN_DSL("GRADLE_KOTLIN_DSL", isSourceCode = true),
    GRADLE_GROOVY_DSL("GRADLE_GROOVY_DSL", isSourceCode = true),
    ANDROID_MANIFEST("ANDROID_MANIFEST", isSourceCode = false),
    XML_RESOURCE_OR_LAYOUT("XML_RESOURCE_OR_LAYOUT", isSourceCode = false),
    PROGUARD_R8_RULES("PROGUARD_R8_RULES", isSourceCode = false),
    PROPERTIES("PROPERTIES", isSourceCode = false),
    JSON("JSON", isSourceCode = false),
    YAML("YAML", isSourceCode = false),
    MARKDOWN("MARKDOWN", isSourceCode = false),
    TEXT("TEXT", isSourceCode = false),
    BINARY_OR_IMAGE("BINARY_OR_IMAGE", isSourceCode = false),
    BYTECODE_ARCHIVE("BYTECODE_ARCHIVE", isSourceCode = false),
    UNKNOWN("UNKNOWN", isSourceCode = false);

    companion object {
        fun fromFileNameOrPath(relativePath: String): SemanticFileType {
            val fileName = relativePath.substringAfterLast('/').lowercase()
            return when {
                fileName == "androidmanifest.xml" -> ANDROID_MANIFEST
                fileName.endsWith(".gradle.kts") -> GRADLE_KOTLIN_DSL
                fileName.endsWith(".gradle") -> GRADLE_GROOVY_DSL
                fileName.endsWith(".kt") -> KOTLIN_SOURCE
                fileName.endsWith(".java") -> JAVA_SOURCE
                fileName.endsWith(".xml") -> XML_RESOURCE_OR_LAYOUT
                fileName.endsWith(".pro") || fileName == "proguard-rules.pro" -> PROGUARD_R8_RULES
                fileName.endsWith(".properties") -> PROPERTIES
                fileName.endsWith(".json") -> JSON
                fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> YAML
                fileName.endsWith(".md") -> MARKDOWN
                fileName.endsWith(".txt") -> TEXT
                fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".webp") || fileName.endsWith(".ico") || fileName.endsWith(".svg") ||
                        fileName.endsWith(".so") -> BINARY_OR_IMAGE
                fileName.endsWith(".jar") || fileName.endsWith(".aar") || fileName.endsWith(".apk") || fileName.endsWith(".class") -> BYTECODE_ARCHIVE
                else -> UNKNOWN
            }
        }
    }
}

/**
 * State of physical file content retrieval and availability.
 */
enum class ContentAvailabilityState(val label: String, val isAvailable: Boolean) {
    AVAILABLE("AVAILABLE", isAvailable = true),
    UNAVAILABLE_NOT_FETCHED("UNAVAILABLE_NOT_FETCHED", isAvailable = false),
    UNAVAILABLE_EMPTY("UNAVAILABLE_EMPTY", isAvailable = false),
    UNAVAILABLE_BINARY("UNAVAILABLE_BINARY", isAvailable = false),
    UNAVAILABLE_PERMISSION_DENIED("UNAVAILABLE_PERMISSION_DENIED", isAvailable = false),
    UNAVAILABLE_RETRIEVAL_ERROR("UNAVAILABLE_RETRIEVAL_ERROR", isAvailable = false)
}

/**
 * Parsing and structural extraction outcome status.
 */
enum class ParsingStatus(val label: String) {
    PARSED_SUCCESS("PARSED_SUCCESS"),
    PARSED_PARTIAL("PARSED_PARTIAL"),
    SKIPPED_NON_SOURCE("SKIPPED_NON_SOURCE"),
    UNPARSEABLE_SYNTAX_ERROR("UNPARSEABLE_SYNTAX_ERROR"),
    UNAVAILABLE_CONTENT("UNAVAILABLE_CONTENT")
}

/**
 * Model representing an import declaration in source code.
 */
data class ImportDeclaration(
    val importPath: String,
    val importedSymbolName: String,
    val isWildcard: Boolean,
    val alias: String? = null,
    val lineNumber: Int = 0
)

/**
 * Model representing a parameter inside a function or constructor declaration.
 */
data class ParameterSymbol(
    val name: String,
    val type: String,
    val hasDefaultValue: Boolean = false,
    val annotations: List<String> = emptyList()
)

/**
 * Types of logical symbols discovered inside a physical File.
 */
enum class CodeSymbolKind(val label: String) {
    PACKAGE("PACKAGE"),
    IMPORT("IMPORT"),
    CLASS("CLASS"),
    INTERFACE("INTERFACE"),
    OBJECT("OBJECT"),
    ENUM("ENUM"),
    DATA_CLASS("DATA_CLASS"),
    SEALED_CLASS("SEALED_CLASS"),
    FUNCTION("FUNCTION"),
    METHOD("METHOD"),
    CONSTRUCTOR("CONSTRUCTOR"),
    PROPERTY("PROPERTY"),
    CONSTANT("CONSTANT"),
    ANNOTATION("ANNOTATION"),
    TYPE_ALIAS("TYPE_ALIAS"),
    COMPOSABLE("COMPOSABLE")
}

/**
 * Model representing an invocation/call expression discovered within a function or block body.
 */
data class CallInvocation(
    val targetName: String,
    val receiverName: String? = null,
    val lineNumber: Int = 0,
    val rawExpression: String = ""
)

/**
 * Logical code symbol model associated with its defining File.
 */
data class CodeSymbol(
    val name: String,
    val kind: CodeSymbolKind,
    val definingFileRelativePath: String,
    val packageName: String = "",
    val visibility: String = "public",
    val isMethod: Boolean = (kind == CodeSymbolKind.METHOD),
    val isSuspend: Boolean = false,
    val isComposable: Boolean = false,
    val isOverride: Boolean = false,
    val parameters: List<ParameterSymbol> = emptyList(),
    val returnType: String? = null,
    val containingSymbolName: String? = null,
    val startLine: Int = 0,
    val endLine: Int = 0,
    val annotations: List<String> = emptyList(),
    val modifiers: List<String> = emptyList(),
    val childSymbols: List<CodeSymbol> = emptyList(),
    val superTypes: List<String> = emptyList(),
    val invocations: List<CallInvocation> = emptyList()
) {
    val fullyQualifiedName: String
        get() = buildFqn(packageName, containingSymbolName, name)

    companion object {
        fun buildFqn(pkg: String, container: String?, name: String): String {
            val prefix = if (pkg.isNotBlank()) "$pkg." else ""
            val owner = if (!container.isNullOrBlank()) "$container." else ""
            return "$prefix$owner$name"
        }
    }
}

/**
 * Canonical model for Station 3 (File Content & Code Structure Inspection Result).
 */
data class FileInspectionResult(
    val relativePath: String,
    val physicalNodeType: PhysicalNodeType,
    val pathClassification: PathClassification,
    val semanticFileType: SemanticFileType,
    val byteSize: Long,
    val contentAvailability: ContentAvailabilityState,
    val contentSha256: String? = null,
    val declaredPackage: String? = null,
    val packageDiscrepancy: Boolean = false,
    val imports: List<ImportDeclaration> = emptyList(),
    val fileAnnotations: List<String> = emptyList(),
    val topLevelSymbols: List<CodeSymbol> = emptyList(),
    val allSymbols: List<CodeSymbol> = emptyList(),
    val parsingStatus: ParsingStatus,
    val parsingErrors: List<String> = emptyList(),
    val linesOfCode: Int = 0
)

// ============================================================================
// TIER 5 — EXECUTION & RELATIONSHIPS
// ============================================================================

/**
 * Canonical resolution status for symbols, imports, and cross-file references.
 */
enum class ResolutionStatus(val label: String, val description: String) {
    RESOLVED("RESOLVED", "Symbol or relationship definitively resolved to local target"),
    PARTIALLY_RESOLVED("PARTIALLY_RESOLVED", "Target identified but signature or overload resolution is incomplete"),
    AMBIGUOUS("AMBIGUOUS", "Multiple candidate targets match the referenced identifier"),
    EXTERNAL("EXTERNAL", "Target belongs to an external library, SDK, or framework"),
    UNRESOLVED("UNRESOLVED", "Referenced identifier cannot be found in local project or known external APIs"),
    UNKNOWN("UNKNOWN", "Resolution status not determinable from available evidence")
}

/**
 * Kind of structural or logical dependency relationship.
 */
enum class DependencyKind(val label: String) {
    IMPORT("IMPORT"),
    TYPE_USAGE("TYPE_USAGE"),
    CONSTRUCTOR_PARAM("CONSTRUCTOR_PARAM"),
    INHERITANCE("INHERITANCE"),
    IMPLEMENTATION("IMPLEMENTATION"),
    ANNOTATION("ANNOTATION"),
    FUNCTION_CALL("FUNCTION_CALL"),
    PROPERTY_ACCESS("PROPERTY_ACCESS")
}

/**
 * Explicit reference from one source location to a named symbol.
 */
data class SymbolReference(
    val referencingFileRelativePath: String,
    val referencedSymbolName: String,
    val referencedFqn: String? = null,
    val containingSymbolName: String? = null,
    val lineNumber: Int = 0,
    val resolutionStatus: ResolutionStatus = ResolutionStatus.UNKNOWN,
    val resolvedTargetFqn: String? = null,
    val resolvedTargetFileRelativePath: String? = null,
    val isExternal: Boolean = false,
    val evidence: String = ""
)

/**
 * Directed dependency edge between files, symbols, or external libraries.
 */
data class DependencyEdge(
    val sourceFileRelativePath: String,
    val sourceSymbolFqn: String? = null,
    val targetFileRelativePath: String? = null,
    val targetSymbolFqn: String,
    val dependencyKind: DependencyKind = DependencyKind.IMPORT,
    val role: DependencyRole = DependencyRole.DEPENDENCY,
    val resolutionStatus: ResolutionStatus = ResolutionStatus.RESOLVED,
    val isExternal: Boolean = false,
    val lineNumber: Int = 0,
    val evidence: String = ""
)

/**
 * Directed inheritance edge (subclass -> superclass).
 */
data class InheritanceEdge(
    val subTypeFqn: String,
    val subTypeFileRelativePath: String,
    val superTypeFqn: String,
    val superTypeFileRelativePath: String? = null,
    val isInterfaceImplementation: Boolean = false,
    val resolutionStatus: ResolutionStatus = ResolutionStatus.RESOLVED,
    val isExternal: Boolean = false,
    val lineNumber: Int = 0,
    val evidence: String = ""
)

/**
 * Directed implementation edge (implementor class -> interface).
 */
data class ImplementationEdge(
    val implementingTypeFqn: String,
    val implementingFileRelativePath: String,
    val interfaceTypeFqn: String,
    val interfaceFileRelativePath: String? = null,
    val resolutionStatus: ResolutionStatus = ResolutionStatus.RESOLVED,
    val isExternal: Boolean = false,
    val lineNumber: Int = 0,
    val evidence: String = ""
)

/**
 * Component relationship role in call topology.
 */
enum class CallRole {
    CALLER,
    CALLEE
}

/**
 * Component relationship role in dependency topology.
 */
enum class DependencyRole {
    DEPENDENCY,
    DEPENDENT
}

/**
 * Flow type distinguishing data transformations from invocation sequencing.
 */
enum class FlowType(val label: String, val description: String) {
    DATA_FLOW("DATA_FLOW", "Movement and transformation of information through components"),
    EXECUTION_FLOW("EXECUTION_FLOW", "Ordered invocation sequence of executable operations")
}

// ============================================================================
// TIER 6 — ARCHITECTURAL CLASSIFICATION
// ============================================================================

/**
 * Canonical Architectural Roles describing WHY a component exists.
 */
enum class ArchitecturalRole(val label: String, val description: String) {
    INGESTION("INGESTION", "Discovers and ingests physical files or remote repositories"),
    DATA_VALIDATION("DATA_VALIDATION", "Validates input schemas, paths, and raw payloads"),
    STRUCTURAL_ANALYSIS("STRUCTURAL_ANALYSIS", "Reconstructs physical topology and parent-child hierarchies"),
    SEMANTIC_ANALYSIS("SEMANTIC_ANALYSIS", "Extracts logical code symbols, AST structures, and behaviors"),
    PERSISTENCE("PERSISTENCE", "Manages durable preferences and state storage"),
    DOMAIN_LOGIC("DOMAIN_LOGIC", "Coordinates domain models and business rules"),
    RISK_CONTROL("RISK_CONTROL", "Enforces verification invariants and compliance checks"),
    EXECUTION("EXECUTION", "Executes background analysis jobs and coroutine pipelines"),
    PRESENTATION("PRESENTATION", "Renders user interfaces and visual trees"),
    AI_INTELLIGENCE("AI_INTELLIGENCE", "Analyzes system architecture and produces audit insights")
}

/**
 * Canonical Workflow Stages describing WHERE a component participates in the operational lifecycle.
 */
enum class WorkflowStage(val sequenceOrder: Int, val label: String) {
    INGESTION(1, "INGESTION"),
    PATH_DISCOVERY(2, "PATH_DISCOVERY"),
    PATH_CLASSIFICATION(3, "PATH_CLASSIFICATION"),
    PHYSICAL_HIERARCHY(4, "PHYSICAL_HIERARCHY"),
    FILE_INSPECTION(5, "FILE_INSPECTION"),
    CODE_STRUCTURE(6, "CODE_STRUCTURE"),
    SYMBOL_EXTRACTION(7, "SYMBOL_EXTRACTION"),
    FUNCTION_ANALYSIS(8, "FUNCTION_ANALYSIS"),
    RELATIONSHIP_ANALYSIS(9, "RELATIONSHIP_ANALYSIS"),
    CALL_GRAPH(10, "CALL_GRAPH"),
    DEPENDENCY_GRAPH(11, "DEPENDENCY_GRAPH"),
    DATA_FLOW(12, "DATA_FLOW"),
    EXECUTION_FLOW(13, "EXECUTION_FLOW"),
    ARCHITECTURAL_RECONSTRUCTION(14, "ARCHITECTURAL_RECONSTRUCTION"),
    ACTUAL_VS_INTENDED_COMPARISON(15, "ACTUAL_VS_INTENDED_COMPARISON"),
    FORENSIC_REPORT(16, "FORENSIC_REPORT")
}

// ============================================================================
// TIER 7 — ANALYSIS TYPES
// ============================================================================

/**
 * Category of analysis performed.
 */
enum class AnalysisType(val label: String, val description: String) {
    STRUCTURAL_ANALYSIS("STRUCTURAL_ANALYSIS", "Analyzes how the project is physically and logically constructed"),
    SEMANTIC_ANALYSIS("SEMANTIC_ANALYSIS", "Analyzes what the implementation actually means and does"),
    FORENSIC_AUDIT("FORENSIC_AUDIT", "Evidence-based verification distinguishing facts from assumptions")
}

// ============================================================================
// TIER 8 — FORENSIC STATUS
// ============================================================================

/**
 * Canonical forensic status indicators for components, functions, and features.
 */
enum class ForensicStatus(val code: String, val description: String) {
    IMPLEMENTED("IMPLEMENTED", "Executable and connected functionality exists"),
    PARTIAL("PARTIAL", "Functionality exists but is incomplete"),
    STUB("STUB", "Placeholder or non-functional implementation"),
    UNWIRED("UNWIRED", "Implementation exists but has no confirmed execution connection"),
    ORPHAN("ORPHAN", "Implemented component with no confirmed caller or consumer"),
    BROKEN("BROKEN", "Expected functionality cannot execute because of a confirmed defect"),
    MISSING("MISSING", "Required capability cannot be found in the inspected project"),
    DUPLICATE("DUPLICATE", "Multiple implementations represent substantially the same responsibility"),
    UNKNOWN("UNKNOWN", "NOT DETERMINABLE FROM CURRENT CODEBASE");

    companion object {
        const val UNKNOWN_LABEL = "NOT DETERMINABLE FROM CURRENT CODEBASE"
    }
}

// ============================================================================
// TIER 9 — ARCHITECTURE COMPARISON
// ============================================================================

/**
 * Architecture perspective classification.
 */
enum class ArchitecturePerspective(val label: String, val description: String) {
    ACTUAL_ARCHITECTURE("ACTUAL_ARCHITECTURE", "Architecture demonstrated by executable code, calls, and data flows"),
    INTENDED_ARCHITECTURE("INTENDED_ARCHITECTURE", "Architecture specified by requirements, designs, or target specs")
}

/**
 * Model representing an identified Architectural Gap between Actual and Intended architecture.
 */
data class ArchitecturalGap(
    val title: String,
    val actualState: String,
    val intendedState: String,
    val evidence: String,
    val impact: String,
    val recommendation: String,
    val status: ForensicStatus = ForensicStatus.MISSING
)

// ============================================================================
// TIER 10 — FORENSIC DEFECT & INCONSISTENCY TAXONOMY
// ============================================================================

/**
 * Recognized software-engineering defect and inconsistency classifications.
 */
enum class ForensicDefectKind(val label: String, val description: String) {
    TERMINOLOGY_INCONSISTENCY("TERMINOLOGY_INCONSISTENCY", "Conflicting or overloaded terms used across layers"),
    SEMANTIC_INCONSISTENCY("SEMANTIC_INCONSISTENCY", "Implementation behavior differs from its semantic contract"),
    ARCHITECTURAL_BOUNDARY_VIOLATION("ARCHITECTURAL_BOUNDARY_VIOLATION", "Layer boundary or separation of concerns violated"),
    DUPLICATE_IMPLEMENTATION("DUPLICATE_IMPLEMENTATION", "Redundant implementations representing the same responsibility"),
    DEAD_OR_UNREACHABLE_CODE("DEAD_OR_UNREACHABLE_CODE", "Code constructs with no active callers or execution paths"),
    ORPHAN_COMPONENT("ORPHAN_COMPONENT", "Implemented component with no incoming reference or consumer"),
    BROKEN_DEPENDENCY("BROKEN_DEPENDENCY", "Required dependency missing, unresolvable, or broken"),
    CIRCULAR_DEPENDENCY("CIRCULAR_DEPENDENCY", "Circular reference loop between components or modules"),
    INVALID_DEPENDENCY_DIRECTION("INVALID_DEPENDENCY_DIRECTION", "Dependency points from core domain to external framework"),
    MISPLACED_RESPONSIBILITY("MISPLACED_RESPONSIBILITY", "Logic implemented in an inappropriate architectural layer"),
    HIGH_COUPLING_LOW_COHESION("HIGH_COUPLING_LOW_COHESION", "Excessive cross-component coupling with low internal focus"),
    CONTRACT_OR_INTERFACE_VIOLATION("CONTRACT_OR_INTERFACE_VIOLATION", "Component breaks expected interface or contract"),
    TYPE_OR_NULLABILITY_DEFECT("TYPE_OR_NULLABILITY_DEFECT", "Unsafe null handling or type mismatch"),
    STATE_INCONSISTENCY("STATE_INCONSISTENCY", "Corrupt, unsynchronized, or contradictory state mutations"),
    RESOURCE_LEAK("RESOURCE_LEAK", "Unclosed streams, connections, or unreleased system resources"),
    ERROR_HANDLING_DEFECT("ERROR_HANDLING_DEFECT", "Swallowed exceptions, missing recovery, or silent failures"),
    CONFIGURATION_DEFECT("CONFIGURATION_DEFECT", "Defect in build script, manifest, or runtime configuration"),
    DATA_FLOW_INCONSISTENCY("DATA_FLOW_INCONSISTENCY", "Data transformed incorrectly or dropped across pipeline"),
    EXECUTION_FLOW_INCONSISTENCY("EXECUTION_FLOW_INCONSISTENCY", "Operations invoked in an invalid sequence or race condition"),
    LOOKAHEAD_OR_DATA_LEAKAGE("LOOKAHEAD_OR_DATA_LEAKAGE", "Temporal leakage or evaluation contamination"),
    HARDCODED_ASSUMPTION("HARDCODED_ASSUMPTION", "Magic values or unverified environment assumptions")
}

/**
 * Severity level of an identified forensic finding.
 */
enum class DefectSeverity {
    CRITICAL,
    MAJOR,
    MINOR,
    INFO
}

/**
 * Canonical model for a verified Forensic Finding.
 */
data class ForensicFinding(
    val id: String,
    val defectKind: ForensicDefectKind,
    val severity: DefectSeverity,
    val affectedRelativePath: String,
    val description: String,
    val evidence: String,
    val epistemicType: EpistemicClassification,
    val recommendation: String
)

// ============================================================================
// TIER 11 — EPISTEMIC EVIDENCE CLASSIFICATION
// ============================================================================

/**
 * Epistemic classification distinguishing direct physical facts from derived inferences and unknowns.
 */
enum class EpistemicClassification(val label: String, val description: String) {
    /**
     * Observed directly from code, configuration, filesystem, or execution evidence.
     */
    FACT("FACT", "Directly observed from code, AST, filesystem, or execution evidence"),

    /**
     * Logical conclusion derived from multiple observed facts.
     */
    INFERENCE("INFERENCE", "Logical conclusion synthesized from multiple observed facts"),

    /**
     * Possible explanation not yet proven by repository evidence.
     */
    HYPOTHESIS("HYPOTHESIS", "Possible explanation requiring further code inspection"),

    /**
     * Cannot be established from available codebase evidence.
     */
    UNKNOWN("UNKNOWN", "NOT DETERMINABLE FROM CURRENT CODEBASE")
}

// ============================================================================
// TIER 12 — NAME-INDEPENDENT EVIDENCE HIERARCHY
// ============================================================================

/**
 * Order of authority for inferring architectural meaning.
 * Filenames and comments provide clues but NEVER override code structure,
 * call graphs, or data flow evidence.
 */
enum class EvidenceAuthorityTier(val priorityRank: Int, val description: String) {
    EXECUTABLE_BEHAVIOR(1, "Actual runtime execution, operations, and state mutations (Highest)"),
    SYMBOL_STRUCTURE(2, "Class, interface, function, and property AST declarations"),
    FUNCTION_CALLS(3, "Actual caller and callee graph invocations"),
    DATA_FLOW(4, "Movement, parameters, and transformations of data"),
    DEPENDENCY_GRAPH(5, "Injected and imported service dependencies"),
    FRAMEWORK_CONTRACTS(6, "Implemented platform interfaces (e.g. ViewModel, Activity, Repository)"),
    ANNOTATIONS(7, "Language and framework metadata annotations"),
    PACKAGE_AND_LOCATION(8, "Physical filesystem path and package hierarchy"),
    NAMING_CONVENTIONS(9, "Component and filename naming patterns (Clue only, not truth)"),
    COMMENTS_AND_DOCS(10, "Source code comments and documentation (Lowest authority)")
}

// ============================================================================
// TIER 13 — STATION-TO-STATION PIPELINE CONTRACTS
// ============================================================================

/**
 * Station 1 Ingestion Output.
 */
data class IngestedProjectRecord(
    val projectBoundary: ProjectBoundary,
    val projectRoot: ProjectRoot,
    val files: List<SourceFileNode>
)

/**
 * Station 2 Physical Hierarchy Output.
 */
data class PhysicalHierarchyResult(
    val projectRoot: ProjectRoot,
    val rootNode: ProjectTreeNode,
    val totalNodes: Int,
    val rootPaths: List<ProjectTreeNode>,
    val childPaths: List<ProjectTreeNode>
)

/**
 * Graph call edge connecting a caller to a callee.
 */
data class CallEdge(
    val callerSymbol: String,
    val callerFileRelativePath: String,
    val calleeSymbol: String,
    val calleeFileRelativePath: String,
    val callerFqn: String? = null,
    val calleeFqn: String? = null,
    val lineNumber: Int = 0,
    val role: CallRole = CallRole.CALLER,
    val resolutionStatus: ResolutionStatus = ResolutionStatus.RESOLVED,
    val isExternal: Boolean = false,
    val evidence: String = ""
)

/**
 * Summary metrics and counts for Station 4 Symbol Resolution.
 */
data class ResolutionSummary(
    val totalFilesInspected: Int = 0,
    val totalSymbolsIndexed: Int = 0,
    val totalDependencies: Int = 0,
    val resolvedDependencies: Int = 0,
    val externalDependencies: Int = 0,
    val unresolvedDependencies: Int = 0,
    val totalCalls: Int = 0,
    val resolvedCalls: Int = 0,
    val externalCalls: Int = 0,
    val unresolvedCalls: Int = 0,
    val totalInheritances: Int = 0,
    val totalImplementations: Int = 0,
    val totalDefectsFound: Int = 0
)

/**
 * Station 4 Symbol Resolution & Cross-File Relationship Mapping Output.
 */
data class Station4ResolutionResult(
    val dependencies: List<DependencyEdge> = emptyList(),
    val calls: List<CallEdge> = emptyList(),
    val inheritances: List<InheritanceEdge> = emptyList(),
    val implementations: List<ImplementationEdge> = emptyList(),
    val references: List<SymbolReference> = emptyList(),
    val findings: List<ForensicFinding> = emptyList(),
    val summary: ResolutionSummary = ResolutionSummary()
)

/**
 * Graph data-flow edge connecting an input source to a destination.
 */
data class DataFlowEdge(
    val sourceSymbol: String,
    val destinationSymbol: String,
    val payloadType: String
)

/**
 * Graph execution-flow edge connecting sequentially executed operations.
 */
data class ExecutionFlowEdge(
    val stepOrder: Int,
    val fromOperation: String,
    val toOperation: String
)

/**
 * Component mapping associating a source file to an evidence-backed architectural role.
 */
data class ComponentRoleMapping(
    val relativePath: String,
    val primarySymbolName: String,
    val assignedRole: ArchitecturalRole,
    val assignedStage: WorkflowStage,
    val evidenceTier: EvidenceAuthorityTier,
    val supportingEvidence: String
)

/**
 * Final Forensic Audit Report combining all pipeline station findings.
 */
data class ForensicAuditReport(
    val project: ProjectBoundary,
    val projectRoot: ProjectRoot,
    val hierarchy: PhysicalHierarchyResult,
    val findings: List<ForensicFinding> = emptyList(),
    val gaps: List<ArchitecturalGap> = emptyList(),
    val roleMappings: List<ComponentRoleMapping> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis()
)
