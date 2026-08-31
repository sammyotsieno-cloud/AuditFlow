package com.auditflow.app.domain.model

/**
 * Immutable recursive tree model representing a node in the reconstructed project hierarchy.
 *
 * Represents genuine parent-child structural relationships discovered during ingestion.
 *
 * @property name Name of the file or directory segment (e.g. "app", "src", "MainActivity.kt").
 * @property relativePath Full normalized path relative to the project root (e.g. "app/src/main/java/MainActivity.kt").
 * @property isDirectory True if this node represents a directory parent, false for a leaf file.
 * @property sourceNode Original ingested [SourceFileNode] if available (contains size, mime, extension).
 * @property children Ordered list of child nodes beneath this directory.
 */
data class ProjectTreeNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sourceNode: SourceFileNode? = null,
    val children: List<ProjectTreeNode> = emptyList()
)
