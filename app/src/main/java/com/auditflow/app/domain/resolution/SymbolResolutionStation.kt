package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.ResolutionStatus
import com.auditflow.app.domain.model.ResolutionSummary
import com.auditflow.app.domain.model.Station4ResolutionResult

/**
 * STATION 4 — Symbol Resolution, Cross-File Relationship Mapping & Canonical Defect Detection
 *
 * Coordinates:
 * 1. Project-wide symbol registration & indexing
 * 2. Cross-file relationship mapping (inheritance, implementation, calls, type usages)
 * 3. Canonical software defect and architectural inconsistency detection
 */
class SymbolResolutionStation(
    private val relationshipResolver: CrossFileRelationshipResolver = CrossFileRelationshipResolver(),
    private val defectDetector: CanonicalDefectDetector = CanonicalDefectDetector()
) {

    /**
     * Execute Station 4 audit over the collected FileInspectionResults.
     */
    fun process(inspections: List<FileInspectionResult>): Station4ResolutionResult {
        // 1. Build Project-Wide Symbol Registry
        val registry = ProjectSymbolRegistry.build(inspections)

        // 2. Resolve Relationships and Construct Graph
        val graph = relationshipResolver.resolve(inspections, registry)

        // 3. Detect Canonical Architectural Defects & Inconsistencies
        val findings = defectDetector.detectDefects(inspections, registry, graph)

        // 4. Compute Resolution Summary Statistics
        val totalUnresolved = graph.dependencies.count { it.resolutionStatus == ResolutionStatus.UNRESOLVED } +
            graph.calls.count { it.resolutionStatus == ResolutionStatus.UNRESOLVED } +
            graph.inheritances.count { it.resolutionStatus == ResolutionStatus.UNRESOLVED }

        val totalExternal = graph.dependencies.count { it.isExternal } +
            graph.calls.count { it.isExternal } +
            graph.inheritances.count { it.isExternal }

        val summary = ResolutionSummary(
            totalFilesAudited = inspections.size,
            totalSymbolsIndexed = registry.size,
            totalDependenciesResolved = graph.dependencies.size,
            totalCallEdges = graph.calls.size,
            totalInheritanceEdges = graph.inheritances.size,
            totalImplementationEdges = graph.implementations.size,
            totalUnresolvedSymbols = totalUnresolved,
            totalExternalReferences = totalExternal,
            totalDefectsDetected = findings.size
        )

        return Station4ResolutionResult(
            dependencies = graph.dependencies,
            calls = graph.calls,
            inheritances = graph.inheritances,
            implementations = graph.implementations,
            references = graph.references,
            findings = findings,
            summary = summary
        )
    }
}
