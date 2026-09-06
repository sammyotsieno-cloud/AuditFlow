/**
 * Deterministic domain reconstructor for project hierarchies.
 * Mirroring ProjectTreeReconstructor.kt with 100% fidelity.
 */

import { SourceFileNode } from '../types';

export interface ProjectTreeLine {
  prefix: string;
  displayName: string;
  isDirectory: boolean;
  isRoot: boolean;
  relativePath: string;
  sizeBytes: number;
  extension: string;
  depth: number;
}

export interface ProjectTreeNode {
  name: string;
  relativePath: string;
  isDirectory: boolean;
  sourceNode?: SourceFileNode;
  children: ProjectTreeNode[];
}

class MutableNode {
  name: string;
  relativePath: string;
  isDirectory: boolean;
  sourceNode?: SourceFileNode;
  children: Map<string, MutableNode> = new Map();

  constructor(name: string, relativePath: string, isDirectory: boolean, sourceNode?: SourceFileNode) {
    this.name = name;
    this.relativePath = relativePath;
    this.isDirectory = isDirectory;
    this.sourceNode = sourceNode;
  }

  toImmutable(): ProjectTreeNode {
    const sortedChildren = Array.from(this.children.values())
      .sort((a, b) => {
        // Directories first (false < true when evaluating !isDirectory)
        if (a.isDirectory !== b.isDirectory) {
          return a.isDirectory ? -1 : 1;
        }
        return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
      })
      .map((child) => child.toImmutable());

    return {
      name: this.name,
      relativePath: this.relativePath,
      isDirectory: this.isDirectory,
      sourceNode: this.sourceNode,
      children: sortedChildren,
    };
  }
}

export function normalizePath(rawPath: string): string {
  if (!rawPath) return '';
  return rawPath
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '')
    .split('/')
    .filter(Boolean)
    .join('/');
}

export function reconstructProjectTree(projectName: string, files: SourceFileNode[]): ProjectTreeNode {
  const cleanRootName = projectName.trim() || 'PROJECT';
  const root = new MutableNode(cleanRootName, '', true);

  // Deduplicate files by normalized path
  const seenPaths = new Set<string>();
  const distinctFiles: SourceFileNode[] = [];
  for (const f of files) {
    const norm = normalizePath(f.relativePath);
    if (norm && !seenPaths.has(norm)) {
      seenPaths.add(norm);
      distinctFiles.push(f);
    }
  }

  for (const fileNode of distinctFiles) {
    const normalizedPath = normalizePath(fileNode.relativePath);
    if (!normalizedPath) continue;

    const segments = normalizedPath.split('/').filter(Boolean);
    if (segments.length === 0) continue;

    let currentNode = root;
    let currentPathAccumulator = '';

    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i];
      const isLastSegment = i === segments.length - 1;
      currentPathAccumulator = currentPathAccumulator ? `${currentPathAccumulator}/${segment}` : segment;

      if (isLastSegment) {
        const existing = currentNode.children.get(segment);
        if (!existing) {
          const node = new MutableNode(
            segment,
            currentPathAccumulator,
            fileNode.isDirectory,
            fileNode
          );
          currentNode.children.set(segment, node);
          currentNode = node;
        } else {
          if (fileNode.isDirectory) {
            existing.isDirectory = true;
          }
          if (!existing.sourceNode) {
            existing.sourceNode = fileNode;
          }
          currentNode = existing;
        }
      } else {
        const existing = currentNode.children.get(segment);
        if (!existing) {
          const intermediateDir = new MutableNode(
            segment,
            currentPathAccumulator,
            true
          );
          currentNode.children.set(segment, intermediateDir);
          currentNode = intermediateDir;
        } else {
          existing.isDirectory = true;
          currentNode = existing;
        }
      }
    }
  }

  return root.toImmutable();
}

export function generateTreeLines(root: ProjectTreeNode): ProjectTreeLine[] {
  const lines: ProjectTreeLine[] = [];

  const rootDisplayName = root.isDirectory && !root.name.endsWith('/') ? `${root.name}/` : root.name;
  lines.push({
    prefix: '',
    displayName: rootDisplayName,
    isDirectory: true,
    isRoot: true,
    relativePath: root.relativePath,
    sizeBytes: 0,
    extension: '',
    depth: 0,
  });

  traverseChildren(root.children, [], lines, 1);
  return lines;
}

function traverseChildren(
  children: ProjectTreeNode[],
  ancestorContinuations: boolean[],
  lines: ProjectTreeLine[],
  depth: number
) {
  const count = children.length;
  for (let i = 0; i < count; i++) {
    const child = children[i];
    const isLast = i === count - 1;

    let prefix = '';
    for (const hasContinuation of ancestorContinuations) {
      prefix += hasContinuation ? '│   ' : '    ';
    }

    prefix += isLast ? '└── ' : '├── ';

    const displayName = child.isDirectory
      ? child.name.endsWith('/')
        ? child.name
        : `${child.name}/`
      : child.name;

    const extension = child.sourceNode?.extension || (!child.isDirectory ? child.name.split('.').pop() || '' : '');

    lines.push({
      prefix,
      displayName,
      isDirectory: child.isDirectory,
      isRoot: false,
      relativePath: child.relativePath,
      sizeBytes: child.sourceNode?.sizeBytes || 0,
      extension,
      depth,
    });

    if (child.isDirectory && child.children.length > 0) {
      const nextContinuations = [...ancestorContinuations, !isLast];
      traverseChildren(child.children, nextContinuations, lines, depth + 1);
    }
  }
}

export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const digitGroups = Math.min(Math.floor(Math.log10(bytes) / Math.log10(1024)), units.length - 1);
  if (digitGroups === 0) return `${bytes} B`;
  const value = bytes / Math.pow(1024, digitGroups);
  return `${value.toFixed(1)} ${units[digitGroups]}`;
}
