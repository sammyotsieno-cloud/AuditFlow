import React, { useState, useMemo } from 'react';
import {
  ArrowLeft,
  Search,
  X,
  Folder,
  FileCode2,
  RefreshCw,
  FolderClosed,
  ChevronRight,
  ChevronDown,
  Sparkles,
  Layers,
  FileText,
  Copy,
  Check,
  Code2,
  GitBranch,
  ShieldAlert,
  SlidersHorizontal,
  Box,
  CornerDownRight,
  Braces,
  Binary
} from 'lucide-react';
import {
  ProjectState,
  DecomposedTreeNode,
  CompleteTreeLine,
} from '../types';
import {
  reconstructProjectTree,
  generateTreeLines,
  ProjectTreeLine,
  formatBytes
} from '../utils/projectTreeReconstructor';
import { CompleteTreeReconstructor } from '../utils/completeTreeReconstructor';

interface SourceTreeScreenProps {
  projectState: ProjectState;
  onNavigateBack: () => void;
  onNavigateToProjectInput: () => void;
}

type TreeDisplayMode = 'COMPLETE_DECOMPOSED' | 'PHYSICAL_ONLY';

export const SourceTreeScreen: React.FC<SourceTreeScreenProps> = ({
  projectState,
  onNavigateBack,
  onNavigateToProjectInput,
}) => {
  const [displayMode, setDisplayMode] = useState<TreeDisplayMode>('COMPLETE_DECOMPOSED');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPhysicalFile, setSelectedPhysicalFile] = useState<ProjectTreeLine | null>(null);
  const [selectedDecomposedNode, setSelectedDecomposedNode] = useState<DecomposedTreeNode | null>(null);
  const [copiedText, setCopiedText] = useState(false);

  // Set of expanded node IDs for the Decomposed Tree
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(() => {
    return new Set<string>(['root']);
  });

  // Physical tree lines
  const physicalTreeLines = useMemo(() => {
    if (projectState.kind !== 'ProjectLoaded') return [];
    const root = reconstructProjectTree(projectState.metadata.name, projectState.files);
    return generateTreeLines(root);
  }, [projectState]);

  // Root decomposed tree
  const decomposedRoot = useMemo(() => {
    if (projectState.kind !== 'ProjectLoaded') return null;
    if (projectState.decomposedTreeRoot) return projectState.decomposedTreeRoot;
    // Fallback if not cached in state: reconstruct immediately
    return CompleteTreeReconstructor.reconstruct(
      projectState.metadata.name,
      projectState.files,
      projectState.inspections || {},
      projectState.resolutionResult
    );
  }, [projectState]);

  // Collect all node IDs for Expand All
  const allDecomposedNodeIds = useMemo(() => {
    const ids = new Set<string>();
    if (!decomposedRoot) return ids;

    const traverse = (node: DecomposedTreeNode) => {
      if (node.children.length > 0) {
        ids.add(node.id);
        node.children.forEach(traverse);
      }
    };
    traverse(decomposedRoot);
    return ids;
  }, [decomposedRoot]);

  // Expand default prominent nodes on load
  React.useEffect(() => {
    if (decomposedRoot) {
      const defaultExpanded = new Set<string>(['root']);
      // Auto-expand first 2 levels so the user immediately sees the tree structure
      decomposedRoot.children.forEach((c) => {
        defaultExpanded.add(c.id);
        c.children.forEach((gc) => {
          defaultExpanded.add(gc.id);
          // Auto expand files
          if (gc.isFile) {
            defaultExpanded.add(gc.id);
            gc.children.forEach((sub) => {
              if (sub.kind === 'DECLARATIONS_GROUP') defaultExpanded.add(sub.id);
            });
          }
        });
      });
      setExpandedNodeIds(defaultExpanded);
    }
  }, [decomposedRoot]);

  // Flatten decomposed tree according to expanded state
  const decomposedTreeLines = useMemo(() => {
    if (!decomposedRoot) return [];
    return CompleteTreeReconstructor.generateLines(decomposedRoot, expandedNodeIds);
  }, [decomposedRoot, expandedNodeIds]);

  // Filtered physical lines
  const displayedPhysicalLines = useMemo(() => {
    if (!searchQuery.trim()) return physicalTreeLines;
    const q = searchQuery.trim().toLowerCase();
    return physicalTreeLines.filter(
      (l) => l.isRoot || l.displayName.toLowerCase().includes(q) || l.relativePath.toLowerCase().includes(q)
    );
  }, [physicalTreeLines, searchQuery]);

  // Filtered decomposed lines
  const displayedDecomposedLines = useMemo(() => {
    if (!searchQuery.trim()) return decomposedTreeLines;
    const q = searchQuery.trim().toLowerCase();
    return decomposedTreeLines.filter(
      (l) =>
        l.depth === 0 ||
        l.displayName.toLowerCase().includes(q) ||
        l.node.relativePath.toLowerCase().includes(q) ||
        (l.node.fqn && l.node.fqn.toLowerCase().includes(q))
    );
  }, [decomposedTreeLines, searchQuery]);

  const toggleNodeExpansion = (nodeId: string) => {
    setExpandedNodeIds((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  };

  const handleExpandAll = () => {
    setExpandedNodeIds(new Set(allDecomposedNodeIds));
  };

  const handleCollapseAll = () => {
    setExpandedNodeIds(new Set(['root']));
  };

  const handleCopyFullTree = () => {
    const lines =
      displayMode === 'COMPLETE_DECOMPOSED'
        ? displayedDecomposedLines.map((l) => `${l.prefix}${l.displayName}`).join('\n')
        : displayedPhysicalLines.map((l) => `${l.prefix}${l.displayName}`).join('\n');

    navigator.clipboard.writeText(lines);
    setCopiedText(true);
    setTimeout(() => setCopiedText(false), 2000);
  };

  const handleCopyString = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(true);
    setTimeout(() => setCopiedText(false), 2000);
  };

  const getBadgeClass = (color?: string) => {
    switch (color) {
      case 'blue':
        return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'purple':
        return 'bg-purple-100 text-purple-800 border-purple-200';
      case 'green':
        return 'bg-emerald-100 text-emerald-800 border-emerald-200';
      case 'amber':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      case 'rose':
        return 'bg-rose-100 text-rose-800 border-rose-200';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
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
            title="Back to Ingestion"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-base font-bold tracking-wider text-slate-900 uppercase">
              {displayMode === 'COMPLETE_DECOMPOSED' ? 'COMPLETE TREE' : 'PHYSICAL TREE'}
            </h1>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={onNavigateToProjectInput}
            className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-700 transition-colors"
            title="Ingest another repository"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
          <span
            className={`px-2 py-0.5 text-[10px] font-bold tracking-wider rounded border ${
              displayMode === 'COMPLETE_DECOMPOSED'
                ? 'text-purple-700 bg-purple-100 border-purple-300'
                : 'text-emerald-700 bg-emerald-100 border-emerald-300'
            }`}
          >
            {displayMode === 'COMPLETE_DECOMPOSED' ? 'LEVEL A+B+C' : 'LEVEL A'}
          </span>
        </div>
      </header>

      {/* Screen Body */}
      {projectState.kind === 'ProjectLoaded' ? (
        <main className="flex-1 flex flex-col p-4 max-w-xl mx-auto w-full space-y-3">
          {/* Project Summary Header Bar */}
          <div className="bg-white rounded-xl p-3 border border-slate-200 shadow-xs flex items-center justify-between">
            <div className="truncate mr-2">
              <span className="text-xs font-bold text-slate-900 block truncate">
                {projectState.metadata.name}
              </span>
              <span className="text-[10px] font-mono text-slate-500 truncate block">
                {projectState.metadata.branchOrTag
                  ? `branch: ${projectState.metadata.branchOrTag}`
                  : 'deterministic source tree'}
              </span>
            </div>
            <div className="text-right shrink-0">
              <span className="text-xs font-bold font-mono text-slate-900">
                {projectState.metadata.fileCount} files
              </span>
              <span className="text-[10px] font-mono text-slate-500 block">
                {formatBytes(projectState.metadata.totalSizeBytes)}
              </span>
            </div>
          </div>

          {/* Tree Mode Selector Toggle */}
          <div className="flex items-center bg-slate-200/80 p-1 rounded-xl text-xs font-semibold">
            <button
              onClick={() => setDisplayMode('COMPLETE_DECOMPOSED')}
              className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-lg transition-colors ${
                displayMode === 'COMPLETE_DECOMPOSED'
                  ? 'bg-white text-purple-950 shadow-xs border border-slate-200/50'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Layers className="w-3.5 h-3.5 text-purple-600" />
              <span>Complete Decomposed Tree</span>
            </button>
            <button
              onClick={() => setDisplayMode('PHYSICAL_ONLY')}
              className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-lg transition-colors ${
                displayMode === 'PHYSICAL_ONLY'
                  ? 'bg-white text-slate-950 shadow-xs border border-slate-200/50'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Folder className="w-3.5 h-3.5 text-blue-600" />
              <span>Physical File Tree</span>
            </button>
          </div>

          {/* Station 4 Resolution Summary (when in Complete Decomposed Mode) */}
          {displayMode === 'COMPLETE_DECOMPOSED' && projectState.resolutionResult && (
            <div className="bg-purple-50/60 rounded-xl p-3 border border-purple-200/70 shadow-xs space-y-1.5">
              <div className="flex items-center justify-between text-[11px] font-bold text-purple-900">
                <span className="flex items-center gap-1.5">
                  <Code2 className="w-3.5 h-3.5 text-purple-600" />
                  Station 4: Cross-File Symbol Resolution
                </span>
                <span className="text-[10px] font-mono px-1.5 py-0.5 bg-purple-200/60 rounded text-purple-800">
                  {projectState.resolutionResult.indexedSymbolCount} Symbols
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-[10px] font-mono text-purple-800 pt-1 border-t border-purple-200/50">
                <div>
                  <span className="text-purple-600 block">Local Calls:</span>
                  <strong className="text-xs">{projectState.resolutionResult.localCallsCount}</strong>
                </div>
                <div>
                  <span className="text-purple-600 block">SDK Calls:</span>
                  <strong className="text-xs">{projectState.resolutionResult.externalSdkCallsCount}</strong>
                </div>
                <div>
                  <span className="text-purple-600 block">Findings / Defects:</span>
                  <strong className={`text-xs ${projectState.resolutionResult.defects.length > 0 ? 'text-amber-700' : 'text-emerald-700'}`}>
                    {projectState.resolutionResult.defects.length}
                  </strong>
                </div>
              </div>
            </div>
          )}

          {/* Real-time Path Filter Input & Action Buttons */}
          <div className="flex items-center gap-2">
            <div className="relative flex-1">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={
                  displayMode === 'COMPLETE_DECOMPOSED'
                    ? 'Filter classes, functions, calls, files...'
                    : 'Search physical files or directories...'
                }
                className="w-full pl-8 pr-8 py-2 bg-white border border-slate-300 rounded-xl text-xs font-mono text-slate-900 placeholder:text-slate-400 focus:outline-hidden focus:border-blue-500 transition-colors shadow-2xs"
              />
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-2.5 top-2.5 text-slate-400 hover:text-slate-600"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            {displayMode === 'COMPLETE_DECOMPOSED' && (
              <div className="flex items-center gap-1">
                <button
                  onClick={handleExpandAll}
                  className="px-2 py-2 bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 rounded-xl text-[10px] font-semibold transition-colors shrink-0 shadow-2xs"
                  title="Expand All Nodes"
                >
                  Expand All
                </button>
                <button
                  onClick={handleCollapseAll}
                  className="px-2 py-2 bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 rounded-xl text-[10px] font-semibold transition-colors shrink-0 shadow-2xs"
                  title="Collapse All Nodes"
                >
                  Collapse
                </button>
              </div>
            )}

            <button
              onClick={handleCopyFullTree}
              className="p-2 bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 rounded-xl transition-colors shrink-0 shadow-2xs"
              title="Copy Complete ASCII Tree"
            >
              {copiedText ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
            </button>
          </div>

          {/* Node Count & Milestone State */}
          <div className="flex items-center justify-between px-1 text-[11px] text-slate-500 font-mono">
            <span>
              {displayMode === 'COMPLETE_DECOMPOSED'
                ? `Showing ${displayedDecomposedLines.length} decomposed nodes`
                : `Showing ${displayedPhysicalLines.length} of ${physicalTreeLines.length} nodes`}
            </span>
            <span className="text-purple-700 font-semibold">
              {displayMode === 'COMPLETE_DECOMPOSED' ? 'Complete AST Decomposition' : 'Deterministic Ordering'}
            </span>
          </div>

          {/* Canonical Tree View Container */}
          <div className="flex-1 bg-white rounded-xl border border-slate-200 shadow-xs p-3 overflow-x-auto min-h-[380px] max-h-[560px]">
            {displayMode === 'COMPLETE_DECOMPOSED' ? (
              /* COMPLETE DECOMPOSED TREE VIEW */
              displayedDecomposedLines.length > 0 ? (
                <div className="space-y-0.5 font-mono text-[11px] leading-relaxed">
                  {displayedDecomposedLines.map((line) => {
                    const isSelected = selectedDecomposedNode?.id === line.node.id;
                    return (
                      <div
                        key={line.id}
                        onClick={() => {
                          if (line.hasChildren) {
                            toggleNodeExpansion(line.node.id);
                          }
                          setSelectedDecomposedNode(line.node);
                        }}
                        className={`flex items-center py-0.5 px-1.5 rounded transition-colors cursor-pointer ${
                          isSelected ? 'bg-purple-100 text-purple-950 font-medium' : 'hover:bg-slate-100'
                        }`}
                      >
                        {/* Structural ASCII Branch */}
                        {line.prefix && (
                          <span className="text-slate-400 select-none whitespace-pre">
                            {line.prefix}
                          </span>
                        )}

                        {/* Expand / Collapse Indicator */}
                        {line.hasChildren && (
                          <span className="mr-1 text-slate-500 hover:text-slate-900 select-none">
                            {line.isExpanded ? (
                              <ChevronDown className="w-3 h-3 inline-block" />
                            ) : (
                              <ChevronRight className="w-3 h-3 inline-block" />
                            )}
                          </span>
                        )}

                        {/* Node Display Name */}
                        <span
                          className={`truncate ${
                            line.depth === 0
                              ? 'font-bold text-slate-950 text-xs'
                              : line.node.isDirectory
                              ? 'font-semibold text-slate-800'
                              : line.node.isFile
                              ? 'font-semibold text-blue-900'
                              : line.node.kind === 'CLASS' || line.node.kind === 'INTERFACE' || line.node.kind === 'OBJECT'
                              ? 'font-bold text-purple-900'
                              : line.node.kind === 'FUNCTION'
                              ? 'font-medium text-slate-900'
                              : line.node.kind === 'PROPERTY'
                              ? 'font-normal text-emerald-900'
                              : line.node.kind === 'CALL'
                              ? 'font-normal text-amber-900'
                              : 'text-slate-700'
                          }`}
                        >
                          {line.displayName}
                        </span>

                        {/* Node Badge */}
                        {line.node.badge && (
                          <span
                            className={`ml-auto pl-1.5 text-[9px] font-mono px-1.5 py-0.2 rounded border shrink-0 select-none ${getBadgeClass(
                              line.node.badgeColor
                            )}`}
                          >
                            {line.node.badge}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="p-8 text-center space-y-2">
                  <Search className="w-6 h-6 text-slate-300 mx-auto" />
                  <p className="text-xs text-slate-500">No nodes matched "{searchQuery}"</p>
                </div>
              )
            ) : (
              /* PHYSICAL LEVEL A TREE VIEW */
              displayedPhysicalLines.length > 0 ? (
                <div className="space-y-0.5 font-mono text-[11px] leading-relaxed">
                  {displayedPhysicalLines.map((line, idx) => (
                    <div
                      key={`${line.relativePath}-${idx}`}
                      onClick={() => {
                        if (!line.isDirectory && !line.isRoot) {
                          setSelectedPhysicalFile(line);
                        }
                      }}
                      className={`flex items-center py-0.5 px-1.5 rounded transition-colors ${
                        !line.isDirectory && !line.isRoot
                          ? 'cursor-pointer hover:bg-slate-100'
                          : ''
                      } ${
                        selectedPhysicalFile?.relativePath === line.relativePath
                          ? 'bg-blue-50 text-blue-900'
                          : ''
                      }`}
                    >
                      {line.prefix && (
                        <span className="text-slate-400 select-none whitespace-pre">
                          {line.prefix}
                        </span>
                      )}

                      <span
                        className={`truncate ${
                          line.isRoot
                            ? 'font-bold text-slate-950 text-xs'
                            : line.isDirectory
                            ? 'font-semibold text-slate-800'
                            : 'font-normal text-slate-900'
                        }`}
                      >
                        {line.displayName}
                      </span>

                      {!line.isDirectory && !line.isRoot && line.sizeBytes > 0 && (
                        <span className="ml-auto pl-2 text-[10px] text-slate-400 shrink-0 select-none">
                          {formatBytes(line.sizeBytes)}
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-center space-y-2">
                  <Search className="w-6 h-6 text-slate-300 mx-auto" />
                  <p className="text-xs text-slate-500">No nodes matched "{searchQuery}"</p>
                </div>
              )
            )}
          </div>

          {/* Detail Inspector Card (For Decomposed Node) */}
          {displayMode === 'COMPLETE_DECOMPOSED' && selectedDecomposedNode && (
            <div className="bg-white rounded-xl p-3.5 border border-purple-200 shadow-sm space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-slate-900 font-bold text-xs truncate">
                  <Box className="w-4 h-4 text-purple-600 shrink-0" />
                  <span className="truncate">{selectedDecomposedNode.displayName}</span>
                  <span
                    className={`text-[9px] px-1.5 py-0.5 rounded border uppercase font-mono shrink-0 ${getBadgeClass(
                      selectedDecomposedNode.badgeColor
                    )}`}
                  >
                    {selectedDecomposedNode.kind}
                  </span>
                </div>
                <button
                  onClick={() => setSelectedDecomposedNode(null)}
                  className="text-slate-400 hover:text-slate-600 p-0.5"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* FQN or Path */}
              <div className="bg-slate-50 rounded-lg p-2 font-mono text-[10px] text-slate-700 flex items-center justify-between gap-2">
                <span className="truncate">
                  {selectedDecomposedNode.fqn || selectedDecomposedNode.relativePath}
                </span>
                <button
                  onClick={() =>
                    handleCopyString(selectedDecomposedNode.fqn || selectedDecomposedNode.relativePath)
                  }
                  className="text-slate-500 hover:text-slate-900 shrink-0"
                  title="Copy"
                >
                  {copiedText ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>

              {/* Parameters & Return Type if function */}
              {selectedDecomposedNode.kind === 'FUNCTION' && (
                <div className="text-[11px] text-slate-700 space-y-1 bg-slate-50/60 p-2 rounded-lg border border-slate-100">
                  <div className="flex items-center justify-between">
                    <span>Return Type:</span>
                    <strong className="font-mono text-emerald-700">
                      {selectedDecomposedNode.returnType || 'Unit'}
                    </strong>
                  </div>
                  {selectedDecomposedNode.parameters && selectedDecomposedNode.parameters.length > 0 && (
                    <div>
                      <span className="block font-semibold mb-0.5">Parameters:</span>
                      <ul className="list-disc pl-4 font-mono text-[10px] space-y-0.5">
                        {selectedDecomposedNode.parameters.map((p, i) => (
                          <li key={i}>
                            {p.name}: {p.type} {p.hasDefaultValue ? '(default)' : ''}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* Calls list if present */}
              {selectedDecomposedNode.calls && selectedDecomposedNode.calls.length > 0 && (
                <div className="text-[10px] font-mono text-slate-700 bg-amber-50/60 p-2 rounded-lg border border-amber-200/60 space-y-1">
                  <span className="font-bold text-amber-900 block">Outbound Calls ({selectedDecomposedNode.calls.length}):</span>
                  <div className="space-y-0.5">
                    {selectedDecomposedNode.calls.map((c, i) => (
                      <div key={i} className="flex items-center justify-between">
                        <span>{c.receiverName ? `${c.receiverName}.` : ''}{c.calleeName}()</span>
                        <span className="text-slate-500">{c.resolvedTargetFqn || (c.isLocalProjectSymbol ? 'local' : 'sdk')}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Defect Warning if present */}
              {selectedDecomposedNode.defectWarning && (
                <div className="bg-amber-50 rounded-lg p-2 text-[11px] text-amber-800 border border-amber-200 flex items-start gap-1.5">
                  <ShieldAlert className="w-3.5 h-3.5 text-amber-600 shrink-0 mt-0.5" />
                  <span>{selectedDecomposedNode.defectWarning}</span>
                </div>
              )}

              <div className="flex items-center justify-between text-[11px] text-slate-600 pt-1 border-t border-slate-100">
                <span>
                  Line: <strong className="text-slate-900 font-mono">{selectedDecomposedNode.startLine ?? 'N/A'}</strong>
                </span>
                <span>
                  Children: <strong className="text-slate-900">{selectedDecomposedNode.children.length}</strong>
                </span>
              </div>
            </div>
          )}

          {/* Physical File Inspection Card (when in Physical Mode) */}
          {displayMode === 'PHYSICAL_ONLY' && selectedPhysicalFile && (
            <div className="bg-white rounded-xl p-3.5 border border-blue-200 shadow-sm space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-slate-900 font-bold text-xs truncate">
                  <FileCode2 className="w-4 h-4 text-blue-600 shrink-0" />
                  <span className="truncate">{selectedPhysicalFile.displayName}</span>
                </div>
                <button
                  onClick={() => setSelectedPhysicalFile(null)}
                  className="text-slate-400 hover:text-slate-600 p-0.5"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="bg-slate-50 rounded-lg p-2 font-mono text-[10px] text-slate-700 flex items-center justify-between gap-2">
                <span className="truncate">{selectedPhysicalFile.relativePath}</span>
                <button
                  onClick={() => handleCopyString(selectedPhysicalFile.relativePath)}
                  className="text-slate-500 hover:text-slate-900 shrink-0"
                  title="Copy path"
                >
                  {copiedText ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>

              <div className="flex items-center justify-between text-[11px] text-slate-600">
                <span>
                  Size: <strong className="text-slate-900">{formatBytes(selectedPhysicalFile.sizeBytes)}</strong>
                </span>
                <span>
                  Type: <strong className="text-slate-900 font-mono">.{selectedPhysicalFile.extension || 'none'}</strong>
                </span>
                <button
                  onClick={() => setDisplayMode('COMPLETE_DECOMPOSED')}
                  className="px-2 py-0.5 bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 rounded font-semibold text-[10px] transition-colors"
                >
                  Decompose AST &rarr;
                </button>
              </div>
            </div>
          )}
        </main>
      ) : (
        /* Empty State */
        <main className="flex-1 flex flex-col items-center justify-center p-6 text-center space-y-4 max-w-md mx-auto w-full">
          <div className="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center border border-slate-200">
            <FolderClosed className="w-8 h-8 text-slate-400" />
          </div>

          <div>
            <h2 className="text-base font-bold text-slate-900">No Project Loaded</h2>
            <p className="mt-1 text-xs text-slate-500 max-w-xs mx-auto">
              Ingest a GitHub repository or local directory to reconstruct and inspect its verified physical source tree.
            </p>
          </div>

          <button
            onClick={onNavigateToProjectInput}
            className="px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-xl transition-all shadow-xs"
          >
            INGEST A PROJECT
          </button>
        </main>
      )}
    </div>
  );
};
