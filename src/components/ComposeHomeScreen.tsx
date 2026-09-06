import React from 'react';
import {
  Shield,
  Lock,
  Code2,
  FolderClosed,
  ChevronRight,
  Sparkles,
  Info,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import { ProjectState, NAV_DESTINATIONS, NavDestination, AUDIT_PRINCIPLES } from '../types';

interface ComposeHomeScreenProps {
  projectState: ProjectState;
  onLocalProjectClick: () => void;
  onGitHubRepoClick: () => void;
  onNavigateToDestination: (dest: NavDestination) => void;
  onUnloadProject: () => void;
}

export const ComposeHomeScreen: React.FC<ComposeHomeScreenProps> = ({
  projectState,
  onLocalProjectClick,
  onGitHubRepoClick,
  onNavigateToDestination,
  onUnloadProject,
}) => {
  return (
    <div className="flex flex-col min-h-full bg-slate-50 text-slate-900 selection:bg-blue-100">
      {/* Top Bar (matches Compose CenterAlignedTopAppBar) */}
      <header className="sticky top-0 z-20 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200/80 px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-900 flex items-center justify-center shadow-xs">
              <Shield className="w-4 h-4 text-blue-500" />
            </div>
            <div>
              <span className="text-base font-bold tracking-widest text-slate-900">AUDITFLOW</span>
            </div>
          </div>
          <span className="px-2.5 py-1 text-[11px] font-bold tracking-wider text-blue-700 bg-blue-100/90 border border-blue-300/80 rounded-md">
            PHASE 1B
          </span>
        </div>
      </header>

      {/* Screen Body */}
      <main className="flex-1 p-5 max-w-md mx-auto w-full space-y-6">
        {/* Title & Tagline */}
        <section className="text-center pt-2">
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">AUDITFLOW</h1>
          <p className="mt-1 text-sm font-medium text-slate-600">
            Source Code Audit &amp; Verification
          </p>
        </section>

        {/* Epistemic Core Invariant Box */}
        <section
          id="epistemic-invariant-card"
          className="bg-white rounded-xl p-4 border border-slate-200 shadow-xs space-y-2"
        >
          <div className="flex items-center gap-2 text-slate-900">
            <Lock className="w-4 h-4 text-slate-900 shrink-0" />
            <h2 className="text-xs font-bold tracking-wider uppercase">CORE AUDIT INVARIANT</h2>
          </div>
          <div className="bg-slate-900 rounded-lg p-3 text-[11px] font-mono text-slate-200 leading-relaxed overflow-x-auto">
            EXISTS ≠ CONNECTED ≠ EXECUTED ≠ VALIDATED ≠ VERIFIED ≠ PRODUCES_EXPECTED_RESULT
          </div>
          <p className="text-[11px] text-slate-500 italic">
            AuditFlow strictly forbids displaying synthetic scores, fake trees, or simulated verification.
          </p>
        </section>

        {/* Project State Container */}
        <section
          id="current-project-state-card"
          className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm text-center space-y-5"
        >
          {/* Status Icon */}
          <div className="mx-auto w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center border border-slate-200">
            {projectState.kind === 'NoProject' && (
              <Code2 className="w-8 h-8 text-slate-400" />
            )}
            {projectState.kind === 'ProjectLoading' && (
              <div className="w-6 h-6 border-2 border-slate-400 border-t-slate-900 rounded-full animate-spin" />
            )}
            {projectState.kind === 'ProjectLoaded' && (
              <CheckCircle2 className="w-8 h-8 text-emerald-600" />
            )}
            {projectState.kind === 'Error' && (
              <AlertCircle className="w-8 h-8 text-red-600" />
            )}
          </div>

          <div>
            <h2 className="text-xl font-bold text-slate-900">
              {projectState.kind === 'NoProject' && 'No project loaded.'}
              {projectState.kind === 'ProjectLoading' && 'Loading Project...'}
              {projectState.kind === 'ProjectLoaded' && projectState.metadata.name}
              {projectState.kind === 'Error' && 'Project State Error'}
            </h2>
            <p className="mt-1.5 text-xs text-slate-500 max-w-xs mx-auto">
              {projectState.kind === 'NoProject' &&
                'No source code, repository connection, or audit artifacts are loaded.'}
              {projectState.kind === 'ProjectLoading' &&
                (projectState.statusMessage || 'Connecting to GitHub API...')}
              {projectState.kind === 'ProjectLoaded' &&
                `${projectState.metadata.fileCount} files • ${projectState.files.length} structural nodes`}
              {projectState.kind === 'Error' && projectState.message}
            </p>
          </div>

          <hr className="border-slate-100" />

          {/* Action Entry Points */}
          {projectState.kind === 'ProjectLoaded' ? (
            <div className="space-y-3">
              <p className="text-xs font-semibold text-slate-700 text-left">
                Verified Ingested Project:
              </p>
              <div className="flex gap-2">
                <button
                  id="btn-unload-project"
                  onClick={onUnloadProject}
                  className="flex-1 py-3 px-4 bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold rounded-xl border border-slate-300 transition-all shadow-xs"
                >
                  UNLOAD
                </button>
                <button
                  id="btn-open-source-tree"
                  onClick={() => {
                    const sourceTreeDest = NAV_DESTINATIONS.find((d) => d.id === 'source_tree');
                    if (sourceTreeDest) onNavigateToDestination(sourceTreeDest);
                  }}
                  className="flex-1 py-3 px-4 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold tracking-wider uppercase rounded-xl transition-all shadow-xs"
                >
                  SOURCE TREE
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-xs font-semibold text-slate-700 text-left">
                Choose how you want to provide a project:
              </p>

              <button
                id="btn-local-project"
                onClick={onLocalProjectClick}
                className="w-full flex items-center justify-center gap-2.5 py-3.5 px-4 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all shadow-xs active:scale-[0.99]"
              >
                <FolderClosed className="w-4 h-4" />
                <span>LOCAL PROJECT</span>
              </button>

              <button
                id="btn-github-repo"
                onClick={onGitHubRepoClick}
                className="w-full flex items-center justify-center gap-2.5 py-3.5 px-4 bg-white hover:bg-slate-50 text-slate-900 text-sm font-semibold rounded-xl border border-slate-300 transition-all shadow-xs active:scale-[0.99]"
              >
                <Code2 className="w-4 h-4 text-slate-900" />
                <span>GITHUB REPOSITORY</span>
              </button>
            </div>
          )}
        </section>

        {/* Future Screen Navigation Foundation */}
        <section className="space-y-3">
          <div className="flex items-center justify-between px-1">
            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              DESTINATION DIRECTORY
            </h3>
            <span className="text-[11px] font-medium text-slate-400">Navigation Foundation</span>
          </div>

          <div className="space-y-2">
            {NAV_DESTINATIONS.filter((d) => d.id !== 'home').map((dest) => (
              <button
                key={dest.id}
                id={`nav-dest-btn-${dest.id}`}
                onClick={() => onNavigateToDestination(dest)}
                className="w-full flex items-center justify-between p-3.5 bg-white hover:bg-slate-50 border border-slate-200 rounded-xl transition-colors text-left group"
              >
                <div className="flex items-center gap-3">
                  <div className={`w-2 h-2 rounded-full ${dest.isImplemented ? 'bg-blue-500' : 'bg-slate-300 group-hover:bg-blue-400'} transition-colors`} />
                  <span className="text-sm font-medium text-slate-900">{dest.title}</span>
                </div>
                <div className="flex items-center gap-2">
                  {dest.isImplemented ? (
                    <span className="px-2 py-0.5 text-[10px] font-bold text-blue-700 bg-blue-50 border border-blue-200 rounded">
                      PHASE 1B
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 text-[10px] font-bold text-amber-700 bg-amber-50 border border-amber-200 rounded">
                      NOT IMPLEMENTED YET
                    </span>
                  )}
                  <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-slate-600 transition-colors" />
                </div>
              </button>
            ))}
          </div>
        </section>

        {/* Verification Footer Note */}
        <footer className="pt-2 pb-6 text-center">
          <p className="text-[11px] text-slate-400">
            AuditFlow Phase 1B • Package <code className="font-mono text-slate-600">com.auditflow.app</code>
          </p>
        </footer>
      </main>
    </div>
  );
};
