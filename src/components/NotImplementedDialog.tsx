import React from 'react';
import { AlertTriangle, X } from 'lucide-react';

interface NotImplementedDialogProps {
  featureTitle: string;
  isOpen: boolean;
  onDismiss: () => void;
}

export const NotImplementedDialog: React.FC<NotImplementedDialogProps> = ({
  featureTitle,
  isOpen,
  onDismiss,
}) => {
  if (!isOpen) return null;

  return (
    <div
      id="not-implemented-dialog-backdrop"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4"
      onClick={onDismiss}
    >
      <div
        id="not-implemented-dialog-card"
        className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-6 h-6 text-amber-600" />
            </div>
            <div className="flex-1">
              <div className="flex items-center justify-between">
                <span className="inline-block px-2.5 py-0.5 text-[11px] font-bold tracking-wider uppercase text-amber-700 bg-amber-100 rounded-md">
                  NOT IMPLEMENTED YET
                </span>
                <button
                  id="dialog-close-icon-btn"
                  onClick={onDismiss}
                  className="text-slate-400 hover:text-slate-600 p-1 rounded-lg transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              <h3 className="mt-2 text-lg font-bold text-slate-900">{featureTitle}</h3>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                This capability belongs to a future AuditFlow phase. In Phase 1A (Android Foundation &amp; Build Infrastructure), only the core architecture, build system, and truthful empty state are active.
              </p>
            </div>
          </div>
        </div>

        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
          <button
            id="dialog-dismiss-btn"
            onClick={onDismiss}
            className="px-5 py-2.5 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-colors shadow-xs"
          >
            Dismiss
          </button>
        </div>
      </div>
    </div>
  );
};
