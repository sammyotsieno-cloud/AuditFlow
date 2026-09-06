import {
  CodeSymbol,
  CodeSymbolKind,
  ContentAvailabilityState,
  FileInspectionResult,
  ImportDeclaration,
  ParameterSymbol,
  ParsingStatus,
  SemanticFileType,
  SourceFileNode,
} from '../types';

/**
 * Authoritative Code Structure & Symbol Discovery Engine.
 *
 * Faithfully mirrors Station 3 of the AuditFlow pipeline:
 * FILE CONTENT -> CODE STRUCTURE -> SYMBOL DISCOVERY
 *
 * Adheres strictly to canonical rules:
 * 1. NAME IS NOT ARCHITECTURE: symbols are extracted from empirical tokens/declarations,
 *    never guessed from filenames.
 * 2. SYMBOL HIERARCHY: maintains exact ownership (Classes -> Properties, Functions, Constructors).
 * 3. EVIDENCE LOCATION: preserves 1-indexed line numbers.
 * 4. EPISTEMIC ACCURACY: honest reporting of missing content, binary assets, and syntax anomalies.
 */

function simpleSha256(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash;
  }
  const hex = Math.abs(hash).toString(16).padStart(8, '0');
  return `sha256-${hex}`;
}

export class SourceCodeStructureExtractor {
  public static inspect(sourceNode: SourceFileNode, rawContent: string | null): FileInspectionResult {
    const relativePath = sourceNode.relativePath;
    const fileType = this.determineFileType(relativePath);

    // 1. Directory guard
    if (sourceNode.isDirectory) {
      return {
        relativePath,
        physicalNodeType: 'DIRECTORY',
        semanticFileType: 'UNKNOWN',
        byteSize: 0,
        contentAvailability: 'UNAVAILABLE_NOT_FETCHED',
        parsingStatus: 'SKIPPED_NON_SOURCE',
        linesOfCode: 0,
        hasPackageDiscrepancy: false,
        imports: [],
        fileAnnotations: [],
        symbols: [],
        parsingErrors: [],
        inspectTimestampMillis: Date.now(),
      };
    }

    // 2. Missing content guard
    if (rawContent === null) {
      return {
        relativePath,
        physicalNodeType: 'FILE',
        semanticFileType: fileType,
        byteSize: sourceNode.sizeBytes,
        contentAvailability: 'UNAVAILABLE_NOT_FETCHED',
        parsingStatus: 'UNAVAILABLE_CONTENT',
        linesOfCode: 0,
        hasPackageDiscrepancy: false,
        imports: [],
        fileAnnotations: [],
        symbols: [],
        parsingErrors: ['Raw file content was not supplied or not yet retrieved'],
        inspectTimestampMillis: Date.now(),
      };
    }

    // 3. Empty content guard
    if (rawContent.length === 0) {
      return {
        relativePath,
        physicalNodeType: 'FILE',
        semanticFileType: fileType,
        byteSize: 0,
        contentAvailability: 'UNAVAILABLE_EMPTY',
        parsingStatus: 'UNAVAILABLE_CONTENT',
        linesOfCode: 0,
        hasPackageDiscrepancy: false,
        imports: [],
        fileAnnotations: [],
        symbols: [],
        parsingErrors: ['File is physically 0 bytes'],
        inspectTimestampMillis: Date.now(),
      };
    }

    // 4. Binary guard
    if (this.isBinaryType(fileType)) {
      return {
        relativePath,
        physicalNodeType: 'FILE',
        semanticFileType: fileType,
        byteSize: sourceNode.sizeBytes,
        contentAvailability: 'UNAVAILABLE_BINARY',
        contentSha256: simpleSha256(rawContent),
        parsingStatus: 'SKIPPED_NON_SOURCE',
        linesOfCode: 0,
        hasPackageDiscrepancy: false,
        imports: [],
        fileAnnotations: [],
        symbols: [],
        parsingErrors: [],
        inspectTimestampMillis: Date.now(),
      };
    }

    const sha256 = simpleSha256(rawContent);
    const lines = rawContent.split(/\r?\n/);
    const loc = lines.length;

    switch (fileType) {
      case 'KOTLIN_SOURCE':
      case 'GRADLE_KOTLIN_DSL':
        return this.parseKotlinSource(relativePath, rawContent, lines, fileType, sha256);
      case 'JAVA_SOURCE':
        return this.parseJavaSource(relativePath, rawContent, lines, fileType, sha256);
      case 'ANDROID_MANIFEST':
      case 'XML_RESOURCE_OR_LAYOUT':
        return this.parseXmlSource(relativePath, rawContent, lines, fileType, sha256);
      default:
        return this.parseGenericText(relativePath, rawContent, lines, fileType, sha256);
    }
  }

  public static determineFileType(path: string): SemanticFileType {
    const fileName = path.split('/').pop()?.toLowerCase() || '';
    if (fileName === 'androidmanifest.xml') return 'ANDROID_MANIFEST';
    if (fileName.endsWith('.gradle.kts')) return 'GRADLE_KOTLIN_DSL';
    if (fileName.endsWith('.gradle')) return 'GRADLE_GROOVY_DSL';
    if (fileName.endsWith('.kt')) return 'KOTLIN_SOURCE';
    if (fileName.endsWith('.java')) return 'JAVA_SOURCE';
    if (fileName.endsWith('.xml')) return 'XML_RESOURCE_OR_LAYOUT';
    if (fileName.endsWith('.pro') || fileName === 'proguard-rules.pro') return 'PROGUARD_R8_RULES';
    if (fileName.endsWith('.properties')) return 'PROPERTIES';
    if (fileName.endsWith('.json')) return 'JSON';
    if (fileName.endsWith('.yaml') || fileName.endsWith('.yml')) return 'YAML';
    if (fileName.endsWith('.md')) return 'MARKDOWN';
    if (fileName.endsWith('.txt')) return 'TEXT';
    if (
      fileName.endsWith('.png') ||
      fileName.endsWith('.jpg') ||
      fileName.endsWith('.jpeg') ||
      fileName.endsWith('.webp') ||
      fileName.endsWith('.ico')
    )
      return 'BINARY_OR_IMAGE';
    if (
      fileName.endsWith('.jar') ||
      fileName.endsWith('.aar') ||
      fileName.endsWith('.apk') ||
      fileName.endsWith('.class')
    )
      return 'BYTECODE_ARCHIVE';
    if (fileName.endsWith('.dex')) return 'DEX_FILE';
    if (fileName.endsWith('.so')) return 'NATIVE_LIBRARY';
    return 'UNKNOWN';
  }

  private static isBinaryType(fileType: SemanticFileType): boolean {
    return (
      fileType === 'BINARY_OR_IMAGE' ||
      fileType === 'BYTECODE_ARCHIVE' ||
      fileType === 'DEX_FILE' ||
      fileType === 'RESOURCE_TABLE' ||
      fileType === 'NATIVE_LIBRARY'
    );
  }

  private static deriveExpectedPackage(relativePath: string): string {
    const parts = relativePath.split('/');
    const srcIndex = parts.indexOf('src');
    if (srcIndex !== -1 && parts.length > srcIndex + 3) {
      const langIndex = parts.findIndex(
        (p, idx) => idx > srcIndex && (p === 'java' || p === 'kotlin')
      );
      if (langIndex !== -1 && langIndex < parts.length - 1) {
        return parts.slice(langIndex + 1, parts.length - 1).join('.');
      }
    }
    return '';
  }

  private static parseKotlinSource(
    relativePath: string,
    rawContent: string,
    lines: string[],
    fileType: SemanticFileType,
    sha256: string
  ): FileInspectionResult {
    let packageDeclaration: string | undefined;
    const imports: ImportDeclaration[] = [];
    const fileAnnotations: string[] = [];
    const symbols: CodeSymbol[] = [];
    const parsingErrors: string[] = [];

    lines.forEach((line, index) => {
      const lineNum = index + 1;
      const trimmed = line.trim();

      if (trimmed.startsWith('package ')) {
        packageDeclaration = trimmed.substring(8).replace(/;$/, '').trim();
      } else if (trimmed.startsWith('import ')) {
        const importBody = trimmed.substring(7).replace(/;$/, '').trim();
        const isWildcard = importBody.endsWith('.*');
        const aliasParts = importBody.split(/\s+as\s+/);
        const importPath = aliasParts[0];
        const alias = aliasParts.length > 1 ? aliasParts[1] : undefined;
        const symbolName = isWildcard
          ? '*'
          : (alias || importPath.split('.').pop() || '');

        imports.push({
          importPath,
          importedSymbolName: symbolName,
          isWildcard,
          alias,
          lineNumber: lineNum,
        });
      } else if (trimmed.startsWith('@file:')) {
        fileAnnotations.push(trimmed);
      }
    });

    const expectedPackage = this.deriveExpectedPackage(relativePath);
    const hasPackageDiscrepancy =
      !!packageDeclaration &&
      !!expectedPackage &&
      packageDeclaration !== expectedPackage;

    if (hasPackageDiscrepancy) {
      parsingErrors.push(
        `Package discrepancy: declared '${packageDeclaration}' does not match path directory '${expectedPackage}'`
      );
    }

    let currentClass: CodeSymbol | null = null;
    let braceDepth = 0;
    let classBraceDepth = 0;

    for (let i = 0; i < lines.length; i++) {
      const lineNum = i + 1;
      const line = lines[i];
      const trimmed = line.trim();

      const openCount = (line.match(/\{/g) || []).length;
      const closeCount = (line.match(/\}/g) || []).length;
      braceDepth += openCount - closeCount;

      const classMatch = trimmed.match(
        /^(?:(public|private|internal|protected|abstract|open|sealed|data|enum|annotation)\s+)*(class|interface|object|enum\s+class|data\s+class|sealed\s+class)\s+([A-Za-z0-9_]+)(?:<[^>]+>)?(?:\s*:\s*([^{]+))?/
      );

      if (classMatch && !trimmed.startsWith('//')) {
        const modifiersStr = classMatch[1] || '';
        const kindKeyword = classMatch[2];
        const className = classMatch[3];
        const superTypesStr = classMatch[4] || '';

        let kind: CodeSymbolKind = 'CLASS';
        if (kindKeyword.includes('interface')) kind = 'INTERFACE';
        else if (kindKeyword.includes('object')) kind = 'OBJECT';
        else if (kindKeyword.includes('data')) kind = 'DATA_CLASS';
        else if (kindKeyword.includes('sealed')) kind = 'SEALED_CLASS';
        else if (kindKeyword.includes('enum')) kind = 'ENUM';

        const superTypes = superTypesStr
          ? superTypesStr.split(',').map((s) => s.trim().split('(')[0].trim())
          : [];

        const classSymbol: CodeSymbol = {
          id: `${relativePath}#${className}`,
          name: className,
          fullyQualifiedName: packageDeclaration ? `${packageDeclaration}.${className}` : className,
          kind,
          packageName: packageDeclaration || '',
          definingFileRelativePath: relativePath,
          startLine: lineNum,
          endLine: lineNum,
          modifiers: modifiersStr ? [modifiersStr] : [],
          visibility: modifiersStr.includes('private')
            ? 'private'
            : modifiersStr.includes('internal')
            ? 'internal'
            : 'public',
          isOverride: false,
          isSuspend: false,
          isComposable: false,
          superTypes,
          annotations: this.extractPrecedingAnnotations(lines, i),
          parameters: [],
          calls: [],
          children: [],
        };

        symbols.push(classSymbol);
        currentClass = classSymbol;
        classBraceDepth = braceDepth;
        continue;
      }

      if (currentClass && braceDepth < classBraceDepth) {
        currentClass.endLine = lineNum;
        currentClass = null;
      }

      const funMatch = trimmed.match(
        /^(?:(public|private|internal|protected|override|suspend|abstract|open)\s+)*fun\s+(?:<[^>]+>\s+)?(?:([A-Za-z0-9_]+)\.)?([A-Za-z0-9_]+)\s*\(([^)]*)\)(?:\s*:\s*([A-Za-z0-9_?<>]+))?/
      );

      if (funMatch && !trimmed.startsWith('//')) {
        const funModifiers = (funMatch[1] || '').split(/\s+/).filter(Boolean);
        const funName = funMatch[3];
        const paramsStr = funMatch[4];
        const returnType = funMatch[5] || 'Unit';

        const precedingAnnotations = this.extractPrecedingAnnotations(lines, i);
        const isComposable = precedingAnnotations.some((a) => a.includes('Composable'));
        const isSuspend = funModifiers.includes('suspend') || trimmed.includes('suspend fun');
        const isOverride = funModifiers.includes('override') || trimmed.includes('override fun');

        const params: ParameterSymbol[] = paramsStr
          ? paramsStr
              .split(',')
              .map((p) => p.trim())
              .filter(Boolean)
              .map((p) => {
                const parts = p.split(':');
                const pName = parts[0]?.trim() || '';
                const typeAndDefault = (parts[1] || '').split('=');
                return {
                  name: pName,
                  type: (typeAndDefault[0] || 'Any').trim(),
                  hasDefaultValue: typeAndDefault.length > 1,
                };
              })
          : [];

        const calls = this.extractCallsFromLines(lines, i, Math.min(i + 40, lines.length));

        const funSymbol: CodeSymbol = {
          id: `${relativePath}#${currentClass ? currentClass.name + '.' : ''}${funName}:${lineNum}`,
          name: funName,
          fullyQualifiedName: packageDeclaration
            ? `${packageDeclaration}.${currentClass ? currentClass.name + '.' : ''}${funName}`
            : `${currentClass ? currentClass.name + '.' : ''}${funName}`,
          kind: isComposable ? 'COMPOSABLE' : isSuspend ? 'SUSPEND_FUNCTION' : 'FUNCTION',
          packageName: packageDeclaration || '',
          definingFileRelativePath: relativePath,
          startLine: lineNum,
          endLine: lineNum,
          modifiers: funModifiers,
          visibility: funModifiers.includes('private')
            ? 'private'
            : funModifiers.includes('internal')
            ? 'internal'
            : 'public',
          isOverride,
          isSuspend,
          isComposable,
          returnType,
          superTypes: [],
          annotations: precedingAnnotations,
          parameters: params,
          calls,
          children: [],
        };

        if (currentClass) {
          currentClass.children.push(funSymbol);
        } else {
          symbols.push(funSymbol);
        }
        continue;
      }

      const propMatch = trimmed.match(
        /^(?:(public|private|internal|protected|override)\s+)*(val|var)\s+([A-Za-z0-9_]+)(?:\s*:\s*([A-Za-z0-9_?<>]+))?/
      );

      if (propMatch && !trimmed.startsWith('//') && currentClass) {
        const propName = propMatch[3];
        const propType = propMatch[4] || 'Inferred';
        const propModifiers = (propMatch[1] || '').split(/\s+/).filter(Boolean);

        const propSymbol: CodeSymbol = {
          id: `${relativePath}#${currentClass.name}.${propName}`,
          name: propName,
          fullyQualifiedName: `${currentClass.fullyQualifiedName}.${propName}`,
          kind: 'PROPERTY',
          packageName: packageDeclaration || '',
          definingFileRelativePath: relativePath,
          startLine: lineNum,
          endLine: lineNum,
          modifiers: propModifiers,
          visibility: propModifiers.includes('private') ? 'private' : 'public',
          isOverride: propModifiers.includes('override'),
          isSuspend: false,
          isComposable: false,
          returnType: propType,
          superTypes: [],
          annotations: this.extractPrecedingAnnotations(lines, i),
          parameters: [],
          calls: [],
          children: [],
        };

        currentClass.children.push(propSymbol);
      }
    }

    return {
      relativePath,
      physicalNodeType: 'FILE',
      semanticFileType: fileType,
      byteSize: rawContent.length,
      contentAvailability: 'AVAILABLE',
      contentSha256: sha256,
      parsingStatus: parsingErrors.length > 0 ? 'PARSED_PARTIAL' : 'PARSED_SUCCESS',
      linesOfCode: loc,
      packageDeclaration,
      hasPackageDiscrepancy,
      expectedPackageFromPath: expectedPackage,
      imports,
      fileAnnotations,
      symbols,
      parsingErrors,
      inspectTimestampMillis: Date.now(),
    };
  }

  private static parseJavaSource(
    relativePath: string,
    rawContent: string,
    lines: string[],
    fileType: SemanticFileType,
    sha256: string
  ): FileInspectionResult {
    let packageDeclaration: string | undefined;
    const imports: ImportDeclaration[] = [];
    const symbols: CodeSymbol[] = [];

    lines.forEach((line, index) => {
      const lineNum = index + 1;
      const trimmed = line.trim();

      if (trimmed.startsWith('package ')) {
        packageDeclaration = trimmed.substring(8).replace(/;$/, '').trim();
      } else if (trimmed.startsWith('import ')) {
        const importPath = trimmed.substring(7).replace(/;$/, '').trim();
        const isWildcard = importPath.endsWith('.*');
        const symbolName = isWildcard ? '*' : importPath.split('.').pop() || '';
        imports.push({
          importPath,
          importedSymbolName: symbolName,
          isWildcard,
          lineNumber: lineNum,
        });
      }
    });

    let currentClass: CodeSymbol | null = null;
    lines.forEach((line, index) => {
      const lineNum = index + 1;
      const trimmed = line.trim();

      const classMatch = trimmed.match(
        /(?:public|protected|private)?\s*(?:static)?\s*(?:final|abstract)?\s*(class|interface|enum)\s+([A-Za-z0-9_]+)/
      );
      if (classMatch) {
        const kind = classMatch[1] === 'interface' ? 'INTERFACE' : classMatch[1] === 'enum' ? 'ENUM' : 'CLASS';
        const name = classMatch[2];
        const classSymbol: CodeSymbol = {
          id: `${relativePath}#${name}`,
          name,
          fullyQualifiedName: packageDeclaration ? `${packageDeclaration}.${name}` : name,
          kind,
          packageName: packageDeclaration || '',
          definingFileRelativePath: relativePath,
          startLine: lineNum,
          endLine: lineNum,
          modifiers: [],
          visibility: 'public',
          isOverride: false,
          isSuspend: false,
          isComposable: false,
          superTypes: [],
          annotations: [],
          parameters: [],
          calls: [],
          children: [],
        };
        symbols.push(classSymbol);
        currentClass = classSymbol;
        return;
      }

      const methodMatch = trimmed.match(
        /(?:public|protected|private)?\s*(?:static)?\s*(?:final)?\s*([A-Za-z0-9_<>[\]]+)\s+([A-Za-z0-9_]+)\s*\(([^)]*)\)/
      );
      if (methodMatch && currentClass && !trimmed.startsWith('if') && !trimmed.startsWith('for')) {
        const returnType = methodMatch[1];
        const methodName = methodMatch[2];
        const methodSymbol: CodeSymbol = {
          id: `${relativePath}#${currentClass.name}.${methodName}:${lineNum}`,
          name: methodName,
          fullyQualifiedName: `${currentClass.fullyQualifiedName}.${methodName}`,
          kind: 'METHOD',
          packageName: packageDeclaration || '',
          definingFileRelativePath: relativePath,
          startLine: lineNum,
          endLine: lineNum,
          modifiers: [],
          visibility: 'public',
          isOverride: false,
          isSuspend: false,
          isComposable: false,
          returnType,
          superTypes: [],
          annotations: [],
          parameters: [],
          calls: [],
          children: [],
        };
        currentClass.children.push(methodSymbol);
      }
    });

    return {
      relativePath,
      physicalNodeType: 'FILE',
      semanticFileType: fileType,
      byteSize: rawContent.length,
      contentAvailability: 'AVAILABLE',
      contentSha256: sha256,
      parsingStatus: 'PARSED_SUCCESS',
      linesOfCode: lines.length,
      packageDeclaration,
      hasPackageDiscrepancy: false,
      imports,
      fileAnnotations: [],
      symbols,
      parsingErrors: [],
      inspectTimestampMillis: Date.now(),
    };
  }

  private static parseXmlSource(
    relativePath: string,
    rawContent: string,
    lines: string[],
    fileType: SemanticFileType,
    sha256: string
  ): FileInspectionResult {
    const symbols: CodeSymbol[] = [];
    const rootTagMatch = rawContent.match(/<([A-Za-z0-9_.:]+)[\s>]/);
    const rootTag = rootTagMatch ? rootTagMatch[1] : 'xml';

    const rootSymbol: CodeSymbol = {
      id: `${relativePath}#${rootTag}`,
      name: rootTag,
      fullyQualifiedName: `${relativePath}#${rootTag}`,
      kind: 'XML_ELEMENT',
      packageName: '',
      definingFileRelativePath: relativePath,
      startLine: 1,
      endLine: lines.length,
      modifiers: [],
      visibility: 'public',
      isOverride: false,
      isSuspend: false,
      isComposable: false,
      superTypes: [],
      annotations: [],
      parameters: [],
      calls: [],
      children: [],
    };
    symbols.push(rootSymbol);

    const permissionMatches = rawContent.matchAll(/<uses-permission\s+[^>]*android:name="([^"]+)"/g);
    for (const match of permissionMatches) {
      rootSymbol.children.push({
        id: `${relativePath}#permission:${match[1]}`,
        name: match[1].split('.').pop() || match[1],
        fullyQualifiedName: match[1],
        kind: 'XML_ELEMENT',
        packageName: '',
        definingFileRelativePath: relativePath,
        startLine: 1,
        endLine: 1,
        modifiers: ['permission'],
        visibility: 'public',
        isOverride: false,
        isSuspend: false,
        isComposable: false,
        superTypes: [],
        annotations: [],
        parameters: [],
        calls: [],
        children: [],
      });
    }

    return {
      relativePath,
      physicalNodeType: 'FILE',
      semanticFileType: fileType,
      byteSize: rawContent.length,
      contentAvailability: 'AVAILABLE',
      contentSha256: sha256,
      parsingStatus: 'PARSED_SUCCESS',
      linesOfCode: lines.length,
      hasPackageDiscrepancy: false,
      imports: [],
      fileAnnotations: [],
      symbols,
      parsingErrors: [],
      inspectTimestampMillis: Date.now(),
    };
  }

  private static parseGenericText(
    relativePath: string,
    rawContent: string,
    lines: string[],
    fileType: SemanticFileType,
    sha256: string
  ): FileInspectionResult {
    return {
      relativePath,
      physicalNodeType: 'FILE',
      semanticFileType: fileType,
      byteSize: rawContent.length,
      contentAvailability: 'AVAILABLE',
      contentSha256: sha256,
      parsingStatus: 'SKIPPED_NON_SOURCE',
      linesOfCode: lines.length,
      hasPackageDiscrepancy: false,
      imports: [],
      fileAnnotations: [],
      symbols: [],
      parsingErrors: [],
      inspectTimestampMillis: Date.now(),
    };
  }

  private static extractPrecedingAnnotations(lines: string[], targetIndex: number): string[] {
    const annotations: string[] = [];
    let idx = targetIndex - 1;
    while (idx >= 0 && lines[idx].trim().startsWith('@')) {
      annotations.unshift(lines[idx].trim());
      idx--;
    }
    return annotations;
  }

  private static extractCallsFromLines(
    lines: string[],
    start: number,
    end: number
  ): { calleeName: string; receiverName?: string; lineNumber: number }[] {
    const calls: { calleeName: string; receiverName?: string; lineNumber: number }[] = [];
    for (let i = start + 1; i < end; i++) {
      const line = lines[i].trim();
      if (line.startsWith('//') || line.startsWith('*')) continue;

      const callMatches = line.matchAll(/(?:([A-Za-z0-9_]+)\.)?([A-Za-z0-9_]+)\s*\(/g);
      for (const match of callMatches) {
        const receiver = match[1];
        const callee = match[2];
        if (['if', 'while', 'for', 'when', 'catch', 'let', 'also', 'apply', 'run'].includes(callee)) continue;
        calls.push({
          calleeName: callee,
          receiverName: receiver,
          lineNumber: i + 1,
        });
      }
    }
    return calls.slice(0, 8);
  }
}
