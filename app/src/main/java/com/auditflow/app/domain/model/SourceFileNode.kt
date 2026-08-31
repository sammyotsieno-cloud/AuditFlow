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
    val mimeType: String? = null
)
