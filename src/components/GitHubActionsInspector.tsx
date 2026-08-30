import React, { useState } from 'react';
import { Play, CheckCircle2, Download, Terminal, GitBranch, ArrowRight, ShieldCheck } from 'lucide-react';

export const GitHubActionsInspector: React.FC = () => {
  const [activeStep, setActiveStep] = useState<number>(1);

  const workflowSteps = [
    { num: 1, name: 'Checkout Repository', desc: 'actions/checkout@v4 pulls full source tree', status: 'PASS' },
    { num: 2, name: 'Setup JDK 17 (Temurin)', desc: 'actions/setup-java@v4 with gradle caching', status: 'PASS' },
    { num: 3, name: 'Setup Android SDK', desc: 'android-actions/setup-android@v3', status: 'PASS' },
    { num: 4, name: 'Unit Tests Execution', desc: './gradlew testDebugUnitTest --continue', status: 'PASS' },
    { num: 5, name: 'Assemble Debug APK', desc: './gradlew assembleDebug --stacktrace', status: 'PASS' },
    { num: 6, name: 'Verify APK File Output', desc: 'app/build/outputs/apk/debug/app-debug.apk', status: 'PASS' },
    { num: 7, name: 'Upload Downloadable Artifact', desc: 'actions/upload-artifact@v4 (auditflow-debug-apk)', status: 'PASS' },
  ];

  return (
    <div className="space-y-6">
      {/* Workflow Header Card */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
              <h3 className="text-sm font-bold text-slate-900">.github/workflows/build.yml</h3>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Deterministic CI/CD build configuration with manual trigger support
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="px-2.5 py-1 text-xs font-mono font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-lg">
              workflow_dispatch: ENABLED
            </span>
          </div>
        </div>

        {/* Triggers Bar */}
        <div className="mt-4 pt-4 border-t border-slate-100 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
          <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/80">
            <span className="font-semibold text-slate-500 block">Manual Dispatch:</span>
            <span className="font-mono text-slate-900 font-bold">workflow_dispatch (Manual UI trigger)</span>
          </div>
          <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/80">
            <span className="font-semibold text-slate-500 block">Push Trigger:</span>
            <span className="font-mono text-slate-900 font-bold">main, master</span>
          </div>
          <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/80">
            <span className="font-semibold text-slate-500 block">Pull Request Trigger:</span>
            <span className="font-mono text-slate-900 font-bold">main, master</span>
          </div>
        </div>
      </div>

      {/* Workflow Execution Pipeline Diagram */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs">
        <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-4">
          AUTOMATED BUILD PIPELINE EXECUTION STEPS
        </h4>

        <div className="space-y-2">
          {workflowSteps.map((step) => (
            <div
              key={step.num}
              onClick={() => setActiveStep(step.num)}
              className={`p-3 rounded-xl border transition-all cursor-pointer flex items-center justify-between ${
                activeStep === step.num
                  ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                  : 'bg-slate-50 hover:bg-slate-100 text-slate-900 border-slate-200'
              }`}
            >
              <div className="flex items-center gap-3">
                <span
                  className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                    activeStep === step.num ? 'bg-blue-500 text-white' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {step.num}
                </span>
                <div>
                  <h5 className="text-xs font-bold">{step.name}</h5>
                  <p
                    className={`text-[11px] ${
                      activeStep === step.num ? 'text-slate-300' : 'text-slate-500'
                    }`}
                  >
                    {step.desc}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <span
                  className={`px-2 py-0.5 text-[10px] font-bold rounded ${
                    activeStep === step.num
                      ? 'bg-emerald-900/80 text-emerald-300 border border-emerald-700'
                      : 'bg-emerald-100 text-emerald-800'
                  }`}
                >
                  {step.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Artifact Download Specification */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs">
        <div className="flex items-start gap-4">
          <div className="w-12 h-12 rounded-xl bg-blue-50 border border-blue-200 flex items-center justify-center shrink-0">
            <Download className="w-6 h-6 text-blue-600" />
          </div>
          <div className="flex-1">
            <h4 className="text-sm font-bold text-slate-900">Downloadable APK Artifact Specification</h4>
            <p className="text-xs text-slate-600 mt-1">
              The GitHub Actions workflow uploads the generated APK using the official GitHub Actions artifact mechanism.
            </p>
            <div className="mt-3 p-3 bg-slate-900 rounded-xl text-slate-200 text-xs font-mono space-y-1">
              <div><span className="text-slate-400">Artifact Name:</span> auditflow-debug-apk</div>
              <div><span className="text-slate-400">Generated File:</span> app/build/outputs/apk/debug/app-debug.apk</div>
              <div><span className="text-slate-400">Retention Policy:</span> 14 days</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
