package com.auditflow.app.domain.repository

import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.RepositorySnapshot
import com.auditflow.app.domain.model.SourceFileNode

/**

* Domain boundary for acquiring real software-project data.

* 

* The repository layer is responsible for acquisition only. It must never

* manufacture synthetic files or report successful acquisition without

* real underlying data.

* 

* Local projects and artifacts may be ingested directly.

* 

* Remote GitHub repositories use the authoritative snapshot acquisition

* contract [acquireRepositorySnapshot].

* 

* Core acquisition invariant:

* 

* Acquire once

*      ↓

* Verify once

*      ↓

* Freeze snapshot

*      ↓

* Reuse downstream

* 

* Once a GitHub repository snapshot has been successfully acquired,

* downstream stages must consume that frozen snapshot rather than

* initiating another repository acquisition.

* 

* The interface defines WHAT the domain guarantees.

* The implementation defines HOW those guarantees are achieved.
  */
  interface ProjectIngestionRepository {
  
  /**
  
  * Ingests a local project directory selected through Android Storage
  * Access Framework (SAF).
  * 
  * The result contains the real directory/file hierarchy that could
  * be enumerated from the selected location.
    */
    suspend fun ingestLocalDirectory(
    treeUriString: String,
    onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>>
  
  /**
  
  * Ingests a locally supplied file artifact selected through SAF.
  * 
  * Examples include source files, APKs, and ZIP archives.
  * 
  * The implementation is responsible for identifying the artifact,
  * extracting its real structure, and preserving artifact-specific
  * metadata through the domain models.
    */
    suspend fun ingestLocalFile(
    fileUriString: String,
    onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<Pair<ProjectMetadata, List<SourceFileNode>>>
  
  /**
  
  * Acquires the authoritative immutable snapshot of a GitHub repository.
  * 
  * A successful result must represent one resolved repository version
  * and carry the acquisition evidence required by downstream stages.
  * 
  * The snapshot is expected to preserve:
  * 
  * - repository identity
  * - target branch
  * - immutable target commit SHA
  * - complete repository tree
  * - acquired file content or bytes
  * - expected Git blob identity
  * - verification state
  * - acquisition completeness state
  * 
  * Failure must be explicit.
  * 
  * There is intentionally no secondary GitHub ingestion method or
  * fallback contract here. A failed snapshot acquisition must remain
  * a failed acquisition rather than silently switching to another
  * repository-fetching path.
    */
    suspend fun acquireRepositorySnapshot(
    repoUrlOrSlug: String,
    branch: String? = null,
    onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<RepositorySnapshot>
  
  /**
  
  * Reads raw text content from a local SAF artifact or another
  * non-snapshot ingestion context.
  * 
  * This method is retained for workflows where content is not already
  * available inside a frozen repository snapshot.
  * 
  * Once a GitHub [RepositorySnapshot] exists, downstream processing
  * must use the snapshot's acquired file records rather than invoking
  * this method to perform another network acquisition.
  * 
  * Binary repository evidence is intentionally outside this
  * String-only compatibility boundary. Binary acquisition belongs to
  * the snapshot model as acquired bytes.
    */
    suspend fun readFileContent(
    projectMetadata: ProjectMetadata,
    relativePath: String
    ): Result<String>
    }
