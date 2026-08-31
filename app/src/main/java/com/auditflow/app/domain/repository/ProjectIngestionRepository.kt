package com.auditflow.app.domain.repository

import android.content.Context
import android.net.Uri
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.SourceFileNode

/**
 * Domain interface for ingesting real software projects from local storage or GitHub.
 *
 * Invariant:
 * Never returns synthetic files or fake success.
 * Always establishes real file representations or reports honest failure.
 */
interface ProjectIngestionRepository {

    /**
     * Ingests a local project directory selected via Android Storage Access Framework (SAF).
     * Enumerates actual directory structure and extracts file metadata.
     */
    suspend fun ingestLocalDirectory(
        treeUri: Uri,
        context: Context,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>>

    /**
     * Ingests a remote GitHub repository by fetching its verified metadata and Git file tree.
     */
    suspend fun ingestGitHubRepository(
        repoUrlOrSlug: String,
        branch: String? = null,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>>

    /**
     * Reads or fetches the raw text content of a specific file from local SAF or remote GitHub.
     */
    suspend fun readFileContent(
        projectMetadata: ProjectMetadata,
        relativePath: String,
        context: Context? = null
    ): Result<String>
}
