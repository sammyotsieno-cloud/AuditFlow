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
            totalFilesInspected = inspections.size,
            totalSymbolsIndexed = registry.size,
            totalDependencies = graph.dependencies.size,
            resolvedDependencies = graph.dependencies.count { it.resolutionStatus == ResolutionStatus.RESOLVED },
            externalDependencies = graph.dependencies.count { it.isExternal },
            unresolvedDependencies = graph.dependencies.count { it.resolutionStatus == ResolutionStatus.UNRESOLVED },
            totalCalls = graph.calls.size,
            resolvedCalls = graph.calls.count { it.resolutionStatus == ResolutionStatus.RESOLVED },
            externalCalls = graph.calls.count { it.isExternal },
            unresolvedCalls = graph.calls.count { it.resolutionStatus == ResolutionStatus.UNRESOLVED },
            totalInheritances = graph.inheritances.size,
            totalImplementations = graph.implementations.size,
            totalDefectsFound = findings.size
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
