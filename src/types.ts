/**
 * AuditFlow - TypeScript domain types mirroring Kotlin Android models
 */

export type ProjectSourceKind = 'LOCAL_DIRECTORY' | 'GITHUB_REPOSITORY';

export interface ProjectMetadata {
  name: string;
  pathOrUri: string;
  sourceKind: ProjectSourceKind;
  timestampLoadedMillis: number;
}

export type ProjectState =
  | { kind: 'NoProject' }
  | { kind: 'ProjectLoading'; source: string; progressPercentage: number }
  | { kind: 'ProjectLoaded'; metadata: ProjectMetadata }
  | { kind: 'Error'; message: string; cause?: string };

export type DestinationId =
  | 'home'
  | 'project_input'
  | 'source_tree'
  | 'file_inspection'
  | 'audit'
  | 'workflow'
  | 'evidence'
  | 'results'
  | 'settings';

export interface NavDestination {
  id: DestinationId;
  route: string;
  title: string;
  isImplemented: boolean;
  category: 'core' | 'analysis' | 'verification' | 'config';
}

export const NAV_DESTINATIONS: NavDestination[] = [
  { id: 'home', route: 'home', title: 'Home', isImplemented: true, category: 'core' },
  { id: 'project_input', route: 'project_input', title: 'Project Input', isImplemented: false, category: 'core' },
  { id: 'source_tree', route: 'source_tree', title: 'Source Tree', isImplemented: false, category: 'analysis' },
  { id: 'file_inspection', route: 'file_inspection', title: 'File Inspection', isImplemented: false, category: 'analysis' },
  { id: 'audit', route: 'audit', title: 'Audit Engine', isImplemented: false, category: 'analysis' },
  { id: 'workflow', route: 'workflow', title: 'Workflow Contract', isImplemented: false, category: 'verification' },
  { id: 'evidence', route: 'evidence', title: 'Evidence & Invariants', isImplemented: false, category: 'verification' },
  { id: 'results', route: 'results', title: 'Results & Reports', isImplemented: false, category: 'verification' },
  { id: 'settings', route: 'settings', title: 'Settings', isImplemented: false, category: 'config' },
];

export interface AuditPrinciple {
  level: string;
  label: string;
  description: string;
  verifiedInPhase1A: boolean;
}

export const AUDIT_PRINCIPLES: AuditPrinciple[] = [
  { level: '1', label: 'EXISTS', description: 'Code file or artifact is present on disk or remote host', verifiedInPhase1A: true },
  { level: '2', label: 'CONNECTED', description: 'Transport link to repository, VCS, or build runner is open', verifiedInPhase1A: false },
  { level: '3', label: 'EXECUTED', description: 'Process, compiler, or analyzer has completed an execution run', verifiedInPhase1A: false },
  { level: '4', label: 'VALIDATED', description: 'Syntactic schema or structure meets formal prerequisite rules', verifiedInPhase1A: false },
  { level: '5', label: 'VERIFIED', description: 'Cryptographic or mathematical proof of semantic correctness established', verifiedInPhase1A: false },
  { level: '6', label: 'PRODUCES_EXPECTED_RESULT', description: 'Output strictly satisfies domain specification under all test invariants', verifiedInPhase1A: false },
];
