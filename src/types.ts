/**
 * AuditFlow - TypeScript domain types mirroring Kotlin Android models
 */

export type ProjectSourceKind = 'LOCAL_DIRECTORY' | 'GITHUB_REPOSITORY';

export interface SourceFileNode {
  relativePath: string;
  name: string;
  extension: string;
  sizeBytes: number;
  isDirectory: boolean;
  isReadable: boolean;
}

export interface ProjectMetadata {
  name: string;
  pathOrUri: string;
  sourceKind: ProjectSourceKind;
  fileCount: number;
  totalSizeBytes: number;
  branchOrTag?: string;
  timestampLoadedMillis: number;
}

export type ProjectState =
  | { kind: 'NoProject' }
  | { kind: 'ProjectLoading'; source: string; statusMessage?: string; progressPercentage: number }
  | {
      kind: 'ProjectLoaded';
      metadata: ProjectMetadata;
      files: SourceFileNode[];
      inspections?: Record<string, FileInspectionResult>;
      resolutionResult?: Station4ResolutionResult;
      decomposedTreeRoot?: DecomposedTreeNode;
      isDecomposing?: boolean;
      decompositionProgress?: number;
    }
  | { kind: 'Error'; message: string; cause?: string };

// ============================================================================
// CANONICAL CODE INSPECTION & AST SYMBOL TYPES
// ============================================================================

export type ContentAvailabilityState =
  | 'AVAILABLE'
  | 'UNAVAILABLE_NOT_FETCHED'
  | 'UNAVAILABLE_EMPTY'
  | 'UNAVAILABLE_BINARY'
  | 'UNAVAILABLE_PERMISSION_DENIED'
  | 'UNAVAILABLE_RETRIEVAL_ERROR';

export type ParsingStatus =
  | 'PARSED_SUCCESS'
  | 'PARSED_PARTIAL'
  | 'SKIPPED_NON_SOURCE'
  | 'UNPARSEABLE_SYNTAX_ERROR'
  | 'UNAVAILABLE_CONTENT';

export type SemanticFileType =
  | 'KOTLIN_SOURCE'
  | 'JAVA_SOURCE'
  | 'GRADLE_KOTLIN_DSL'
  | 'GRADLE_GROOVY_DSL'
  | 'ANDROID_MANIFEST'
  | 'XML_RESOURCE_OR_LAYOUT'
  | 'PROGUARD_R8_RULES'
  | 'PROPERTIES'
  | 'JSON'
  | 'YAML'
  | 'MARKDOWN'
  | 'TEXT'
  | 'BINARY_OR_IMAGE'
  | 'BYTECODE_ARCHIVE'
  | 'DEX_FILE'
  | 'RESOURCE_TABLE'
  | 'APK_MANIFEST'
  | 'APK_ASSET'
  | 'APK_RESOURCE'
  | 'NATIVE_LIBRARY'
  | 'ZIP_ENTRY_FILE'
  | 'ZIP_ENTRY_DIRECTORY'
  | 'UNKNOWN';

export type CodeSymbolKind =
  | 'PACKAGE'
  | 'IMPORT'
  | 'CLASS'
  | 'INTERFACE'
  | 'OBJECT'
  | 'ENUM'
  | 'DATA_CLASS'
  | 'SEALED_CLASS'
  | 'ANNOTATION_CLASS'
  | 'FUNCTION'
  | 'METHOD'
  | 'CONSTRUCTOR'
  | 'PROPERTY'
  | 'FIELD'
  | 'PARAMETER'
  | 'COMPOSABLE'
  | 'SUSPEND_FUNCTION'
  | 'TYPE_ALIAS'
  | 'VARIABLE'
  | 'XML_ELEMENT'
  | 'XML_ATTRIBUTE'
  | 'UNKNOWN';

export interface ParameterSymbol {
  name: string;
  type: string;
  hasDefaultValue?: boolean;
  annotations?: string[];
}

export interface ImportDeclaration {
  importPath: string;
  importedSymbolName: string;
  isWildcard: boolean;
  alias?: string;
  lineNumber: number;
}

export interface CallInvocation {
  calleeName: string;
  receiverName?: string;
  lineNumber: number;
  resolvedTargetFqn?: string;
  isLocalProjectSymbol?: boolean;
}

export interface CodeSymbol {
  id: string;
  name: string;
  fullyQualifiedName: string;
  kind: CodeSymbolKind;
  packageName: string;
  definingFileRelativePath: string;
  startLine: number;
  endLine: number;
  modifiers: string[];
  visibility: string;
  isOverride: boolean;
  isSuspend: boolean;
  isComposable: boolean;
  returnType?: string;
  superTypes: string[];
  annotations: string[];
  parameters: ParameterSymbol[];
  calls: CallInvocation[];
  children: CodeSymbol[];
}

export interface FileInspectionResult {
  relativePath: string;
  physicalNodeType: 'FILE' | 'DIRECTORY';
  semanticFileType: SemanticFileType;
  byteSize: number;
  contentAvailability: ContentAvailabilityState;
  contentSha256?: string;
  parsingStatus: ParsingStatus;
  linesOfCode: number;
  packageDeclaration?: string;
  hasPackageDiscrepancy: boolean;
  expectedPackageFromPath?: string;
  imports: ImportDeclaration[];
  fileAnnotations: string[];
  symbols: CodeSymbol[];
  parsingErrors: string[];
  inspectTimestampMillis: number;
}

// ============================================================================
// CROSS-FILE SYMBOL RESOLUTION & DEFECT DETECTION
// ============================================================================

export interface CanonicalDefectFinding {
  id: string;
  severity: 'WARNING' | 'ERROR' | 'INFO';
  title: string;
  description: string;
  relativePath: string;
  lineNumber?: number;
  ruleId: string;
}

export interface Station4ResolutionResult {
  indexedSymbolCount: number;
  resolvedCallsCount: number;
  localCallsCount: number;
  externalSdkCallsCount: number;
  unresolvedCallsCount: number;
  defects: CanonicalDefectFinding[];
  timestampMillis: number;
}

// ============================================================================
// DECOMPOSED TREE REPRESENTATION
// ============================================================================

export type DecomposedNodeKind =
  | 'DIRECTORY'
  | 'FILE'
  | 'PACKAGE'
  | 'IMPORTS_GROUP'
  | 'IMPORT_ITEM'
  | 'ANNOTATIONS_GROUP'
  | 'ANNOTATION_ITEM'
  | 'DECLARATIONS_GROUP'
  | 'CLASS'
  | 'INTERFACE'
  | 'OBJECT'
  | 'DATA_CLASS'
  | 'SEALED_CLASS'
  | 'ENUM'
  | 'PROPERTIES_GROUP'
  | 'PROPERTY'
  | 'CONSTRUCTORS_GROUP'
  | 'CONSTRUCTOR'
  | 'FUNCTIONS_GROUP'
  | 'FUNCTION'
  | 'PARAMETER'
  | 'RETURN_TYPE'
  | 'CALLS_GROUP'
  | 'CALL'
  | 'METADATA_GROUP'
  | 'METADATA_ITEM'
  | 'BINARY_STUB'
  | 'UNAVAILABLE_STUB';

export interface DecomposedTreeNode {
  id: string;
  displayName: string;
  kind: DecomposedNodeKind;
  relativePath: string;
  isDirectory: boolean;
  isFile: boolean;
  sizeBytes?: number;
  extension?: string;
  children: DecomposedTreeNode[];
  // Semantic attributes
  symbolKind?: CodeSymbolKind;
  startLine?: number;
  endLine?: number;
  modifiers?: string[];
  returnType?: string;
  parameters?: ParameterSymbol[];
  calls?: CallInvocation[];
  fqn?: string;
  resolutionTarget?: string;
  badge?: string;
  badgeColor?: 'blue' | 'purple' | 'green' | 'amber' | 'slate' | 'rose';
  defectWarning?: string;
  details?: string;
}

export interface CompleteTreeLine {
  id: string;
  node: DecomposedTreeNode;
  prefix: string;
  displayName: string;
  depth: number;
  hasChildren: boolean;
  isExpanded: boolean;
}

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
  { id: 'project_input', route: 'project_input', title: 'Project Input', isImplemented: true, category: 'core' },
  { id: 'source_tree', route: 'source_tree', title: 'Source Tree', isImplemented: true, category: 'analysis' },
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
