import React from 'react';
import { X, Smartphone, CheckSquare, Shield } from 'lucide-react';

interface PhysicalTestPlanModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const PhysicalTestPlanModal: React.FC<PhysicalTestPlanModalProps> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  const testSteps = [
    {
      num: 1,
      title: 'INSTALL & LAUNCH',
      action: 'Launch AuditFlow on an Android device (API 24+) or the live simulated Compose viewport.',
      expected: 'AuditFlow launches cleanly without crashing, displaying the title "AUDITFLOW" and Phase 1B status.'
    },
    {
      num: 2,
      title: 'EMPTY INITIAL STATE',
      action: 'Observe the initial screen presentation on fresh install.',
      expected: 'State strictly reads "No project loaded." ZERO sample repositories, fake scan scores, or fabricated source files exist.'
    },
    {
      num: 3,
      title: 'GITHUB REPOSITORY INPUT NAVIGATION',
      action: 'Tap the [ GITHUB REPOSITORY ] button on the home screen.',
      expected: 'Navigates directly to ProjectInputScreen (route: project_input) with real input fields and quick presets.'
    },
    {
      num: 4,
      title: 'IMMUTABLE GIT TREE INGESTION',
      action: 'Enter a valid GitHub repository (e.g. octocat/Hello-World, square/retrofit, or custom repo) and tap [ INGEST REPOSITORY ].',
      expected: 'Fetches the remote recursive Git tree directly from GitHub API, computes exact node sizes, and loads physical metadata without fabrication.'
    },
    {
      num: 5,
      title: 'SOURCE TREE HIERARCHY RENDERING',
      action: 'Tap [ VIEW SOURCE TREE ] to open SourceTreeScreen.',
      expected: 'Renders the complete physical tree: directories first, sorted alphabetically, with monospace prefixes (├──, └──, │   ), trailing slashes, and exact byte sizes.'
    },
    {
      num: 6,
      title: 'PATH INTEGRITY & SEARCH FILTER',
      action: 'Type a filename or directory name into the search bar.',
      expected: 'Accurately filters nodes in real time, preserves relative path integrity, and allows selecting files to verify AST decomposition readiness.'
    },
    {
      num: 7,
      title: 'RESET / UNLOAD REPOSITORY',
      action: 'Tap [ Unload ] or reset state.',
      expected: 'Deterministic return to empty state without lingering phantom references or data corruption.'
    }
  ];

  return (
    <div
      id="physical-test-backdrop"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4"
      onClick={onClose}
    >
      <div
        id="physical-test-card"
        className="w-full max-w-2xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 border border-blue-200 flex items-center justify-center">
              <Smartphone className="w-5 h-5 text-blue-600" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">Physical Repository Tree Testing Plan (Phase 1B)</h3>
              <p className="text-xs text-slate-500">Step-by-step verification protocol for QA &amp; repository ingestion</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 overflow-y-auto space-y-4 flex-1">
          {testSteps.map((step) => (
            <div key={step.num} className="p-4 rounded-xl bg-slate-50 border border-slate-200/90 space-y-2">
              <div className="flex items-center gap-2">
                <span className="w-5 h-5 rounded-full bg-slate-900 text-white text-[10px] font-bold flex items-center justify-center">
                  {step.num}
                </span>
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900">{step.title}</h4>
              </div>
              <div className="text-xs text-slate-700 pl-7">
                <span className="font-semibold text-slate-900">Action:</span> {step.action}
              </div>
              <div className="text-xs text-emerald-800 bg-emerald-50/80 p-2.5 rounded-lg border border-emerald-200/70 pl-7">
                <span className="font-semibold text-emerald-900">Expected:</span> {step.expected}
              </div>
            </div>
          ))}
        </div>

        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
          <button
            onClick={onClose}
            className="px-5 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition-colors"
          >
            Close Plan
          </button>
        </div>
      </div>
    </div>
  );
};
