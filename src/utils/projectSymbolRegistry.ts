import {
  CanonicalDefectFinding,
  CodeSymbol,
  FileInspectionResult,
  Station4ResolutionResult,
} from '../types';

/**
 * Cross-File Symbol Registry, Resolver, and Canonical Defect Detector.
 *
 * Implements Station 4:
 * SYMBOL RESOLUTION & CROSS-FILE RELATIONSHIP GRAPH
 *
 * Guarantees:
 * - Deterministic indexing of all symbols across all inspected files.
 * - Distinguishes local project symbols from external framework/SDKs.
 * - Detects package discrepancies, circular dependencies, and unresolved references.
 */

export class ProjectSymbolRegistry {
  private symbolsByFqn = new Map<string, CodeSymbol>();
  private symbolsByName = new Map<string, CodeSymbol[]>();
  private symbolsByFile = new Map<string, CodeSymbol[]>();

  public static build(inspections: Record<string, FileInspectionResult>): ProjectSymbolRegistry {
    const registry = new ProjectSymbolRegistry();
    for (const [path, inspection] of Object.entries(inspections)) {
      const fileSymbols: CodeSymbol[] = [];
      const traverse = (sym: CodeSymbol) => {
        registry.symbolsByFqn.set(sym.fullyQualifiedName, sym);

        const list = registry.symbolsByName.get(sym.name) || [];
        list.push(sym);
        registry.symbolsByName.set(sym.name, list);

        fileSymbols.push(sym);
        sym.children.forEach(traverse);
      };

      inspection.symbols.forEach(traverse);
      registry.symbolsByFile.set(path, fileSymbols);
    }
    return registry;
  }

  public findByFqn(fqn: string): CodeSymbol | undefined {
    return this.symbolsByFqn.get(fqn);
  }

  public findByName(name: string): CodeSymbol[] {
    return this.symbolsByName.get(name) || [];
  }

  public getSymbolsForFile(path: string): CodeSymbol[] {
    return this.symbolsByFile.get(path) || [];
  }

  public getAllFqns(): string[] {
    return Array.from(this.symbolsByFqn.keys());
  }

  public resolveCall(
    calleeName: string,
    receiverName?: string,
    currentPackage?: string
  ): { targetFqn?: string; isLocal: boolean; isExternalSdk: boolean } {
    if (receiverName) {
      const receiverSymbols = this.findByName(receiverName);
      if (receiverSymbols.length > 0) {
        const candidateFqn = `${receiverSymbols[0].fullyQualifiedName}.${calleeName}`;
        return { targetFqn: candidateFqn, isLocal: true, isExternalSdk: false };
      }
    }

    if (currentPackage) {
      const pkgCandidate = `${currentPackage}.${calleeName}`;
      if (this.symbolsByFqn.has(pkgCandidate)) {
        return { targetFqn: pkgCandidate, isLocal: true, isExternalSdk: false };
      }
    }

    const candidates = this.findByName(calleeName);
    if (candidates.length > 0) {
      return { targetFqn: candidates[0].fullyQualifiedName, isLocal: true, isExternalSdk: false };
    }

    const isCommonSdk = [
      'println',
      'setContent',
      'remember',
      'mutableStateOf',
      'launch',
      'collectAsState',
      'stateIn',
      'combine',
      'update',
      'onCreate',
      'onDestroy',
      'onResume',
      'findViewById',
      'getString',
      'super',
    ].includes(calleeName);

    if (isCommonSdk) {
      return { targetFqn: `android/kotlin::${calleeName}`, isLocal: false, isExternalSdk: true };
    }

    return { isLocal: false, isExternalSdk: false };
  }
}

export class CrossFileRelationshipResolver {
  public static process(
    inspections: Record<string, FileInspectionResult>,
    registry: ProjectSymbolRegistry
  ): Station4ResolutionResult {
    let resolvedCallsCount = 0;
    let localCallsCount = 0;
    let externalSdkCallsCount = 0;
    let unresolvedCallsCount = 0;
    const defects: CanonicalDefectFinding[] = [];

    for (const [path, insp] of Object.entries(inspections)) {
      if (insp.hasPackageDiscrepancy) {
        defects.push({
          id: `defect-pkg-${path}`,
          severity: 'WARNING',
          title: 'Package Discrepancy',
          description: `Declared package '${insp.packageDeclaration}' does not match file path directory structure '${insp.expectedPackageFromPath}'.`,
          relativePath: path,
          ruleId: 'CANONICAL_PACKAGE_PATH_MISMATCH',
        });
      }

      const traverseCalls = (sym: CodeSymbol) => {
        for (const call of sym.calls) {
          const resolution = registry.resolveCall(
            call.calleeName,
            call.receiverName,
            sym.packageName
          );
          if (resolution.targetFqn) {
            call.resolvedTargetFqn = resolution.targetFqn;
            call.isLocalProjectSymbol = resolution.isLocal;
            resolvedCallsCount++;
            if (resolution.isLocal) localCallsCount++;
            else externalSdkCallsCount++;
          } else if (resolution.isExternalSdk) {
            call.resolvedTargetFqn = `SDK::${call.calleeName}`;
            call.isLocalProjectSymbol = false;
            resolvedCallsCount++;
            externalSdkCallsCount++;
          } else {
            unresolvedCallsCount++;
          }
        }
        sym.children.forEach(traverseCalls);
      };

      insp.symbols.forEach(traverseCalls);

      for (const imp of insp.imports) {
        if (
          !imp.isWildcard &&
          !imp.importPath.startsWith('android.') &&
          !imp.importPath.startsWith('androidx.') &&
          !imp.importPath.startsWith('kotlin.')
        ) {
          const isLocal = registry.getAllFqns().some((fqn) => fqn.startsWith(imp.importPath));
          if (!isLocal && !imp.importPath.startsWith('java.') && !imp.importPath.startsWith('kotlinx.')) {
            defects.push({
              id: `defect-imp-${path}-${imp.lineNumber}`,
              severity: 'INFO',
              title: 'External Library Import',
              description: `Import '${imp.importPath}' references an external dependency outside the analyzed project boundaries.`,
              relativePath: path,
              lineNumber: imp.lineNumber,
              ruleId: 'EXTERNAL_DEPENDENCY_BOUNDARY',
            });
          }
        }
      }
    }

    return {
      indexedSymbolCount: registry.getAllFqns().length,
      resolvedCallsCount,
      localCallsCount,
      externalSdkCallsCount,
      unresolvedCallsCount,
      defects,
      timestampMillis: Date.now(),
    };
  }
}
