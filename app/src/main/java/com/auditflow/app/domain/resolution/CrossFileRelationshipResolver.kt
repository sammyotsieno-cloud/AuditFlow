package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.model.CallEdge
import com.auditflow.app.domain.model.CallRole
import com.auditflow.app.domain.model.CodeSymbol
import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.DependencyEdge
import com.auditflow.app.domain.model.DependencyKind
import com.auditflow.app.domain.model.DependencyRole
import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.ImplementationEdge
import com.auditflow.app.domain.model.InheritanceEdge
import com.auditflow.app.domain.model.ResolutionStatus
import com.auditflow.app.domain.model.SymbolReference

/**
 * Immutable canonical graph representing all resolved cross-file dependencies,
 * calls, inheritances, and interface implementations across the audited codebase.
 */
data class ResolvedRelationshipGraph(
    val dependencies: List<DependencyEdge> = emptyList(),
    val calls: List<CallEdge> = emptyList(),
    val inheritances: List<InheritanceEdge> = emptyList(),
    val implementations: List<ImplementationEdge> = emptyList(),
    val references: List<SymbolReference> = emptyList()
) {
    private val dependenciesBySourceFile: Map<String, List<DependencyEdge>> by lazy {
        dependencies.groupBy { it.sourceFileRelativePath }
    }

    private val dependentsByTargetFile: Map<String, List<DependencyEdge>> by lazy {
        dependencies.filter { it.targetFileRelativePath != null }.groupBy { it.targetFileRelativePath!! }
    }

    private val callersByCalleeSymbol: Map<String, List<CallEdge>> by lazy {
        calls.groupBy { it.calleeSymbol }
    }

    private val calleesByCallerSymbol: Map<String, List<CallEdge>> by lazy {
        calls.groupBy { it.callerSymbol }
    }

    private val subtypesBySuperTypeFqn: Map<String, List<InheritanceEdge>> by lazy {
        inheritances.groupBy { it.superTypeFqn }
    }

    private val implementationsByInterfaceFqn: Map<String, List<ImplementationEdge>> by lazy {
        implementations.groupBy { it.interfaceTypeFqn }
    }

    fun getDependenciesForFile(relativePath: String): List<DependencyEdge> =
        dependenciesBySourceFile[relativePath] ?: emptyList()

    fun getDependentsForFile(relativePath: String): List<DependencyEdge> =
        dependentsByTargetFile[relativePath] ?: emptyList()

    fun getCallersFor(calleeSymbolOrFqn: String): List<CallEdge> =
        calls.filter { it.calleeSymbol == calleeSymbolOrFqn || it.calleeFqn == calleeSymbolOrFqn }

    fun getCalleesFor(callerSymbolOrFqn: String): List<CallEdge> =
        calls.filter { it.callerSymbol == callerSymbolOrFqn || it.callerFqn == callerSymbolOrFqn }

    fun getSubtypesOf(superTypeFqn: String): List<InheritanceEdge> =
        subtypesBySuperTypeFqn[superTypeFqn] ?: emptyList()

    fun getSupertypesOf(subTypeFqn: String): List<InheritanceEdge> =
        inheritances.filter { it.subTypeFqn == subTypeFqn }

    fun getImplementationsOf(interfaceFqn: String): List<ImplementationEdge> =
        implementationsByInterfaceFqn[interfaceFqn] ?: emptyList()
}

/**
 * Resolves cross-file relationships, inheritance trees, interface implementations,
 * type dependencies, and function call edges across the inspected codebase.
 */
class CrossFileRelationshipResolver {

    /**
     * Resolve all relationships across the inspected files using the ProjectSymbolRegistry.
     */
    fun resolve(
        inspections: List<FileInspectionResult>,
        registry: ProjectSymbolRegistry
    ): ResolvedRelationshipGraph {
        val dependencies = mutableListOf<DependencyEdge>()
        val calls = mutableListOf<CallEdge>()
        val inheritances = mutableListOf<InheritanceEdge>()
        val implementations = mutableListOf<ImplementationEdge>()
        val references = mutableListOf<SymbolReference>()

        for (inspection in inspections) {
            val sourceFile = inspection.relativePath

            // 1. Resolve Import Dependencies
            for (importDecl in inspection.imports) {
                val resolvedImports = registry.resolveImport(importDecl, sourceFile)
                for (resolved in resolvedImports) {
                    dependencies.add(
                        DependencyEdge(
                            sourceFileRelativePath = sourceFile,
                            sourceSymbolFqn = null,
                            targetFileRelativePath = resolved.targetFileRelativePath,
                            targetSymbolFqn = resolved.resolvedFqn,
                            dependencyKind = DependencyKind.IMPORT,
                            role = DependencyRole.DEPENDENCY,
                            resolutionStatus = resolved.status,
                            isExternal = resolved.isExternal,
                            lineNumber = importDecl.lineNumber,
                            evidence = "Import statement: ${importDecl.importPath}"
                        )
                    )

                    references.add(
                        SymbolReference(
                            referencingFileRelativePath = sourceFile,
                            referencedSymbolName = importDecl.importedSymbolName,
                            referencedFqn = resolved.resolvedFqn,
                            lineNumber = importDecl.lineNumber,
                            resolutionStatus = resolved.status,
                            resolvedTargetFqn = resolved.resolvedFqn,
                            resolvedTargetFileRelativePath = resolved.targetFileRelativePath,
                            isExternal = resolved.isExternal,
                            evidence = "Import '${importDecl.importPath}'"
                        )
                    )
                }
            }

            // 2. Resolve Symbol SuperTypes & Inheritance / Implementation
            for (symbol in inspection.allSymbols) {
                if (symbol.kind in listOf(
                        CodeSymbolKind.CLASS, CodeSymbolKind.DATA_CLASS, CodeSymbolKind.SEALED_CLASS,
                        CodeSymbolKind.INTERFACE, CodeSymbolKind.OBJECT, CodeSymbolKind.ENUM
                    )
                ) {
                    for (superTypeName in symbol.superTypes) {
                        val target = registry.resolveTypeReference(sourceFile, superTypeName, inspection)
                        val targetFqn = target.resolvedFqn ?: superTypeName
                        val isInterface = target.resolvedSymbol?.kind == CodeSymbolKind.INTERFACE

                        val inheritanceEdge = InheritanceEdge(
                            subTypeFqn = symbol.fullyQualifiedName,
                            subTypeFileRelativePath = sourceFile,
                            superTypeFqn = targetFqn,
                            superTypeFileRelativePath = target.targetFileRelativePath,
                            isInterfaceImplementation = isInterface,
                            resolutionStatus = target.status,
                            isExternal = target.isExternal,
                            lineNumber = symbol.startLine,
                            evidence = "${symbol.name} inherits/implements $superTypeName"
                        )
                        inheritances.add(inheritanceEdge)

                        if (isInterface) {
                            implementations.add(
                                ImplementationEdge(
                                    implementingTypeFqn = symbol.fullyQualifiedName,
                                    implementingFileRelativePath = sourceFile,
                                    interfaceTypeFqn = targetFqn,
                                    interfaceFileRelativePath = target.targetFileRelativePath,
                                    resolutionStatus = target.status,
                                    isExternal = target.isExternal,
                                    lineNumber = symbol.startLine,
                                    evidence = "${symbol.name} implements interface $superTypeName"
                                )
                            )
                        }

                        dependencies.add(
                            DependencyEdge(
                                sourceFileRelativePath = sourceFile,
                                sourceSymbolFqn = symbol.fullyQualifiedName,
                                targetFileRelativePath = target.targetFileRelativePath,
                                targetSymbolFqn = targetFqn,
                                dependencyKind = if (isInterface) DependencyKind.IMPLEMENTATION else DependencyKind.INHERITANCE,
                                role = DependencyRole.DEPENDENCY,
                                resolutionStatus = target.status,
                                isExternal = target.isExternal,
                                lineNumber = symbol.startLine,
                                evidence = "Subtype relationship to $superTypeName"
                            )
                        )
                    }
                }

                // 3. Resolve Parameter & Constructor Type Dependencies
                for (param in symbol.parameters) {
                    val paramTypeTarget = registry.resolveTypeReference(sourceFile, param.type, inspection)
                    if (!paramTypeTarget.isExternal || paramTypeTarget.resolvedFqn != null) {
                        val kind = if (symbol.kind == CodeSymbolKind.CONSTRUCTOR || symbol.kind == CodeSymbolKind.CLASS || symbol.kind == CodeSymbolKind.DATA_CLASS) {
                            DependencyKind.CONSTRUCTOR_PARAM
                        } else {
                            DependencyKind.TYPE_USAGE
                        }

                        dependencies.add(
                            DependencyEdge(
                                sourceFileRelativePath = sourceFile,
                                sourceSymbolFqn = symbol.fullyQualifiedName,
                                targetFileRelativePath = paramTypeTarget.targetFileRelativePath,
                                targetSymbolFqn = paramTypeTarget.resolvedFqn ?: param.type,
                                dependencyKind = kind,
                                role = DependencyRole.DEPENDENCY,
                                resolutionStatus = paramTypeTarget.status,
                                isExternal = paramTypeTarget.isExternal,
                                lineNumber = symbol.startLine,
                                evidence = "Parameter '${param.name}: ${param.type}' in ${symbol.name}"
                            )
                        )
                    }
                }

                // 4. Resolve Property Return Type Dependencies
                if (symbol.kind == CodeSymbolKind.PROPERTY || symbol.kind == CodeSymbolKind.CONSTANT) {
                    val propType = symbol.returnType
                    if (!propType.isNullOrBlank()) {
                        val propTarget = registry.resolveTypeReference(sourceFile, propType, inspection)
                        dependencies.add(
                            DependencyEdge(
                                sourceFileRelativePath = sourceFile,
                                sourceSymbolFqn = symbol.fullyQualifiedName,
                                targetFileRelativePath = propTarget.targetFileRelativePath,
                                targetSymbolFqn = propTarget.resolvedFqn ?: propType,
                                dependencyKind = DependencyKind.PROPERTY_ACCESS,
                                role = DependencyRole.DEPENDENCY,
                                resolutionStatus = propTarget.status,
                                isExternal = propTarget.isExternal,
                                lineNumber = symbol.startLine,
                                evidence = "Property '${symbol.name}: $propType'"
                            )
                        )
                    }
                }

                // 5. Resolve Function / Method Call Invocations
                for (inv in symbol.invocations) {
                    val callTarget = registry.resolveCallTarget(
                        callerSymbol = symbol,
                        targetName = inv.targetName,
                        receiverName = inv.receiverName,
                        fileInspection = inspection
                    )

                    calls.add(
                        CallEdge(
                            callerSymbol = symbol.name,
                            callerFileRelativePath = sourceFile,
                            calleeSymbol = inv.targetName,
                            calleeFileRelativePath = callTarget.targetFileRelativePath ?: "external",
                            callerFqn = symbol.fullyQualifiedName,
                            calleeFqn = callTarget.resolvedCalleeFqn ?: inv.targetName,
                            lineNumber = inv.lineNumber,
                            role = CallRole.CALLER,
                            resolutionStatus = callTarget.status,
                            isExternal = callTarget.isExternal,
                            evidence = inv.rawExpression
                        )
                    )

                    references.add(
                        SymbolReference(
                            referencingFileRelativePath = sourceFile,
                            referencedSymbolName = inv.targetName,
                            referencedFqn = callTarget.resolvedCalleeFqn,
                            containingSymbolName = symbol.name,
                            lineNumber = inv.lineNumber,
                            resolutionStatus = callTarget.status,
                            resolvedTargetFqn = callTarget.resolvedCalleeFqn,
                            resolvedTargetFileRelativePath = callTarget.targetFileRelativePath,
                            isExternal = callTarget.isExternal,
                            evidence = "Call invocation: ${inv.rawExpression}"
                        )
                    )

                    if (callTarget.targetFileRelativePath != null && callTarget.targetFileRelativePath != sourceFile) {
                        dependencies.add(
                            DependencyEdge(
                                sourceFileRelativePath = sourceFile,
                                sourceSymbolFqn = symbol.fullyQualifiedName,
                                targetFileRelativePath = callTarget.targetFileRelativePath,
                                targetSymbolFqn = callTarget.resolvedCalleeFqn ?: inv.targetName,
                                dependencyKind = DependencyKind.FUNCTION_CALL,
                                role = DependencyRole.DEPENDENCY,
                                resolutionStatus = callTarget.status,
                                isExternal = callTarget.isExternal,
                                lineNumber = inv.lineNumber,
                                evidence = "Call invocation '${inv.targetName}' in ${symbol.name}"
                            )
                        )
                    }
                }
            }
        }

        return ResolvedRelationshipGraph(
            dependencies = dependencies,
            calls = calls,
            inheritances = inheritances,
            implementations = implementations,
            references = references
        )
    }
}
