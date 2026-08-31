package com.auditflow.app.domain.model

/**
 * Deterministic representation of an ingested source file or directory node.
 * Preserves exact relative paths, metadata, and readability status.
 */
data class SourceFileNode(
    val relativePath: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val isReadable: Boolean = true,
    val mimeType: String? = null,
    val pathClassification: PathClassification = PathClassification.ESTABLISHED
) {
    /**
     * Topology classification (ROOT PATH for depth 1, CHILD PATH for depth >= 2).
     */
    val pathTopology: PathTopologyType
        get() = RelativePathHelper.classifyTopology(relativePath)

    /**
     * Physical node type (DIRECTORY or FILE).
     */
    val physicalNodeType: PhysicalNodeType
        get() = if (isDirectory) PhysicalNodeType.DIRECTORY else PhysicalNodeType.FILE

    /**
     * True if this node's parent is the Project Root.
     */
    val isRootPath: Boolean
        get() = RelativePathHelper.isRootPath(relativePath)

    /**
     * Semantic file type derived from filename and extension.
     */
    val semanticFileType: SemanticFileType
        get() = if (isDirectory) SemanticFileType.UNKNOWN else SemanticFileType.fromFileNameOrPath(relativePath)
}
