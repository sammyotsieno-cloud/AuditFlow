package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.IngestedProjectRecord
import com.auditflow.app.domain.model.PhysicalHierarchyResult
import com.auditflow.app.domain.model.ProjectTreeNode
import com.auditflow.app.domain.model.SourceFileNode

/**
 * STATION 3 — FILE CONTENT & CODE STRUCTURE DISCOVERY STATION
 *
 * Consumes: [PhysicalHierarchyResult] or [IngestedProjectRecord]
 * Produces: [FileInspectionResult] for every physical file.
 *
 * Implements the station contract defined in Tier 13 of the Universal Audit Vocabulary.
 */
object FileInspectionStation {

    /**
     * Inspects a single source file with optional raw content.
     */
    fun inspectFile(
        sourceNode: SourceFileNode,
        rawContent: String?
    ): FileInspectionResult {
        return SourceCodeStructureExtractor.inspect(sourceNode, rawContent)
    }

    /**
     * Inspects all files in an [IngestedProjectRecord] using a content provider lambda.
     */
    fun inspectProject(
        ingestedRecord: IngestedProjectRecord,
        contentProvider: (relativePath: String) -> String?
    ): List<FileInspectionResult> {
        return ingestedRecord.files.map { fileNode ->
            val content = contentProvider(fileNode.relativePath)
            SourceCodeStructureExtractor.inspect(fileNode, content)
        }
    }

    /**
     * Inspects all leaf files in a [PhysicalHierarchyResult] using a content provider lambda.
     */
    fun inspectHierarchy(
        hierarchy: PhysicalHierarchyResult,
        contentProvider: (relativePath: String) -> String?
    ): List<FileInspectionResult> {
        val leafFiles = mutableListOf<SourceFileNode>()

        fun collectLeafFiles(node: ProjectTreeNode) {
            if (!node.isDirectory && node.sourceNode != null) {
                leafFiles.add(node.sourceNode)
            }
            for (child in node.children) {
                collectLeafFiles(child)
            }
        }

        collectLeafFiles(hierarchy.rootNode)

        return leafFiles.map { fileNode ->
            val content = contentProvider(fileNode.relativePath)
            SourceCodeStructureExtractor.inspect(fileNode, content)
        }
    }
}
