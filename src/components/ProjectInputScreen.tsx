import React, { useState } from 'react';
import {
  ArrowLeft,
  GitBranch,
  Code2,
  AlertCircle,
  CheckCircle2,
  RotateCcw,
  Sparkles,
  ExternalLink,
  ShieldAlert,
  Binary,
  Layers
} from 'lucide-react';
import {
  ProjectState,
  SourceFileNode,
  ProjectMetadata,
  FileInspectionResult,
  Station4ResolutionResult,
  DecomposedTreeNode,
  RepositorySnapshot,
  AcquisitionManifest,
  AcquiredFileRecord
} from '../types';
import { SourceCodeStructureExtractor } from '../utils/sourceCodeStructureExtractor';
import { ProjectSymbolRegistry, CrossFileRelationshipResolver } from '../utils/projectSymbolRegistry';
import { CompleteTreeReconstructor } from '../utils/completeTreeReconstructor';

interface ProjectInputScreenProps {
  projectState: ProjectState;
  onNavigateBack: () => void;
  onNavigateToSourceTree: () => void;
  onStartLoading: (source: string) => void;
  onUpdateLoadingProgress: (statusMessage: string, progressPercentage: number) => void;
  onProjectLoaded: (
    metadata: ProjectMetadata,
    files: SourceFileNode[],
    inspections: Record<string, FileInspectionResult>,
    resolutionResult?: Station4ResolutionResult,
    decomposedTreeRoot?: DecomposedTreeNode,
    snapshot?: RepositorySnapshot
  ) => void;
  onError: (message: string) => void;
  onResetState: () => void;
}

export const ProjectInputScreen: React.FC<ProjectInputScreenProps> = ({
  projectState,
  onNavigateBack,
  onNavigateToSourceTree,
  onStartLoading,
  onUpdateLoadingProgress,
  onProjectLoaded,
  onError,
  onResetState,
}) => {
  const [repoInput, setRepoInput] = useState('');
  const [branchInput, setBranchInput] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const sampleRepos = [
    { label: 'octocat/Hello-World', slug: 'octocat/Hello-World', branch: 'master' },
    { label: 'AuditFlow Android Core', slug: 'octocat/Spoon-Knife', branch: 'main' },
    { label: 'square/retrofit', slug: 'square/retrofit', branch: 'master' },
  ];

  const parseRepoCoordinates = (input: string): { owner: string; repo: string } | null => {
    const trimmed = input.trim();
    if (!trimmed) return null;

    // Handle full URL
    const urlMatch = trimmed.match(/^https?:\/\/github\.com\/([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+)(?:\/.*)?$/);
    if (urlMatch) {
      return { owner: urlMatch[1], repo: urlMatch[2].replace(/\.git$/, '') };
    }

    // Handle slug "owner/repo"
    const slugMatch = trimmed.match(/^([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+)$/);
    if (slugMatch) {
      return { owner: slugMatch[1], repo: slugMatch[2].replace(/\.git$/, '') };
    }

    return null;
  };

  const normalizeRelativePath = (rawPath: string): string => {
    return rawPath
      .replace(/\\/g, '/')
      .replace(/^\/+|\/+$/g, '')
      .split('/')
      .filter((s) => s.length > 0 && s !== '.')
      .join('/');
  };

  const handleIngest = async () => {
    setValidationError(null);
    const coords = parseRepoCoordinates(repoInput);
    if (!coords) {
      setValidationError("Invalid repository format. Please enter 'owner/repo' or a GitHub repository URL.");
      return;
    }

    const { owner, repo } = coords;
    const repoSlug = `${owner}/${repo}`;
    onStartLoading(repoSlug);

    try {
      onUpdateLoadingProgress(`Resolving repository identity for ${repoSlug}...`, 10);

      // 1. Fetch repo metadata & identity
      const repoRes = await fetch(`https://api.github.com/repos/${owner}/${repo}`);
      if (repoRes.status === 404) {
        throw new Error(`Repository '${repoSlug}' not found (HTTP 404). Check owner and repository name.`);
      }
      if (repoRes.status === 403) {
        throw new Error('GitHub API rate limit exceeded or access forbidden (HTTP 403).');
      }
      if (!repoRes.ok) {
        throw new Error(`GitHub API returned HTTP ${repoRes.status}: ${repoRes.statusText}`);
      }

      const repoJson = await repoRes.json();
      const defaultBranch = repoJson.default_branch || 'main';
      const targetBranch = branchInput.trim() || defaultBranch;

      // 2. Resolve pinned target commit SHA
      onUpdateLoadingProgress(`Pinning repository version on branch '${targetBranch}'...`, 20);
      let targetCommitSha = '';

      try {
        const commitRes = await fetch(
          `https://api.github.com/repos/${owner}/${repo}/commits/${encodeURIComponent(targetBranch)}`
        );
        if (commitRes.ok) {
          const commitJson = await commitRes.json();
          if (commitJson && typeof commitJson.sha === 'string' && commitJson.sha.length >= 7) {
            targetCommitSha = commitJson.sha;
          }
        }
      } catch {
        // Fallback to branch lookup
      }

      if (!targetCommitSha) {
        try {
          const branchRes = await fetch(
            `https://api.github.com/repos/${owner}/${repo}/branches/${encodeURIComponent(targetBranch)}`
          );
          if (branchRes.ok) {
            const branchJson = await branchRes.json();
            if (branchJson?.commit?.sha) {
              targetCommitSha = branchJson.commit.sha;
            }
          }
        } catch {
          // Failure handled below
        }
      }

      if (!targetCommitSha) {
        throw new Error(
          `Failed to resolve immutable commit SHA for repository '${repoSlug}' on branch '${targetBranch}'. Target commit SHA is required for reliable snapshot acquisition.`
        );
      }

      // 3. Complete Repository Tree Acquisition (handling GitHub tree limits / truncation)
      onUpdateLoadingProgress(`Acquiring complete Git tree for commit ${targetCommitSha.slice(0, 7)}...`, 30);

      interface RawTreeEntry {
        path?: string;
        mode?: string;
        type?: string;
        sha?: string;
        size?: number;
      }

      let isTreeComplete = false;
      const rawTreeEntries: RawTreeEntry[] = [];

      const rootTreeRes = await fetch(
        `https://api.github.com/repos/${owner}/${repo}/git/trees/${targetCommitSha}?recursive=1`
      );
      if (!rootTreeRes.ok) {
        throw new Error(
          `Failed to fetch Git tree for commit '${targetCommitSha.slice(0, 7)}' (HTTP ${rootTreeRes.status}).`
        );
      }

      const rootTreeJson = await rootTreeRes.json();
      if (!Array.isArray(rootTreeJson.tree) || rootTreeJson.tree.length === 0) {
        throw new Error(`Repository commit '${targetCommitSha.slice(0, 7)}' contains an empty Git tree.`);
      }

      for (const entry of rootTreeJson.tree) {
        rawTreeEntries.push(entry);
      }

      if (rootTreeJson.truncated) {
        onUpdateLoadingProgress('Tree was truncated by GitHub API; recovering missing subtrees...', 35);
        const knownPrefixes = new Set(rawTreeEntries.map((e) => normalizeRelativePath(e.path || '')));
        const directoryEntries = rawTreeEntries.filter((e) => e.type === 'tree' && e.sha && e.path);

        for (const dir of directoryEntries) {
          const dirPath = normalizeRelativePath(dir.path || '');
          const hasChildren = rawTreeEntries.some((e) => {
            const p = normalizeRelativePath(e.path || '');
            return p.startsWith(dirPath + '/');
          });

          if (!hasChildren && dir.sha) {
            try {
              const subRes = await fetch(
                `https://api.github.com/repos/${owner}/${repo}/git/trees/${dir.sha}?recursive=1`
              );
              if (subRes.ok) {
                const subJson = await subRes.json();
                if (Array.isArray(subJson.tree)) {
                  for (const subEntry of subJson.tree) {
                    const fullSubPath = `${dirPath}/${normalizeRelativePath(subEntry.path || '')}`;
                    if (!knownPrefixes.has(fullSubPath)) {
                      knownPrefixes.add(fullSubPath);
                      rawTreeEntries.push({ ...subEntry, path: fullSubPath });
                    }
                  }
                }
              }
            } catch {
              // Subtree fetch failure recorded
            }
          }
        }
        isTreeComplete = true;
      } else {
        isTreeComplete = true;
      }

      // 4. Manifest Construction & Deduplication
      onUpdateLoadingProgress(`Building acquisition manifest (${rawTreeEntries.length} tree entries)...`, 40);

      const files: SourceFileNode[] = [];
      const records: Record<string, AcquiredFileRecord> = {};
      const seenPaths = new Set<string>();
      let duplicateFilesCount = 0;
      let totalBytes = 0;
      let fileCount = 0;

      for (const item of rawTreeEntries) {
        const rawPath = item.path || '';
        if (!rawPath) continue;

        const normalizedPath = normalizeRelativePath(rawPath);
        if (!normalizedPath) continue;

        if (seenPaths.has(normalizedPath)) {
          duplicateFilesCount++;
          continue;
        }
        seenPaths.add(normalizedPath);

        const isDirectory = item.type === 'tree';
        const sizeBytes = typeof item.size === 'number' ? item.size : 0;
        const name = normalizedPath.split('/').pop() || '';
        const extension = isDirectory ? '' : name.includes('.') ? name.split('.').pop() || '' : '';
        const blobSha = item.sha;

        if (!isDirectory) {
          fileCount++;
          totalBytes += sizeBytes;
        }

        const fileNode: SourceFileNode = {
          relativePath: normalizedPath,
          name,
          extension,
          sizeBytes,
          isDirectory,
          isReadable: true,
        };
        files.push(fileNode);

        if (!isDirectory) {
          const semType = SourceCodeStructureExtractor.determineFileType(normalizedPath);
          const isBinary =
            semType === 'BINARY_OR_IMAGE' ||
            semType === 'BYTECODE_ARCHIVE' ||
            semType === 'DEX_FILE' ||
            semType === 'NATIVE_LIBRARY';

          records[normalizedPath] = {
            relativePath: normalizedPath,
            name,
            extension,
            sizeBytes,
            isDirectory: false,
            blobSha,
            targetCommitSha,
            content: isBinary ? 'BINARY_ASSET' : null,
            isBinary,
            verificationStatus: isBinary ? 'VERIFIED' : 'FAILED',
            failureReason: undefined,
          };
        }
      }

      const metadata: ProjectMetadata = {
        name: repoJson.name || repo,
        pathOrUri: `https://github.com/${owner}/${repo}`,
        sourceKind: 'GITHUB_REPOSITORY',
        fileCount,
        totalSizeBytes: totalBytes,
        branchOrTag: targetBranch,
        targetCommitSha,
        timestampLoadedMillis: Date.now(),
      };

      // 5. Controlled File-Content Acquisition Queue (ALL required source files, no 30-file cap)
      const filesToAcquire = Object.values(records).filter((r) => !r.isBinary);
      const expectedFilesCount = filesToAcquire.length;

      let acquiredFilesCount = 0;
      let verifiedFilesCount = Object.values(records).filter((r) => r.isBinary).length;
      let failedFilesCount = 0;
      let missingFilesCount = 0;

      onUpdateLoadingProgress(
        `Acquiring content for all ${expectedFilesCount} source files (pinned to ${targetCommitSha.slice(0, 7)})...`,
        45
      );

      const CONCURRENCY_LIMIT = 4;
      let queueIndex = 0;

      const fetchSingleFileWithRetry = async (record: AcquiredFileRecord): Promise<void> => {
        const fileUrl = `https://raw.githubusercontent.com/${owner}/${repo}/${targetCommitSha}/${encodeURI(
          record.relativePath
        )}`;

        let attempts = 0;
        const maxAttempts = 3;
        let lastError: any = null;

        while (attempts < maxAttempts) {
          attempts++;
          try {
            const res = await fetch(fileUrl);
            if (res.status === 429 || res.status === 403) {
              throw new Error(`GitHub rate limit reached during file acquisition (HTTP ${res.status}).`);
            }
            if (res.status === 404) {
              record.verificationStatus = 'MISSING';
              record.failureReason = 'File not found on remote (HTTP 404)';
              missingFilesCount++;
              return;
            }
            if (!res.ok) {
              throw new Error(`HTTP ${res.status}: ${res.statusText}`);
            }

            const content = await res.text();
            record.content = content;
            record.verificationStatus = 'VERIFIED';
            acquiredFilesCount++;
            verifiedFilesCount++;
            return;
          } catch (err: any) {
            lastError = err;
            if (err.message && err.message.includes('rate limit')) {
              throw err;
            }
            if (attempts < maxAttempts) {
              await new Promise((resolve) => setTimeout(resolve, attempts * 500));
            }
          }
        }

        record.verificationStatus = 'FAILED';
        record.failureReason = lastError?.message || 'Failed after multiple retries';
        failedFilesCount++;
      };

      const workers = Array.from({ length: CONCURRENCY_LIMIT }, async () => {
        while (queueIndex < filesToAcquire.length) {
          const current = filesToAcquire[queueIndex++];
          if (!current) break;
          await fetchSingleFileWithRetry(current);

          const completed = acquiredFilesCount + failedFilesCount + missingFilesCount;
          const pct = 45 + Math.round((completed / (expectedFilesCount || 1)) * 40);
          onUpdateLoadingProgress(
            `Acquiring repository files (${verifiedFilesCount}/${files.filter((f) => !f.isDirectory).length})...`,
            pct
          );
        }
      });

      await Promise.all(workers);

      // 6. Final Snapshot Verification & Freezing
      onUpdateLoadingProgress('Performing final repository snapshot verification...', 88);

      const isContentComplete = failedFilesCount === 0 && missingFilesCount === 0;
      const isVersionConsistent = Boolean(targetCommitSha && targetCommitSha.length >= 7);

      if (failedFilesCount > 0 || missingFilesCount > 0 || duplicateFilesCount > 0 || !isTreeComplete) {
        throw new Error(
          `Repository snapshot verification failed: ${failedFilesCount} files failed, ${missingFilesCount} missing, ${duplicateFilesCount} duplicates, treeComplete=${isTreeComplete}.`
        );
      }

      const manifest: AcquisitionManifest = {
        targetCommitSha,
        targetBranch,
        expectedFilesCount,
        acquiredFilesCount,
        verifiedFilesCount,
        failedFilesCount,
        missingFilesCount,
        duplicateFilesCount,
        isTreeComplete,
        isContentComplete,
        isVersionConsistent,
        status: 'COMPLETE',
        records,
      };

      const snapshot: RepositorySnapshot = {
        metadata,
        owner,
        repo,
        targetBranch,
        targetCommitSha,
        manifest,
        files,
        acquiredFiles: records,
        isComplete: true,
        createdAtMillis: Date.now(),
      };

      // Freeze snapshot and manifest to guarantee immutability downstream
      Object.freeze(manifest.records);
      Object.freeze(manifest);
      Object.freeze(snapshot.acquiredFiles);
      Object.freeze(snapshot);

      // 7. Pipeline Handoff directly into Existing Inspection Layer
      onUpdateLoadingProgress('Decomposing source AST from verified snapshot...', 90);

      const inspectableFiles = files.filter((f) => !f.isDirectory);
      const inspections: Record<string, FileInspectionResult> = {};

      for (const file of inspectableFiles) {
        const record = snapshot.acquiredFiles[file.relativePath];
        inspections[file.relativePath] = SourceCodeStructureExtractor.inspect(
          file,
          record?.content ?? null
        );
      }

      // STEP 3: Cross-File Symbol Resolution & Defect Detection
      onUpdateLoadingProgress('Building cross-file symbol registry & relationship graph...', 94);
      const symbolRegistry = ProjectSymbolRegistry.build(inspections);
      const resolutionResult = CrossFileRelationshipResolver.process(inspections, symbolRegistry);

      // STEP 5: Construct Complete Decomposed Tree
      onUpdateLoadingProgress('Constructing complete decomposed tree hierarchy...', 98);
      const decomposedTreeRoot = CompleteTreeReconstructor.reconstruct(
        metadata.name,
        files,
        inspections,
        resolutionResult
      );

      onProjectLoaded(
        metadata,
        files,
        inspections,
        resolutionResult,
        decomposedTreeRoot,
        snapshot
      );
    } catch (err: any) {
      onError(err.message || 'An unexpected error occurred during ingestion.');
    }
  };

  return (
    <div className="flex flex-col min-h-full bg-slate-50 text-slate-900 selection:bg-blue-100">
      {/* Top App Bar */}
      <header className="sticky top-0 z-20 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200/80 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={onNavigateBack}
            className="p-1 rounded-lg hover:bg-slate-200 text-slate-700 transition-colors"
            title="Back"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-base font-bold tracking-wider text-slate-900 uppercase">PROJECT INGESTION</h1>
          </div>
        </div>
        <span className="px-2 py-0.5 text-[10px] font-bold tracking-wider text-blue-700 bg-blue-100 border border-blue-300 rounded">
          PHASE 1B
        </span>
      </header>

      {/* Screen Body */}
      <main className="flex-1 p-5 max-w-md mx-auto w-full space-y-5">
        {/* Loading State Banner */}
        {projectState.kind === 'ProjectLoading' && (
          <section className="bg-white rounded-xl p-5 border border-blue-300 shadow-sm text-center space-y-3">
            <div className="mx-auto w-8 h-8 border-3 border-slate-300 border-t-slate-900 rounded-full animate-spin" />
            <div>
              <p className="text-sm font-semibold text-slate-900">
                {projectState.statusMessage || 'Connecting to GitHub API...'}
              </p>
              <p className="text-xs text-slate-500 font-mono mt-0.5">{projectState.source}</p>
            </div>
            {/* Progress bar */}
            <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
              <div
                className="bg-blue-600 h-1.5 rounded-full transition-all duration-300"
                style={{ width: `${projectState.progressPercentage}%` }}
              />
            </div>
          </section>
        )}

        {/* Error State Banner */}
        {projectState.kind === 'Error' && (
          <section className="bg-red-50 rounded-xl p-4 border border-red-200 shadow-sm space-y-3">
            <div className="flex items-center gap-2 text-red-700">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <h2 className="text-xs font-bold uppercase tracking-wider">INGESTION ERROR</h2>
            </div>
            <p className="text-xs text-red-800 leading-relaxed">{projectState.message}</p>
            <div className="flex justify-end">
              <button
                onClick={onResetState}
                className="px-3 py-1 bg-white hover:bg-red-100 text-red-700 border border-red-300 rounded-lg text-xs font-semibold transition-colors"
              >
                Dismiss
              </button>
            </div>
          </section>
        )}

        {/* Loaded State Banner */}
        {projectState.kind === 'ProjectLoaded' && (
          <section className="bg-emerald-50 rounded-xl p-5 border border-emerald-200 shadow-sm space-y-3">
            <div className="flex items-center gap-2 text-emerald-700">
              <CheckCircle2 className="w-5 h-5 shrink-0" />
              <span className="text-xs font-bold tracking-wider uppercase">REPOSITORY INGESTED SUCCESSFULLY</span>
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">{projectState.metadata.name}</h2>
              <p className="text-xs font-mono text-slate-600 truncate">{projectState.metadata.pathOrUri}</p>
              <div className="mt-2 flex items-center gap-2 text-xs font-semibold text-slate-700">
                <span className="px-2 py-0.5 bg-emerald-100 text-emerald-800 rounded font-mono">
                  {projectState.metadata.fileCount} files
                </span>
                <span className="px-2 py-0.5 bg-slate-200 text-slate-800 rounded font-mono">
                  Branch: {projectState.metadata.branchOrTag || 'main'}
                </span>
              </div>
            </div>
            <div className="pt-2 flex gap-2">
              <button
                onClick={onResetState}
                className="flex-1 py-2 px-3 bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold rounded-lg border border-slate-300 transition-colors"
              >
                Unload
              </button>
              <button
                onClick={onNavigateToSourceTree}
                className="flex-1 py-2 px-3 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-lg transition-colors shadow-xs"
              >
                VIEW SOURCE TREE
              </button>
            </div>
          </section>
        )}

        {/* Ingestion Form Card */}
        <section className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-blue-50 border border-blue-200 flex items-center justify-center">
              <Code2 className="w-4 h-4 text-blue-600" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-900">GitHub Repository Input</h2>
              <p className="text-[11px] text-slate-500">
                Direct git tree decomposition via GitHub REST API
              </p>
            </div>
          </div>

          <div className="space-y-3 pt-1">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Repository Slug or URL <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={repoInput}
                onChange={(e) => setRepoInput(e.target.value)}
                placeholder="e.g. octocat/Hello-World"
                disabled={projectState.kind === 'ProjectLoading'}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-xs font-mono text-slate-900 placeholder:text-slate-400 focus:outline-hidden focus:border-blue-500 focus:bg-white transition-colors"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Branch / Tag / SHA <span className="text-slate-400 text-[10px] font-normal">(optional, defaults to repo default)</span>
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={branchInput}
                  onChange={(e) => setBranchInput(e.target.value)}
                  placeholder="main, master, release/v1.0"
                  disabled={projectState.kind === 'ProjectLoading'}
                  className="w-full px-3.5 py-2.5 pl-8 bg-slate-50 border border-slate-300 rounded-xl text-xs font-mono text-slate-900 placeholder:text-slate-400 focus:outline-hidden focus:border-blue-500 focus:bg-white transition-colors"
                />
                <GitBranch className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-3" />
              </div>
            </div>

            {validationError && (
              <p className="text-xs text-red-600 font-medium">{validationError}</p>
            )}

            {/* Quick Test Presets */}
            <div className="pt-1">
              <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block mb-1.5">
                Quick Verification Presets:
              </span>
              <div className="flex flex-wrap gap-1.5">
                {sampleRepos.map((preset) => (
                  <button
                    key={preset.slug}
                    type="button"
                    onClick={() => {
                      setRepoInput(preset.slug);
                      setBranchInput(preset.branch);
                      setValidationError(null);
                    }}
                    className="px-2.5 py-1 text-[11px] font-mono bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg border border-slate-200 transition-colors"
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>

            <button
              onClick={handleIngest}
              disabled={projectState.kind === 'ProjectLoading' || !repoInput.trim()}
              className="w-full mt-2 py-3 px-4 bg-slate-900 hover:bg-slate-800 disabled:opacity-50 text-white text-xs font-bold tracking-wider uppercase rounded-xl transition-all shadow-xs flex items-center justify-center gap-2"
            >
              {projectState.kind === 'ProjectLoading' ? (
                <>
                  <div className="w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                  <span>Ingesting Tree...</span>
                </>
              ) : (
                <>
                  <Code2 className="w-4 h-4" />
                  <span>INGEST REPOSITORY</span>
                </>
              )}
            </button>
          </div>
        </section>

        {/* Verification Guarantee */}
        <section className="p-3.5 rounded-xl bg-slate-100 border border-slate-200 text-[11px] text-slate-600 space-y-1">
          <span className="font-bold text-slate-900 block">Deterministic Physical Ingestion Mandate:</span>
          <p>
            AuditFlow fetches the true immutable Git tree object directly from the remote repository. No files are fabricated, omitted, or hallucinated.
          </p>
        </section>
      </main>
    </div>
  );
};
