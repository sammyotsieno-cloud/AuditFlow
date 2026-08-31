package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.DefectSeverity
import com.auditflow.app.domain.model.EpistemicClassification
import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.ForensicDefectKind
import com.auditflow.app.domain.model.ForensicFinding
import com.auditflow.app.domain.model.ResolutionStatus

/**
 * Performs canonical defect and inconsistency analysis over the resolved symbol registry
 * and cross-file relationship graph using standard software-engineering rules.
 */
class CanonicalDefectDetector {

    /**
     * Run all forensic defect audits across the inspected files and relationship graph.
     */
    fun detectDefects(
        inspections: List<FileInspectionResult>,
        registry: ProjectSymbolRegistry,
        graph: ResolvedRelationshipGraph
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()
        var idCounter = 1

        fun nextId(prefix: String): String = "$prefix-%03d".format(idCounter++)

        // 1. Audit Circular Dependencies
        val circularFindings = detectCircularDependencies(inspections, graph, ::nextId)
        findings.addAll(circularFindings)

        // 2. Audit Invalid Dependency Directions (e.g. Domain -> UI / Framework)
        val invalidDirectionFindings = detectInvalidDependencyDirections(inspections, graph, ::nextId)
        findings.addAll(invalidDirectionFindings)

        // 3. Audit Broken / Unresolved Dependencies
        val brokenDependencyFindings = detectBrokenDependencies(inspections, graph, ::nextId)
        findings.addAll(brokenDependencyFindings)

        // 4. Audit Duplicate Implementations
        val duplicateFindings = detectDuplicateImplementations(graph, ::nextId)
        findings.addAll(duplicateFindings)

        // 5. Audit Orphan Components (Unreferenced & Uncalled)
        val orphanFindings = detectOrphanComponents(inspections, registry, graph, ::nextId)
        findings.addAll(orphanFindings)

        // 6. Audit Terminology & Semantic Inconsistencies & Misplaced Responsibilities
        val consistencyFindings = detectConsistencyDefects(inspections, graph, ::nextId)
        findings.addAll(consistencyFindings)

        return findings
    }

    // ========================================================================
    // 1. CIRCULAR DEPENDENCIES
    // ========================================================================

    private fun detectCircularDependencies(
        inspections: List<FileInspectionResult>,
        graph: ResolvedRelationshipGraph,
        nextId: (String) -> String
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()
        val fileAdjacency = mutableMapOf<String, MutableSet<String>>()

        for (edge in graph.dependencies) {
            val target = edge.targetFileRelativePath
            if (target != null && target != edge.sourceFileRelativePath) {
                fileAdjacency.getOrPut(edge.sourceFileRelativePath) { mutableSetOf() }.add(target)
            }
        }

        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(current: String, path: List<String>) {
            visited.add(current)
            recursionStack.add(current)

            val neighbors = fileAdjacency[current] ?: emptySet()
            for (neighbor in neighbors) {
                if (neighbor in recursionStack) {
                    val cycleStartIdx = path.indexOf(neighbor)
                    val cycle = if (cycleStartIdx != -1) path.subList(cycleStartIdx, path.size) + neighbor else listOf(current, neighbor)
                    val cycleSet = cycle.toSet()

                    if (cycleSet.size > 1 && reportedCycles.add(cycleSet)) {
                        val cycleStr = cycle.joinToString(" -> ")
                        findings.add(
                            ForensicFinding(
                                id = nextId("CIRC"),
                                defectKind = ForensicDefectKind.CIRCULAR_DEPENDENCY,
                                severity = DefectSeverity.MAJOR,
                                affectedRelativePath = current,
                                description = "Circular dependency cycle detected: $cycleStr",
                                evidence = "Cyclic dependency edges between files: $cycleStr",
                                epistemicType = EpistemicClassification.FACT,
                                recommendation = "Break the circular cycle by introducing a shared interface, repository abstraction, or moving common domain models to a lower tier."
                            )
                        )
                    }
                } else if (neighbor !in visited) {
                    dfs(neighbor, path + neighbor)
                }
            }

            recursionStack.remove(current)
        }

        for (file in inspections.map { it.relativePath }) {
            if (file !in visited) {
                dfs(file, listOf(file))
            }
        }

        return findings
    }

    // ========================================================================
    // 2. INVALID DEPENDENCY DIRECTIONS
    // ========================================================================

    private fun detectInvalidDependencyDirections(
        inspections: List<FileInspectionResult>,
        graph: ResolvedRelationshipGraph,
        nextId: (String) -> String
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()

        for (insp in inspections) {
            val path = insp.relativePath.lowercase()
            val isDomain = path.contains("/domain/") || path.contains("domain/model") || path.contains("domain/usecase")

            if (isDomain) {
                // Domain files must NOT depend on UI frameworks (Compose, Android Views, Activities)
                val forbiddenImports = listOf(
                    "androidx.compose" to "Jetpack Compose UI framework",
                    "android.view" to "Android View system",
                    "android.widget" to "Android Widget framework",
                    "android.app.Activity" to "Android Activity lifecycle",
                    "androidx.appcompat" to "Android AppCompat framework"
                )

                for (imp in insp.imports) {
                    for ((forbiddenPrefix, frameworkName) in forbiddenImports) {
                        if (imp.importPath.startsWith(forbiddenPrefix)) {
                            findings.add(
                                ForensicFinding(
                                    id = nextId("DEP-DIR"),
                                    defectKind = ForensicDefectKind.INVALID_DEPENDENCY_DIRECTION,
                                    severity = DefectSeverity.MAJOR,
                                    affectedRelativePath = insp.relativePath,
                                    description = "Domain layer component references external UI framework ($frameworkName)",
                                    evidence = "Import '${imp.importPath}' at line ${imp.lineNumber} in domain file '${insp.relativePath}'",
                                    epistemicType = EpistemicClassification.FACT,
                                    recommendation = "Remove direct UI framework dependency from the core domain layer. The domain layer must remain pure and platform-agnostic."
                                )
                            )
                        }
                    }
                }
            }
        }

        return findings
    }

    // ========================================================================
    // 3. BROKEN / UNRESOLVED DEPENDENCIES
    // ========================================================================

    private fun detectBrokenDependencies(
        inspections: List<FileInspectionResult>,
        graph: ResolvedRelationshipGraph,
        nextId: (String) -> String
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()

        for (edge in graph.dependencies) {
            if (edge.resolutionStatus == ResolutionStatus.UNRESOLVED && !edge.isExternal) {
                findings.add(
                    ForensicFinding(
                        id = nextId("BROKEN-DEP"),
                        defectKind = ForensicDefectKind.BROKEN_DEPENDENCY,
                        severity = DefectSeverity.MAJOR,
                        affectedRelativePath = edge.sourceFileRelativePath,
                        description = "Unresolved dependency target '${edge.targetSymbolFqn}'",
                        evidence = edge.evidence.ifBlank { "Unresolved dependency on '${edge.targetSymbolFqn}' at line ${edge.lineNumber}" },
                        epistemicType = EpistemicClassification.FACT,
                        recommendation = "Verify that '${edge.targetSymbolFqn}' is either defined in the project or declared in external dependencies."
                    )
                )
            }
        }

        return findings
    }

    // ========================================================================
    // 4. DUPLICATE IMPLEMENTATIONS
    // ========================================================================

    private fun detectDuplicateImplementations(
        graph: ResolvedRelationshipGraph,
        nextId: (String) -> String
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()

        // Group implementation edges by interface FQN
        val byInterface = graph.implementations.groupBy { it.interfaceTypeFqn }

        for ((interfaceFqn, impls) in byInterface) {
            if (impls.size > 1 && !interfaceFqn.contains("Serializable") && !interfaceFqn.contains("Comparable")) {
                val implTypes = impls.map { it.implementingTypeFqn }
                val first = impls.first()

                findings.add(
                    ForensicFinding(
                        id = nextId("DUP-IMPL"),
                        defectKind = ForensicDefectKind.DUPLICATE_IMPLEMENTATION,
                        severity = DefectSeverity.MINOR,
                        affectedRelativePath = first.implementingFileRelativePath,
                        description = "Multiple distinct implementations found for interface '$interfaceFqn': ${implTypes.joinToString(", ")}",
                        evidence = "Interface '$interfaceFqn' is implemented by: ${implTypes.joinToString(", ")}",
                        epistemicType = EpistemicClassification.INFERENCE,
                        recommendation = "Consolidate or verify distinct responsibility boundaries for multiple implementations of '$interfaceFqn'."
                    )
                )
            }
        }

        return findings
    }

    // ========================================================================
    // 5. ORPHAN COMPONENTS
    // ========================================================================

    private fun detectOrphanComponents(
        inspections: List<FileInspectionResult>,
        registry: ProjectSymbolRegistry,
        graph: ResolvedRelationshipGraph,
        nextId: (String) -> String
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()

        // Collect all target files and symbols that are referenced by incoming dependencies or calls
        val referencedFiles = graph.dependencies.mapNotNull { it.targetFileRelativePath }.toSet()
        val calledSymbols = graph.calls.map { it.calleeSymbol }.toSet()
        val calledFqns = graph.calls.mapNotNull { it.calleeFqn }.toSet()

        for (insp in inspections) {
            val path = insp.relativePath.lowercase()
            // Ignore main entry points, manifests, test files, and application roots
            val isIgnored = path.endsWith("manifest.xml") ||
                path.contains("test") ||
                path.contains("application") ||
                path.contains("activity") ||
                insp.topLevelSymbols.any { it.annotations.any { a -> a.contains("HiltAndroidApp") || a.contains("AndroidEntryPoint") } }

            if (!isIgnored && insp.relativePath !in referencedFiles) {
                // Check if any symbols inside are called
                val hasIncomingCall = insp.allSymbols.any { it.name in calledSymbols || it.fullyQualifiedName in calledFqns }
                if (!hasIncomingCall && insp.topLevelSymbols.isNotEmpty()) {
                    val primarySymbol = insp.topLevelSymbols.first()
                    findings.add(
                        ForensicFinding(
                            id = nextId("ORPHAN"),
                            defectKind = ForensicDefectKind.ORPHAN_COMPONENT,
                            severity = DefectSeverity.MINOR,
                            affectedRelativePath = insp.relativePath,
                            description = "Orphan component '${primarySymbol.name}' has 0 incoming dependencies or callers",
                            evidence = "No other file in the codebase imports '${insp.relativePath}' or invokes symbols from '${primarySymbol.name}'",
                            epistemicType = EpistemicClassification.FACT,
                            recommendation = "Verify if '${primarySymbol.name}' is dead code or intended to be wired into an existing execution flow."
                        )
                    )
                }
            }
        }

        return findings
    }

    // ========================================================================
    // 6. TERMINOLOGY / SEMANTIC INCONSISTENCY & MISPLACED RESPONSIBILITY
    // ========================================================================

    private fun detectConsistencyDefects(
        inspections: List<FileInspectionResult>,
        graph: ResolvedRelationshipGraph,
        nextId: (String) -> String
    ): List<ForensicFinding> {
        val findings = mutableListOf<ForensicFinding>()

        for (insp in inspections) {
            val fileName = insp.relativePath.substringAfterLast('/')
            val primarySymbol = insp.topLevelSymbols.firstOrNull { it.kind in listOf(CodeSymbolKind.CLASS, CodeSymbolKind.INTERFACE, CodeSymbolKind.OBJECT) }

            if (primarySymbol != null) {
                // Rule A: File name vs Primary Class name divergence
                val expectedFileName = "${primarySymbol.name}.kt"
                val expectedJavaName = "${primarySymbol.name}.java"
                if (!fileName.equals(expectedFileName, ignoreCase = true) && !fileName.equals(expectedJavaName, ignoreCase = true) && !fileName.endsWith(".xml")) {
                    findings.add(
                        ForensicFinding(
                            id = nextId("TERM-INCON"),
                            defectKind = ForensicDefectKind.TERMINOLOGY_INCONSISTENCY,
                            severity = DefectSeverity.MINOR,
                            affectedRelativePath = insp.relativePath,
                            description = "Filename '$fileName' diverges from primary declared symbol '${primarySymbol.name}'",
                            evidence = "File '$fileName' defines primary top-level symbol '${primarySymbol.name}' at line ${primarySymbol.startLine}",
                            epistemicType = EpistemicClassification.FACT,
                            recommendation = "Align the filename with its primary code symbol or extract secondary symbols into their own files."
                        )
                    )
                }

                // Rule B: Misplaced Responsibility (e.g. Controller/Engine that is actually UI Composable)
                val isNameControllerOrEngine = primarySymbol.name.endsWith("Controller") || primarySymbol.name.endsWith("Engine") || primarySymbol.name.endsWith("Calculator")
                val isActuallyComposable = primarySymbol.annotations.any { it.contains("Composable") } ||
                    primarySymbol.childSymbols.any { it.kind == CodeSymbolKind.COMPOSABLE } ||
                    insp.allSymbols.any { it.annotations.any { a -> a.contains("Composable") } }

                if (isNameControllerOrEngine && isActuallyComposable) {
                    findings.add(
                        ForensicFinding(
                            id = nextId("MISPLACED-RESP"),
                            defectKind = ForensicDefectKind.MISPLACED_RESPONSIBILITY,
                            severity = DefectSeverity.MAJOR,
                            affectedRelativePath = insp.relativePath,
                            description = "Component '${primarySymbol.name}' is named as a business controller/engine but directly implements UI layout rendering",
                            evidence = "Symbol '${primarySymbol.name}' contains @Composable functions but uses business engine/controller naming",
                            epistemicType = EpistemicClassification.INFERENCE,
                            recommendation = "Separate UI rendering into presentation views and delegate domain calculation logic to dedicated domain controllers."
                        )
                    )
                }
            }
        }

        return findings
    }
}
