import React from 'react';
import { ArrowLeft, AlertTriangle } from 'lucide-react';
import { NavDestination } from '../types';

interface NotImplementedScreenProps {
  destination: NavDestination;
  onNavigateBack: () => void;
}

export const NotImplementedScreen: React.FC<NotImplementedScreenProps> = ({
  destination,
  onNavigateBack,
}) => {
  return (
    <div className="flex flex-col min-h-full bg-slate-50 text-slate-900">
      {/* Top Bar */}
      <header className="sticky top-0 z-20 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            id="nav-back-btn"
            onClick={onNavigateBack}
            className="p-1.5 rounded-lg hover:bg-slate-200/70 text-slate-700 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <h2 className="text-base font-bold text-slate-900">{destination.title}</h2>
        </div>
        <span className="px-2 py-0.5 text-[10px] font-bold text-amber-700 bg-amber-100 rounded">
          ROUTE REGISTERED
        </span>
      </header>

      {/* Screen Body */}
      <main className="flex-1 p-6 flex flex-col items-center justify-center text-center max-w-sm mx-auto">
        <div className="w-16 h-16 rounded-2xl bg-amber-50 border border-amber-200 flex items-center justify-center text-amber-600 mb-4 shadow-xs">
          <AlertTriangle className="w-8 h-8" />
        </div>

        <span className="inline-block px-3 py-1 text-xs font-bold text-amber-700 bg-amber-100 border border-amber-300 rounded-md uppercase tracking-wider mb-3">
          NOT IMPLEMENTED YET
        </span>

        <h3 className="text-xl font-bold text-slate-900 mb-2">
          {destination.title} Screen
        </h3>

        <p className="text-sm text-slate-600 leading-relaxed mb-6">
          This destination is registered in the Navigation Graph (<code className="font-mono text-xs bg-slate-200/60 px-1 py-0.5 rounded">{destination.route}</code>), but its functional engine will be constructed in subsequent AuditFlow checkpoints.
        </p>

        <div className="w-full p-4 bg-white rounded-xl border border-slate-200 text-left text-xs text-slate-600 space-y-2 mb-6">
          <div className="flex justify-between">
            <span className="font-semibold text-slate-500">Route ID:</span>
            <span className="font-mono font-bold text-slate-900">{destination.id}</span>
          </div>
          <div className="flex justify-between">
            <span className="font-semibold text-slate-500">Category:</span>
            <span className="capitalize text-slate-700">{destination.category}</span>
          </div>
          <div className="flex justify-between">
            <span className="font-semibold text-slate-500">Status:</span>
            <span className="font-bold text-amber-600">Pending Phase Implementation</span>
          </div>
        </div>

        <button
          id="btn-return-home"
          onClick={onNavigateBack}
          className="w-full py-3 px-4 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all shadow-xs"
        >
          Return to Home
        </button>
      </main>
    </div>
  );
};
