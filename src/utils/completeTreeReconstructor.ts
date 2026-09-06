import {
  CodeSymbol,
  CompleteTreeLine,
  DecomposedTreeNode,
  FileInspectionResult,
  SourceFileNode,
  Station4ResolutionResult,
} from '../types';

/**
 * Reconstructs the canonical Complete Decomposed Tree from physical files,
 * code structure inspections, and cross-file resolution results.
 *
 * Implements:
 * - Physical directory hierarchy preservation (Level A).
 * - File-level semantic decomposition (Level B).
 * - Cross-file resolved relationships (Level C).
 */

export class CompleteTreeReconstructor {
  public static reconstruct(
    projectName: string,
    files: SourceFileNode[],
    inspections: Record<string, FileInspectionResult> = {},
    resolution?: Station4ResolutionResult
  ): DecomposedTreeNode {
    const cleanRootName = projectName.trim() || 'REPOSITORY';

    // Step 1: Group files into directory tree
    interface MutableDirectory {
      name: string;
      relativePath: string;
      subdirs: Map<string, MutableDirectory>;
      fileNodes: SourceFileNode[];
    }

    const rootDir: MutableDirectory = {
      name: cleanRootName,
      relativePath: '',
      subdirs: new Map(),
      fileNodes: [],
    };

    // Sort files to ensure deterministic tree structure
    const sortedFiles = [...files].sort((a, b) => a.relativePath.localeCompare(b.relativePath));

    for (const file of sortedFiles) {
      const parts = file.relativePath.split('/').filter(Boolean);
      if (parts.length === 0) continue;

      let currentDir = rootDir;
      let pathAcc = '';

      for (let i = 0; i < parts.length - 1; i++) {
        const seg = parts[i];
        pathAcc = pathAcc ? `${pathAcc}/${seg}` : seg;
        if (!currentDir.subdirs.has(seg)) {
          currentDir.subdirs.set(seg, {
            name: seg,
            relativePath: pathAcc,
            subdirs: new Map(),
            fileNodes: [],
          });
        }
        currentDir = currentDir.subdirs.get(seg)!;
      }

      if (!file.isDirectory) {
        currentDir.fileNodes.push(file);
      }
    }

    // Step 2: Convert to DecomposedTreeNode recursively
    const buildDirNode = (dir: MutableDirectory): DecomposedTreeNode => {
      const childNodes: DecomposedTreeNode[] = [];

      // Sibling directories first (alphabetical)
      const sortedSubdirs = Array.from(dir.subdirs.values()).sort((a, b) =>
        a.name.localeCompare(b.name)
      );
      for (const sub of sortedSubdirs) {
        childNodes.push(buildDirNode(sub));
      }

      // Sibling files next (alphabetical)
      const sortedFiles = [...dir.fileNodes].sort((a, b) => a.name.localeCompare(b.name));
      for (const file of sortedFiles) {
        childNodes.push(this.buildFileDecomposedNode(file, inspections[file.relativePath], resolution));
      }

      return {
        id: dir.relativePath ? `dir:${dir.relativePath}` : 'root',
        displayName: dir.relativePath ? `${dir.name}/` : dir.name,
        kind: 'DIRECTORY',
        relativePath: dir.relativePath,
        isDirectory: true,
        isFile: false,
        children: childNodes,
      };
    };

    return buildDirNode(rootDir);
  }

  private static buildFileDecomposedNode(
    file: SourceFileNode,
    inspection?: FileInspectionResult,
    resolution?: Station4ResolutionResult
  ): DecomposedTreeNode {
    const fileId = `file:${file.relativePath}`;
    const fileChildren: DecomposedTreeNode[] = [];

    if (!inspection || inspection.contentAvailability === 'UNAVAILABLE_NOT_FETCHED') {
      fileChildren.push({
        id: `${fileId}#unfetched`,
        displayName: '[CONTENT_UNFETCHED: Pending full AST inspection]',
        kind: 'UNAVAILABLE_STUB',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
        badge: 'PENDING',
        badgeColor: 'amber',
      });
      return {
        id: fileId,
        displayName: file.name,
        kind: 'FILE',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: true,
        sizeBytes: file.sizeBytes,
        extension: file.extension,
        children: fileChildren,
      };
    }

    if (inspection.contentAvailability === 'UNAVAILABLE_BINARY') {
      fileChildren.push({
        id: `${fileId}#binary`,
        displayName: `[UNAVAILABLE_BINARY: Non-source asset (${inspection.byteSize.toLocaleString()} B)]`,
        kind: 'BINARY_STUB',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
        badge: 'BINARY',
        badgeColor: 'slate',
      });
      return {
        id: fileId,
        displayName: file.name,
        kind: 'FILE',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: true,
        sizeBytes: file.sizeBytes,
        extension: file.extension,
        children: fileChildren,
      };
    }

    // 1. Package declaration
    if (inspection.packageDeclaration) {
      fileChildren.push({
        id: `${fileId}#pkg`,
        displayName: `package: ${inspection.packageDeclaration}`,
        kind: 'PACKAGE',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
        badge: 'PKG',
        badgeColor: 'blue',
        defectWarning: inspection.hasPackageDiscrepancy
          ? `Package mismatch: declared '${inspection.packageDeclaration}' vs path '${inspection.expectedPackageFromPath}'`
          : undefined,
      });
    }

    // 2. Imports group
    if (inspection.imports.length > 0) {
      const importChildren: DecomposedTreeNode[] = inspection.imports.map((imp, idx) => ({
        id: `${fileId}#imp:${idx}`,
        displayName: `import ${imp.importPath}${imp.alias ? ` as ${imp.alias}` : ''}`,
        kind: 'IMPORT_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        startLine: imp.lineNumber,
        children: [],
        badge: imp.isWildcard ? 'WILDCARD' : 'IMPORT',
        badgeColor: 'slate',
      }));

      fileChildren.push({
        id: `${fileId}#imports`,
        displayName: `imports (${inspection.imports.length})`,
        kind: 'IMPORTS_GROUP',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: importChildren,
        badge: `${inspection.imports.length}`,
        badgeColor: 'slate',
      });
    }

    // 3. File annotations
    if (inspection.fileAnnotations.length > 0) {
      const annoChildren: DecomposedTreeNode[] = inspection.fileAnnotations.map((anno, idx) => ({
        id: `${fileId}#anno:${idx}`,
        displayName: anno,
        kind: 'ANNOTATION_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
        badge: 'ANNOTATION',
        badgeColor: 'purple',
      }));

      fileChildren.push({
        id: `${fileId}#annotations`,
        displayName: `file annotations (${inspection.fileAnnotations.length})`,
        kind: 'ANNOTATIONS_GROUP',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: annoChildren,
      });
    }

    // 4. Declarations (Classes, Interfaces, Objects, Functions)
    if (inspection.symbols.length > 0) {
      const declChildren: DecomposedTreeNode[] = inspection.symbols.map((sym) =>
        this.buildSymbolNode(file.relativePath, sym)
      );

      fileChildren.push({
        id: `${fileId}#declarations`,
        displayName: `declarations (${inspection.symbols.length})`,
        kind: 'DECLARATIONS_GROUP',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: declChildren,
        badge: `${inspection.symbols.length}`,
        badgeColor: 'purple',
      });
    }

    // 5. Source metadata node
    const metadataItems: DecomposedTreeNode[] = [
      {
        id: `${fileId}#meta:loc`,
        displayName: `lines of code: ${inspection.linesOfCode}`,
        kind: 'METADATA_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
      },
      {
        id: `${fileId}#meta:size`,
        displayName: `content size: ${inspection.byteSize.toLocaleString()} bytes`,
        kind: 'METADATA_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
      },
      {
        id: `${fileId}#meta:status`,
        displayName: `parsing status: ${inspection.parsingStatus}`,
        kind: 'METADATA_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
        badge: inspection.parsingStatus,
        badgeColor: inspection.parsingStatus === 'PARSED_SUCCESS' ? 'green' : 'amber',
      },
    ];

    if (inspection.contentSha256) {
      metadataItems.push({
        id: `${fileId}#meta:sha`,
        displayName: `sha256: ${inspection.contentSha256}`,
        kind: 'METADATA_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
      });
    }

    // Add defect warnings if any apply to this file
    const fileDefects = resolution?.defects.filter((d) => d.relativePath === file.relativePath) || [];
    for (const defect of fileDefects) {
      metadataItems.push({
        id: `${fileId}#defect:${defect.id}`,
        displayName: `[${defect.severity}] ${defect.title}: ${defect.description}`,
        kind: 'METADATA_ITEM',
        relativePath: file.relativePath,
        isDirectory: false,
        isFile: false,
        children: [],
        badge: defect.severity,
        badgeColor: defect.severity === 'ERROR' ? 'rose' : 'amber',
        defectWarning: defect.description,
      });
    }

    fileChildren.push({
      id: `${fileId}#metadata`,
      displayName: 'source metadata',
      kind: 'METADATA_GROUP',
      relativePath: file.relativePath,
      isDirectory: false,
      isFile: false,
      children: metadataItems,
      badge: 'INFO',
      badgeColor: 'slate',
    });

    return {
      id: fileId,
      displayName: `${file.name} [${file.sizeBytes.toLocaleString()} B]`,
      kind: 'FILE',
      relativePath: file.relativePath,
      isDirectory: false,
      isFile: true,
      sizeBytes: file.sizeBytes,
      extension: file.extension,
      children: fileChildren,
      details: `${inspection.linesOfCode} LOC | ${inspection.symbols.length} symbols | ${inspection.parsingStatus}`,
    };
  }

  private static buildSymbolNode(fileRelativePath: string, sym: CodeSymbol): DecomposedTreeNode {
    const symId = sym.id || `${fileRelativePath}#${sym.name}:${sym.startLine}`;
    const childNodes: DecomposedTreeNode[] = [];

    // Properties inside class
    const properties = sym.children.filter((c) => c.kind === 'PROPERTY');
    if (properties.length > 0) {
      const propItems: DecomposedTreeNode[] = properties.map((p) => ({
        id: p.id,
        displayName: `${p.modifiers.join(' ')}${p.modifiers.length > 0 ? ' ' : ''}val ${p.name}: ${p.returnType || 'Any'}`,
        kind: 'PROPERTY',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        startLine: p.startLine,
        returnType: p.returnType,
        children: [],
        badge: 'val',
        badgeColor: 'green',
        fqn: p.fullyQualifiedName,
      }));

      childNodes.push({
        id: `${symId}#properties`,
        displayName: `properties (${properties.length})`,
        kind: 'PROPERTIES_GROUP',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        children: propItems,
      });
    }

    // Functions / Methods inside class
    const functions = sym.children.filter(
      (c) => c.kind === 'FUNCTION' || c.kind === 'METHOD' || c.kind === 'COMPOSABLE' || c.kind === 'SUSPEND_FUNCTION'
    );
    if (functions.length > 0) {
      const funItems: DecomposedTreeNode[] = functions.map((fn) =>
        this.buildFunctionNode(fileRelativePath, fn)
      );

      childNodes.push({
        id: `${symId}#functions`,
        displayName: `functions (${functions.length})`,
        kind: 'FUNCTIONS_GROUP',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        children: funItems,
        badge: `${functions.length}`,
        badgeColor: 'blue',
      });
    }

    // Nested classes / types
    const nestedTypes = sym.children.filter(
      (c) => !['PROPERTY', 'FUNCTION', 'METHOD', 'COMPOSABLE', 'SUSPEND_FUNCTION'].includes(c.kind)
    );
    for (const nested of nestedTypes) {
      childNodes.push(this.buildSymbolNode(fileRelativePath, nested));
    }

    // Top-level function without enclosing class
    if (['FUNCTION', 'METHOD', 'COMPOSABLE', 'SUSPEND_FUNCTION'].includes(sym.kind)) {
      return this.buildFunctionNode(fileRelativePath, sym);
    }

    const superTypesSuffix = sym.superTypes.length > 0 ? ` : ${sym.superTypes.join(', ')}` : '';
    const kindLabel = sym.kind.toLowerCase().replace('_', ' ');

    return {
      id: symId,
      displayName: `${kindLabel} ${sym.name}${superTypesSuffix}`,
      kind: sym.kind === 'INTERFACE' ? 'INTERFACE' : sym.kind === 'OBJECT' ? 'OBJECT' : 'CLASS',
      relativePath: fileRelativePath,
      isDirectory: false,
      isFile: false,
      symbolKind: sym.kind,
      startLine: sym.startLine,
      endLine: sym.endLine,
      fqn: sym.fullyQualifiedName,
      modifiers: sym.modifiers,
      children: childNodes,
      badge: sym.kind.toLowerCase(),
      badgeColor: sym.kind === 'INTERFACE' ? 'amber' : sym.kind === 'OBJECT' ? 'purple' : 'blue',
    };
  }

  private static buildFunctionNode(fileRelativePath: string, fn: CodeSymbol): DecomposedTreeNode {
    const fnId = fn.id;
    const childNodes: DecomposedTreeNode[] = [];

    // Parameters
    if (fn.parameters.length > 0) {
      const paramStr = fn.parameters
        .map((p) => `${p.name}: ${p.type}${p.hasDefaultValue ? ' = ...' : ''}`)
        .join(', ');
      childNodes.push({
        id: `${fnId}#params`,
        displayName: `parameters: (${paramStr})`,
        kind: 'PARAMETER',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        parameters: fn.parameters,
        children: [],
        badge: `${fn.parameters.length} params`,
        badgeColor: 'slate',
      });
    }

    // Return Type
    childNodes.push({
      id: `${fnId}#return`,
      displayName: `return type: ${fn.returnType || 'Unit'}`,
      kind: 'RETURN_TYPE',
      relativePath: fileRelativePath,
      isDirectory: false,
      isFile: false,
      returnType: fn.returnType,
      children: [],
      badge: fn.returnType || 'Unit',
      badgeColor: 'green',
    });

    // Modifiers & Annotations
    const modifiersList = [...fn.modifiers];
    if (fn.isComposable) modifiersList.push('@Composable');
    if (fn.isSuspend) modifiersList.push('suspend');
    if (fn.isOverride) modifiersList.push('override');

    if (modifiersList.length > 0) {
      childNodes.push({
        id: `${fnId}#modifiers`,
        displayName: `modifiers: [${modifiersList.join(', ')}]`,
        kind: 'METADATA_ITEM',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        modifiers: modifiersList,
        children: [],
        badge: modifiersList.join(' '),
        badgeColor: 'purple',
      });
    }

    // Calls (invocations)
    if (fn.calls.length > 0) {
      const callNodes: DecomposedTreeNode[] = fn.calls.map((call, idx) => ({
        id: `${fnId}#call:${idx}`,
        displayName: `${call.receiverName ? `${call.receiverName}.` : ''}${call.calleeName}()${
          call.resolvedTargetFqn ? ` -> ${call.resolvedTargetFqn}` : ''
        }`,
        kind: 'CALL',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        startLine: call.lineNumber,
        calls: [call],
        children: [],
        badge: call.isLocalProjectSymbol ? 'LOCAL' : 'SDK',
        badgeColor: call.isLocalProjectSymbol ? 'blue' : 'slate',
      }));

      childNodes.push({
        id: `${fnId}#calls`,
        displayName: `calls (${fn.calls.length})`,
        kind: 'CALLS_GROUP',
        relativePath: fileRelativePath,
        isDirectory: false,
        isFile: false,
        children: callNodes,
        badge: `${fn.calls.length}`,
        badgeColor: 'amber',
      });
    }

    const paramsHeader = fn.parameters.map((p) => `${p.name}: ${p.type}`).join(', ');
    const displayPrefix = fn.isComposable ? '@Composable fun ' : fn.isSuspend ? 'suspend fun ' : 'fun ';

    return {
      id: fnId,
      displayName: `${displayPrefix}${fn.name}(${paramsHeader}): ${fn.returnType || 'Unit'}`,
      kind: 'FUNCTION',
      relativePath: fileRelativePath,
      isDirectory: false,
      isFile: false,
      symbolKind: fn.kind,
      startLine: fn.startLine,
      endLine: fn.endLine,
      modifiers: modifiersList,
      returnType: fn.returnType,
      parameters: fn.parameters,
      calls: fn.calls,
      fqn: fn.fullyQualifiedName,
      children: childNodes,
      badge: fn.isComposable ? '@Composable' : fn.isSuspend ? 'suspend' : 'fun',
      badgeColor: fn.isComposable ? 'purple' : 'blue',
    };
  }

  /**
   * Flattens the decomposed tree hierarchy into ASCII-prefixed display lines,
   * respecting expand/collapse states.
   */
  public static generateLines(
    root: DecomposedTreeNode,
    expandedNodeIds: Set<string>
  ): CompleteTreeLine[] {
    const lines: CompleteTreeLine[] = [];

    // Root line
    lines.push({
      id: root.id,
      node: root,
      prefix: '',
      displayName: root.displayName,
      depth: 0,
      hasChildren: root.children.length > 0,
      isExpanded: true,
    });

    const traverse = (children: DecomposedTreeNode[], ancestorIsLast: boolean[], depth: number) => {
      children.forEach((child, index) => {
        const isLast = index === children.length - 1;

        // Build prefix
        let prefix = '';
        for (let i = 0; i < ancestorIsLast.length; i++) {
          prefix += ancestorIsLast[i] ? '    ' : '│   ';
        }
        prefix += isLast ? '└── ' : '├── ';

        const hasChildren = child.children.length > 0;
        const isExpanded = expandedNodeIds.has(child.id);

        lines.push({
          id: child.id,
          node: child,
          prefix,
          displayName: child.displayName,
          depth,
          hasChildren,
          isExpanded,
        });

        if (hasChildren && isExpanded) {
          traverse(child.children, [...ancestorIsLast, isLast], depth + 1);
        }
      });
    };

    traverse(root.children, [], 1);
    return lines;
  }
}
