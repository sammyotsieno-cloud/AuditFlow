/**
 * AuditFlow - Phase 1A: Android Foundation + Build Infrastructure
 *
 * Core Principle:
 * EXISTS ≠ CONNECTED ≠ EXECUTED ≠ VALIDATED ≠ VERIFIED ≠ PRODUCES_EXPECTED_RESULT
 */

import React, { useState } from 'react';
import {
  Shield,
  Smartphone,
  Layers,
  GitBranch,
  CheckCircle2,
  FileText,
  RotateCcw,
  Maximize2,
  Minimize2,
  ChevronLeft,
  ExternalLink,
  Code2
} from 'lucide-react';
import { ProjectState, NAV_DESTINATIONS, NavDestination } from './types';
import { ComposeHomeScreen } from './components/ComposeHomeScreen';
import { NotImplementedScreen } from './components/NotImplementedScreen';
import { NotImplementedDialog } from './components/NotImplementedDialog';
import { ArchitectureInspector } from './components/ArchitectureInspector';
import { GitHubActionsInspector } from './components/GitHubActionsInspector';
import { TestReportModal } from './components/TestReportModal';
import { PhysicalTestPlanModal } from './components/PhysicalTestPlanModal';

type ActiveViewTab = 'device_preview' | 'architecture' | 'github_actions';

export default function App() {
  // Navigation & State Management
  const [currentDestination, setCurrentDestination] = useState<NavDestination>(NAV_DESTINATIONS[0]);
  const [projectState, setProjectState] = useState<ProjectState>({ kind: 'NoProject' });
  const [dialogState, setDialogState] = useState<{ isOpen: boolean; featureTitle: string }>({
    isOpen: false,
    featureTitle: '',
  });

  // Modal inspection dialogs
  const [isTestReportOpen, setIsTestReportOpen] = useState(false);
  const [isPhysicalPlanOpen, setIsPhysicalPlanOpen] = useState(false);

  // Active top tab
  const [activeTab, setActiveTab] = useState<ActiveViewTab>('device_preview');
  const [isDeviceFramed, setIsDeviceFramed] = useState(true);

  // Handlers
  const handleLocalProjectClick = () => {
    setDialogState({
      isOpen: true,
      featureTitle: 'Local Project Ingestion',
    });
  };

  const handleGitHubRepoClick = () => {
    setDialogState({
      isOpen: true,
      featureTitle: 'GitHub Repository Ingestion',
    });
  };

  const handleNavigateToDestination = (dest: NavDestination) => {
    setCurrentDestination(dest);
  };

  const handleNavigateBack = () => {
    setCurrentDestination(NAV_DESTINATIONS[0]);
  };

  const handleResetState = () => {
    setProjectState({ kind: 'NoProject' });
    setCurrentDestination(NAV_DESTINATIONS[0]);
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      {/* Platform Header */}
      <header className="bg-slate-950 border-b border-slate-800 px-4 sm:px-6 py-3.5 sticky top-0 z-30 shadow-md">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-600 flex items-center justify-center shadow-xs">
              <Shield className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-base font-bold tracking-wider text-white">AUDITFLOW</span>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800">
                  PHASE 1A
                </span>
              </div>
              <p className="text-xs text-slate-400">Android Foundation &amp; Build Infrastructure</p>
            </div>
          </div>

          {/* Navigation View Switcher */}
          <div className="flex items-center bg-slate-900 p-1 rounded-xl border border-slate-800 text-xs font-semibold">
            <button
              id="tab-device-preview"
              onClick={() => setActiveTab('device_preview')}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg transition-colors ${
                activeTab === 'device_preview'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>Android Compose UI</span>
            </button>

            <button
              id="tab-architecture"
              onClick={() => setActiveTab('architecture')}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg transition-colors ${
                activeTab === 'architecture'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              <span>Clean Architecture</span>
            </button>

            <button
              id="tab-github-actions"
              onClick={() => setActiveTab('github_actions')}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg transition-colors ${
                activeTab === 'github_actions'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <GitBranch className="w-3.5 h-3.5" />
              <span>CI/CD &amp; APK Build</span>
            </button>
          </div>

          {/* Verification Tools Action Bar */}
          <div className="flex items-center gap-2">
            <button
              id="btn-open-test-report"
              onClick={() => setIsTestReportOpen(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-emerald-950/80 hover:bg-emerald-900 text-emerald-300 border border-emerald-800/80 text-xs font-semibold rounded-lg transition-colors shadow-xs"
            >
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              <span>10 / 10 Tests Pass</span>
            </button>

            <button
              id="btn-open-physical-plan"
              onClick={() => setIsPhysicalPlanOpen(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs font-semibold rounded-lg transition-colors shadow-xs"
            >
              <FileText className="w-3.5 h-3.5 text-slate-300" />
              <span>Physical Test Plan</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Workspace Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 flex flex-col">
        {/* Core Epistemic Verification Banner */}
        <div className="mb-6 p-4 rounded-xl bg-slate-950 border border-slate-800 text-xs flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-xs">
          <div className="flex items-center gap-3">
            <span className="px-2 py-1 bg-amber-950 text-amber-400 border border-amber-800 rounded font-bold uppercase text-[10px] tracking-wider shrink-0">
              Core Principle
            </span>
            <span className="font-mono text-slate-300">
              EXISTS ≠ CONNECTED ≠ EXECUTED ≠ VALIDATED ≠ VERIFIED ≠ PRODUCES_EXPECTED_RESULT
            </span>
          </div>
          <div className="text-slate-400 text-[11px] shrink-0">
            Phase 1A: Strict Truthful Initial State (No Sample Data)
          </div>
        </div>

        {/* Tab 1: Android Jetpack Compose Screen Simulator */}
        {activeTab === 'device_preview' && (
          <div className="flex-1 flex flex-col items-center justify-center py-2">
            {/* Simulation Controls Bar */}
            <div className="w-full max-w-md flex items-center justify-between mb-3 px-2 text-xs text-slate-400">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                <span>Pixel 8 • Android 15 (API 35)</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleResetState}
                  title="Reset to clean NoProject state"
                  className="flex items-center gap-1 hover:text-slate-200 transition-colors p-1"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Reset State</span>
                </button>
                <button
                  onClick={() => setIsDeviceFramed(!isDeviceFramed)}
                  className="hover:text-slate-200 transition-colors p-1"
                  title="Toggle device frame"
                >
                  {isDeviceFramed ? <Maximize2 className="w-3.5 h-3.5" /> : <Minimize2 className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>

            {/* Android Device Mockup Shell */}
            <div
              className={`w-full transition-all duration-300 ${
                isDeviceFramed
                  ? 'max-w-[400px] h-[780px] rounded-[42px] border-[10px] border-slate-800 shadow-2xl p-2 bg-slate-950 ring-1 ring-slate-700/50'
                  : 'max-w-xl h-[700px] rounded-2xl border border-slate-800 shadow-xl'
              } flex flex-col overflow-hidden bg-slate-50`}
            >
              {/* Android Notch & Status Bar */}
              {isDeviceFramed && (
                <div className="bg-slate-50 px-6 pt-2 pb-1 flex items-center justify-between text-[11px] font-semibold text-slate-700 select-none">
                  <span>9:41</span>
                  <div className="w-20 h-4 bg-slate-900 rounded-full mx-auto" />
                  <div className="flex items-center gap-1.5">
                    <span>5G</span>
                    <div className="w-4 h-2.5 border border-slate-700 rounded-xs flex items-center p-0.5">
                      <div className="w-full h-full bg-slate-700 rounded-2xs" />
                    </div>
                  </div>
                </div>
              )}

              {/* Compose Screen Viewport */}
              <div className="flex-1 overflow-y-auto relative bg-slate-50">
                {currentDestination.id === 'home' ? (
                  <ComposeHomeScreen
                    projectState={projectState}
                    onLocalProjectClick={handleLocalProjectClick}
                    onGitHubRepoClick={handleGitHubRepoClick}
                    onNavigateToDestination={handleNavigateToDestination}
                  />
                ) : (
                  <NotImplementedScreen
                    destination={currentDestination}
                    onNavigateBack={handleNavigateBack}
                  />
                )}
              </div>

              {/* Android Gesture Bar */}
              {isDeviceFramed && (
                <div className="bg-slate-50 py-2 flex justify-center">
                  <div className="w-28 h-1 bg-slate-300 rounded-full" />
                </div>
              )}
            </div>
          </div>
        )}

        {/* Tab 2: Architecture Inspector */}
        {activeTab === 'architecture' && <ArchitectureInspector />}

        {/* Tab 3: CI/CD & Build Pipeline Inspector */}
        {activeTab === 'github_actions' && <GitHubActionsInspector />}
      </main>

      {/* Modal Dialogs */}
      <NotImplementedDialog
        featureTitle={dialogState.featureTitle}
        isOpen={dialogState.isOpen}
        onDismiss={() => setDialogState({ isOpen: false, featureTitle: '' })}
      />

      <TestReportModal
        isOpen={isTestReportOpen}
        onClose={() => setIsTestReportOpen(false)}
      />

      <PhysicalTestPlanModal
        isOpen={isPhysicalPlanOpen}
        onClose={() => setIsPhysicalPlanOpen(false)}
      />
    </div>
  );
}
