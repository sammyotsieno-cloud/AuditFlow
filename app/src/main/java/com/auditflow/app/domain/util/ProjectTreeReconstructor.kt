package com.auditflow.app.domain.util

import com.auditflow.app.domain.model.ProjectTreeNode
import com.auditflow.app.domain.model.SourceFileNode

/**
 * Display-ready representation of a single line in the locked canonical tree format.
 *
 * @property prefix ASCII branch and continuation symbols (e.g. "├── ", "│   ├── ", "└── ").
 * @property displayName Formatted name (e.g. "app/" for directories, "MainActivity.kt" for files, or project root).
 * @property isDirectory True if this line represents a directory.
 * @property isRoot True if this line represents the project root node.
 * @property relativePath Full normalized path for lookup/navigation.
 * @property sizeBytes Size in bytes (0 for directories or unavailable).
 * @property extension File extension (e.g. "kt", "xml").
 * @property depth Depth level from the root (0 for root, 1 for top-level children, etc.).
 */
data class ProjectTreeLine(
    val prefix: String,
    val displayName: String,
    val isDirectory: Boolean,
    val isRoot: Boolean = false,
    val relativePath: String = "",
    val sizeBytes: Long = 0L,
    val extension: String = "",
    val depth: Int = 0
)

/**
 * Deterministic domain reconstructor for project hierarchies.
 *
 * Transforms flat [SourceFileNode] lists into a genuine [ProjectTreeNode] hierarchy
 * with merged shared parent paths and deterministic sibling ordering (directories first,
 * then files, alphabetically), and renders display lines in the locked canonical tree format:
 *
 * PROJECT
 * ├── README.md
 * ├── app/
 * │   ├── build.gradle.kts
 * │   └── src/
 * │       ├── main/
 * │       │   └── ...
 * │       └── test/
 * │           └── ...
 * └── ...
 */
object ProjectTreeReconstructor {

    private class MutableNode(
        val name: String,
        val relativePath: String,
        var isDirectory: Boolean,
        var sourceNode: SourceFileNode? = null
    ) {
        val children = mutableMapOf<String, MutableNode>()

        fun toImmutable(): ProjectTreeNode {
            val sortedChildren = children.values
                .sortedWith(
                    compareBy<MutableNode> { !it.isDirectory } // directories first (false < true)
                        .thenBy { it.name.lowercase() }        // alphabetical within category
                )
                .map { it.toImmutable() }

            return ProjectTreeNode(
                name = name,
                relativePath = relativePath,
                isDirectory = isDirectory,
                sourceNode = sourceNode,
                children = sortedChildren
            )
        }
    }

    /**
     * Reconstructs a full [ProjectTreeNode] hierarchy from raw [SourceFileNode] items.
     * Merges common parent directory paths and orders siblings deterministically.
     */
    fun reconstruct(projectName: String, files: List<SourceFileNode>): ProjectTreeNode {
        val cleanRootName = projectName.trim().ifBlank { "PROJECT" }
        val root = MutableNode(
            name = cleanRootName,
            relativePath = "",
            isDirectory = true
        )

        // Deduplicate and process files
        val distinctFiles = files.distinctBy { normalizePath(it.relativePath) }

        for (fileNode in distinctFiles) {
            val normalizedPath = normalizePath(fileNode.relativePath)
            if (normalizedPath.isBlank()) continue

            val segments = normalizedPath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue

            var currentNode = root
            var currentPathAccumulator = ""

            for (i in segments.indices) {
                val segment = segments[i]
                val isLastSegment = (i == segments.size - 1)
                currentPathAccumulator = if (currentPathAccumulator.isEmpty()) segment else "$currentPathAccumulator/$segment"

                if (isLastSegment) {
                    val existing = currentNode.children[segment]
                    if (existing == null) {
                        val node = MutableNode(
                            name = segment,
                            relativePath = currentPathAccumulator,
                            isDirectory = fileNode.isDirectory,
                            sourceNode = fileNode
                        )
                        currentNode.children[segment] = node
                        currentNode = node
                    } else {
                        // Merge explicit node metadata if existing was implicitly created
                        if (fileNode.isDirectory) {
                            existing.isDirectory = true
                        }
                        if (existing.sourceNode == null) {
                            existing.sourceNode = fileNode
                        }
                        currentNode = existing
                    }
                } else {
                    // Intermediate segment is always a directory
                    val existing = currentNode.children[segment]
                    if (existing == null) {
                        val intermediateDir = MutableNode(
                            name = segment,
                            relativePath = currentPathAccumulator,
                            isDirectory = true
                        )
                        currentNode.children[segment] = intermediateDir
                        currentNode = intermediateDir
                    } else {
                        existing.isDirectory = true
                        currentNode = existing
                    }
                }
            }
        }

        return root.toImmutable()
    }

    /**
     * Generates display lines matching the canonical locked visual tree format from a [ProjectTreeNode].
     */
    fun generateTreeLines(root: ProjectTreeNode): List<ProjectTreeLine> {
        val lines = mutableListOf<ProjectTreeLine>()

        // 1. Root line
        val rootDisplayName = if (root.isDirectory && !root.name.endsWith("/")) {
            "${root.name}/"
        } else {
            root.name
        }
        lines.add(
            ProjectTreeLine(
                prefix = "",
                displayName = rootDisplayName,
                isDirectory = true,
                isRoot = true,
                relativePath = root.relativePath,
                depth = 0
            )
        )

        // 2. Recursive traversal for children
        traverseChildren(
            children = root.children,
            ancestorContinuations = emptyList(),
            lines = lines,
            depth = 1
        )

        return lines
    }

    private fun traverseChildren(
        children: List<ProjectTreeNode>,
        ancestorContinuations: List<Boolean>, // true if ancestor has remaining siblings below (needs '│   '), false if last child (needs '    ')
        lines: MutableList<ProjectTreeLine>,
        depth: Int
    ) {
        val count = children.size
        for (i in 0 until count) {
            val child = children[i]
            val isLast = (i == count - 1)

            val prefixBuilder = StringBuilder()
            for (hasContinuation in ancestorContinuations) {
                if (hasContinuation) {
                    prefixBuilder.append("│   ")
                } else {
                    prefixBuilder.append("    ")
                }
            }

            if (isLast) {
                prefixBuilder.append("└── ")
            } else {
                prefixBuilder.append("├── ")
            }

            val displayName = if (child.isDirectory) {
                if (child.name.endsWith("/")) child.name else "${child.name}/"
            } else {
                child.name
            }

            val line = ProjectTreeLine(
                prefix = prefixBuilder.toString(),
                displayName = displayName,
                isDirectory = child.isDirectory,
                isRoot = false,
                relativePath = child.relativePath,
                sizeBytes = child.sourceNode?.sizeBytes ?: 0L,
                extension = child.sourceNode?.extension ?: if (!child.isDirectory) child.name.substringAfterLast('.', "") else "",
                depth = depth
            )
            lines.add(line)

            if (child.isDirectory && child.children.isNotEmpty()) {
                val nextContinuations = ancestorContinuations + (!isLast)
                traverseChildren(
                    children = child.children,
                    ancestorContinuations = nextContinuations,
                    lines = lines,
                    depth = depth + 1
                )
            }
        }
    }

    private fun normalizePath(rawPath: String): String {
        return com.auditflow.app.domain.model.RelativePathHelper.normalize(rawPath)
    }
}
