import React, { useState } from 'react';
import {
  Layers,
  FileCode,
  CheckCircle2,
  Folder,
  File,
  Terminal,
  ShieldCheck,
  Cpu,
  Database,
  Smartphone
} from 'lucide-react';

interface FileNode {
  path: string;
  name: string;
  type: 'file' | 'dir';
  layer: 'Entry' | 'Domain' | 'Data' | 'Presentation' | 'Res' | 'Test' | 'Build' | 'CI';
  desc: string;
}

const ANDROID_FILE_TREE: FileNode[] = [
  { path: 'app/src/main/java/com/auditflow/app/AuditFlowApplication.kt', name: 'AuditFlowApplication.kt', type: 'file', layer: 'Entry', desc: 'Application entry point initializing core singletons' },
  { path: 'app/src/main/java/com/auditflow/app/MainActivity.kt', name: 'MainActivity.kt', type: 'file', layer: 'Entry', desc: 'ComponentActivity entry point hosting AuditFlowNavHost' },
  { path: 'app/src/main/java/com/auditflow/app/domain/model/ProjectState.kt', name: 'ProjectState.kt', type: 'file', layer: 'Domain', desc: 'Immutable sealed state machine (NoProject, Loading, Loaded, Error)' },
  { path: 'app/src/main/java/com/auditflow/app/domain/model/ProjectMetadata.kt', name: 'ProjectMetadata.kt', type: 'file', layer: 'Domain', desc: 'Verified metadata specification for loaded source tree' },
  { path: 'app/src/main/java/com/auditflow/app/domain/model/AuditPrinciple.kt', name: 'AuditPrinciple.kt', type: 'file', layer: 'Domain', desc: 'Epistemic verification hierarchy (EXISTS -> PRODUCES_EXPECTED_RESULT)' },
  { path: 'app/src/main/java/com/auditflow/app/domain/repository/ProjectStateRepository.kt', name: 'ProjectStateRepository.kt', type: 'file', layer: 'Domain', desc: 'StateFlow-backed project state interface' },
  { path: 'app/src/main/java/com/auditflow/app/domain/repository/SettingsRepository.kt', name: 'SettingsRepository.kt', type: 'file', layer: 'Domain', desc: 'Settings repository interface' },
  { path: 'app/src/main/java/com/auditflow/app/data/local/AuditFlowPreferences.kt', name: 'AuditFlowPreferences.kt', type: 'file', layer: 'Data', desc: 'SharedPreferences persistence wrapper (persists NO_PROJECT)' },
  { path: 'app/src/main/java/com/auditflow/app/data/repository/ProjectStateRepositoryImpl.kt', name: 'ProjectStateRepositoryImpl.kt', type: 'file', layer: 'Data', desc: 'Concrete StateFlow repository implementation' },
  { path: 'app/src/main/java/com/auditflow/app/data/repository/SettingsRepositoryImpl.kt', name: 'SettingsRepositoryImpl.kt', type: 'file', layer: 'Data', desc: 'Concrete settings repository implementation' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/home/HomeScreen.kt', name: 'HomeScreen.kt', type: 'file', layer: 'Presentation', desc: 'Jetpack Compose Home screen with empty state & input buttons' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/home/HomeViewModel.kt', name: 'HomeViewModel.kt', type: 'file', layer: 'Presentation', desc: 'ViewModel preserving state across activity recreation' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/home/HomeUiState.kt', name: 'HomeUiState.kt', type: 'file', layer: 'Presentation', desc: 'Unidirectional data flow presentation state' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/navigation/AuditFlowDestination.kt', name: 'AuditFlowDestination.kt', type: 'file', layer: 'Presentation', desc: 'Sealed class defining all 9 application destinations' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/navigation/AuditFlowNavHost.kt', name: 'AuditFlowNavHost.kt', type: 'file', layer: 'Presentation', desc: 'Compose NavHost managing navigation transitions' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/common/NotImplementedComponent.kt', name: 'NotImplementedComponent.kt', type: 'file', layer: 'Presentation', desc: 'Truthful badge & dialog components' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/destination/NotImplementedScreen.kt', name: 'NotImplementedScreen.kt', type: 'file', layer: 'Presentation', desc: 'Placeholder screen for future phase destinations' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/theme/Theme.kt', name: 'Theme.kt', type: 'file', layer: 'Presentation', desc: 'AuditFlow Material 3 theme configuration' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/theme/Color.kt', name: 'Color.kt', type: 'file', layer: 'Presentation', desc: 'Color palette definitions (Slate, Navy, Blue, Amber)' },
  { path: 'app/src/main/java/com/auditflow/app/presentation/theme/Type.kt', name: 'Type.kt', type: 'file', layer: 'Presentation', desc: 'Material 3 typography hierarchy' },
  { path: 'app/src/main/AndroidManifest.xml', name: 'AndroidManifest.xml', type: 'file', layer: 'Res', desc: 'Android manifest with package com.auditflow.app' },
  { path: 'app/src/test/java/com/auditflow/app/domain/ProjectStateTest.kt', name: 'ProjectStateTest.kt', type: 'file', layer: 'Test', desc: 'Unit tests for state machine & epistemic invariants' },
  { path: 'app/src/test/java/com/auditflow/app/presentation/HomeViewModelTest.kt', name: 'HomeViewModelTest.kt', type: 'file', layer: 'Test', desc: 'Unit tests for ViewModel actions & empty state' },
  { path: 'app/src/test/java/com/auditflow/app/data/AuditFlowPreferencesTest.kt', name: 'AuditFlowPreferencesTest.kt', type: 'file', layer: 'Test', desc: 'Unit tests for preference keys and defaults' },
  { path: 'app/src/test/java/com/auditflow/app/presentation/AuditFlowNavigationTest.kt', name: 'AuditFlowNavigationTest.kt', type: 'file', layer: 'Test', desc: 'Unit tests for route resolution and implementation flags' },
  { path: 'gradle/wrapper/gradle-wrapper.jar', name: 'gradle-wrapper.jar', type: 'file', layer: 'Build', desc: 'Official Gradle Wrapper bootstrap JAR containing GradleWrapperMain' },
  { path: 'gradle/wrapper/gradle-wrapper.properties', name: 'gradle-wrapper.properties', type: 'file', layer: 'Build', desc: 'Gradle 8.10.2 distribution URL & wrapper configuration' },
  { path: 'gradlew', name: 'gradlew', type: 'file', layer: 'Build', desc: 'POSIX shell script launcher for Gradle Wrapper' },
  { path: 'gradlew.bat', name: 'gradlew.bat', type: 'file', layer: 'Build', desc: 'Windows batch script launcher for Gradle Wrapper' },
  { path: 'build.gradle.kts', name: 'build.gradle.kts (Root)', type: 'file', layer: 'Build', desc: 'Root build configuration with Kotlin 2.0.21 & AGP 8.7.3' },
  { path: 'app/build.gradle.kts', name: 'app/build.gradle.kts', type: 'file', layer: 'Build', desc: 'Module build file with Jetpack Compose BOM & dependencies' },
  { path: 'settings.gradle.kts', name: 'settings.gradle.kts', type: 'file', layer: 'Build', desc: 'Project name AuditFlow and module inclusion' },
  { path: '.github/workflows/build.yml', name: '.github/workflows/build.yml', type: 'file', layer: 'CI', desc: 'GitHub Actions workflow with workflow_dispatch & APK upload' },
];

export const ArchitectureInspector: React.FC = () => {
  const [selectedLayer, setSelectedLayer] = useState<string>('ALL');

  const filteredFiles = selectedLayer === 'ALL'
    ? ANDROID_FILE_TREE
    : ANDROID_FILE_TREE.filter(f => f.layer === selectedLayer);

  return (
    <div className="space-y-6">
      {/* Layer Architecture Diagram */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs">
        <h3 className="text-sm font-bold text-slate-900 mb-3 flex items-center gap-2">
          <Layers className="w-4 h-4 text-blue-600" />
          <span>Clean Android Architecture Hierarchy</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <Smartphone className="w-3.5 h-3.5 text-blue-600" />
              <span>PRESENTATION</span>
            </div>
            <p className="text-[11px] text-slate-600">Compose UI • Navigation • ViewModels • Theme</p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <Cpu className="w-3.5 h-3.5 text-indigo-600" />
              <span>DOMAIN</span>
            </div>
            <p className="text-[11px] text-slate-600">ProjectState • Invariant Principles • Repositories</p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <Database className="w-3.5 h-3.5 text-emerald-600" />
              <span>DATA</span>
            </div>
            <p className="text-[11px] text-slate-600">Preferences • Repository Impl • State Persistence</p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <ShieldCheck className="w-3.5 h-3.5 text-amber-600" />
              <span>TEST SUITE</span>
            </div>
            <p className="text-[11px] text-slate-600">State Transitions • Invariants • Navigation</p>
          </div>
        </div>
      </div>

      {/* File Tree Explorer */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div>
            <h3 className="text-sm font-bold text-slate-900">Android Project File Inventory</h3>
            <p className="text-xs text-slate-500">{filteredFiles.length} production &amp; test files registered</p>
          </div>

          {/* Filter Pills */}
          <div className="flex flex-wrap gap-1.5">
            {['ALL', 'Entry', 'Domain', 'Data', 'Presentation', 'Test', 'Build', 'CI'].map((layer) => (
              <button
                key={layer}
                onClick={() => setSelectedLayer(layer)}
                className={`px-2.5 py-1 text-xs font-semibold rounded-lg transition-colors ${
                  selectedLayer === layer
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                }`}
              >
                {layer}
              </button>
            ))}
          </div>
        </div>

        <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto pr-1">
          {filteredFiles.map((node) => (
            <div key={node.path} className="py-2.5 flex items-start gap-3 hover:bg-slate-50/80 px-2 rounded-lg transition-colors">
              <FileCode className="w-4 h-4 text-slate-400 mt-0.5 shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-mono font-bold text-slate-900 truncate">{node.name}</span>
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-600 shrink-0">
                    {node.layer}
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 mt-0.5">{node.desc}</p>
                <p className="text-[10px] font-mono text-slate-400 truncate mt-0.5">{node.path}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
