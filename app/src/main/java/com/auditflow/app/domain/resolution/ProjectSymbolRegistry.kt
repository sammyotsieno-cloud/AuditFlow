package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.model.CodeSymbol
import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.ImportDeclaration
import com.auditflow.app.domain.model.ResolutionStatus

/**
 * Resolved target for an import declaration.
 */
data class ResolvedImport(
    val importDeclaration: ImportDeclaration,
    val resolvedFqn: String,
    val resolvedSymbol: CodeSymbol?,
    val targetFileRelativePath: String?,
    val status: ResolutionStatus,
    val isExternal: Boolean
)

/**
 * Resolved target for a type reference (parameter, property, return type, supertype).
 */
data class ResolvedTypeTarget(
    val typeName: String,
    val resolvedFqn: String?,
    val resolvedSymbol: CodeSymbol?,
    val targetFileRelativePath: String?,
    val status: ResolutionStatus,
    val isExternal: Boolean
)

/**
 * Resolved target for a function, method, or constructor invocation.
 */
data class ResolvedCallTarget(
    val targetName: String,
    val receiverName: String?,
    val resolvedCalleeFqn: String?,
    val resolvedCalleeSymbol: CodeSymbol?,
    val targetFileRelativePath: String?,
    val status: ResolutionStatus,
    val isExternal: Boolean
)

/**
 * Project-wide Symbol Registry maintaining bidirectional indexing of all code symbols,
 * packages, files, and types across the entire codebase.
 */
class ProjectSymbolRegistry private constructor(
    private val fqnIndex: Map<String, CodeSymbol>,
    private val nameIndex: Map<String, List<CodeSymbol>>,
    private val packageIndex: Map<String, List<CodeSymbol>>,
    private val fileIndex: Map<String, List<CodeSymbol>>,
    private val typeAliasMap: Map<String, String>,
    private val inspectionsByFile: Map<String, FileInspectionResult>
) {

    /**
     * Find a code symbol by its fully qualified name (e.g. "com.auditflow.domain.model.Order").
     */
    fun findSymbolByFqn(fqn: String): CodeSymbol? = fqnIndex[fqn]

    /**
     * Find all symbols sharing a simple identifier name.
     */
    fun findSymbolsByName(name: String): List<CodeSymbol> = nameIndex[name] ?: emptyList()

    /**
     * Find all symbols defined within a specific source file.
     */
    fun findSymbolsInFile(relativePath: String): List<CodeSymbol> = fileIndex[relativePath] ?: emptyList()

    /**
     * Find all symbols declared under a specific package name.
     */
    fun findSymbolsInPackage(packageName: String): List<CodeSymbol> = packageIndex[packageName] ?: emptyList()

    /**
     * Get all registered symbols in the project.
     */
    fun getAllSymbols(): List<CodeSymbol> = fqnIndex.values.toList()

    /**
     * Get all registered fully qualified names in the project.
     */
    fun getAllFqns(): Set<String> = fqnIndex.keys

    /**
     * Total number of uniquely indexed symbols in the registry.
     */
    val size: Int get() = fqnIndex.size

    /**
     * Resolve an import declaration against the registry or known external SDKs.
     */
    fun resolveImport(importDecl: ImportDeclaration, referencingFile: String): List<ResolvedImport> {
        val path = importDecl.importPath

        if (importDecl.isWildcard) {
            val pkg = path.removeSuffix(".*")
            val matchingSymbols = findSymbolsInPackage(pkg)
            if (matchingSymbols.isNotEmpty()) {
                return matchingSymbols.map { sym ->
                    ResolvedImport(
                        importDeclaration = importDecl,
                        resolvedFqn = sym.fullyQualifiedName,
                        resolvedSymbol = sym,
                        targetFileRelativePath = sym.definingFileRelativePath,
                        status = ResolutionStatus.RESOLVED,
                        isExternal = false
                    )
                }
            }
            // Check if it's a known external package
            return if (isKnownExternalPackage(pkg)) {
                listOf(
                    ResolvedImport(
                        importDeclaration = importDecl,
                        resolvedFqn = path,
                        resolvedSymbol = null,
                        targetFileRelativePath = null,
                        status = ResolutionStatus.EXTERNAL,
                        isExternal = true
                    )
                )
            } else {
                listOf(
                    ResolvedImport(
                        importDeclaration = importDecl,
                        resolvedFqn = path,
                        resolvedSymbol = null,
                        targetFileRelativePath = null,
                        status = ResolutionStatus.UNRESOLVED,
                        isExternal = false
                    )
                )
            }
        }

        // Exact import lookup
        val directLocal = fqnIndex[path]
        if (directLocal != null) {
            return listOf(
                ResolvedImport(
                    importDeclaration = importDecl,
                    resolvedFqn = path,
                    resolvedSymbol = directLocal,
                    targetFileRelativePath = directLocal.definingFileRelativePath,
                    status = ResolutionStatus.RESOLVED,
                    isExternal = false
                )
            )
        }

        // Check if matching by short name matches local symbol
        val byName = nameIndex[importDecl.importedSymbolName]
        val matchingLocal = byName?.firstOrNull { it.fullyQualifiedName == path }
        if (matchingLocal != null) {
            return listOf(
                ResolvedImport(
                    importDeclaration = importDecl,
                    resolvedFqn = path,
                    resolvedSymbol = matchingLocal,
                    targetFileRelativePath = matchingLocal.definingFileRelativePath,
                    status = ResolutionStatus.RESOLVED,
                    isExternal = false
                )
            )
        }

        // External package check
        return if (isKnownExternalPackage(path)) {
            listOf(
                ResolvedImport(
                    importDeclaration = importDecl,
                    resolvedFqn = path,
                    resolvedSymbol = null,
                    targetFileRelativePath = null,
                    status = ResolutionStatus.EXTERNAL,
                    isExternal = true
                )
            )
        } else {
            listOf(
                ResolvedImport(
                    importDeclaration = importDecl,
                    resolvedFqn = path,
                    resolvedSymbol = null,
                    targetFileRelativePath = null,
                    status = ResolutionStatus.UNRESOLVED,
                    isExternal = false
                )
            )
        }
    }

    /**
     * Resolve a referenced type name (from a property, parameter, return type, or supertype)
     * within the context of a referencing file.
     */
    fun resolveTypeReference(
        referencingFile: String,
        rawTypeName: String,
        fileInspection: FileInspectionResult? = null
    ): ResolvedTypeTarget {
        val cleanType = rawTypeName
            .removeSuffix("?")
            .substringBefore('<')
            .substringBefore('[')
            .trim()

        if (cleanType.isBlank() || isPrimitiveOrBuiltInType(cleanType)) {
            return ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = "kotlin.$cleanType",
                resolvedSymbol = null,
                targetFileRelativePath = null,
                status = ResolutionStatus.EXTERNAL,
                isExternal = true
            )
        }

        val inspection = fileInspection ?: inspectionsByFile[referencingFile]

        // 1. Direct FQN match in local registry
        if (fqnIndex.containsKey(cleanType)) {
            val symbol = fqnIndex[cleanType]
            return ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = cleanType,
                resolvedSymbol = symbol,
                targetFileRelativePath = symbol?.definingFileRelativePath,
                status = ResolutionStatus.RESOLVED,
                isExternal = false
            )
        }

        // 2. Local symbol in the same file
        val fileSymbols = fileIndex[referencingFile] ?: emptyList()
        val inSameFile = fileSymbols.firstOrNull { it.name == cleanType }
        if (inSameFile != null) {
            return ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = inSameFile.fullyQualifiedName,
                resolvedSymbol = inSameFile,
                targetFileRelativePath = inSameFile.definingFileRelativePath,
                status = ResolutionStatus.RESOLVED,
                isExternal = false
            )
        }

        // 3. Same package match
        val declaredPkg = inspection?.declaredPackage ?: ""
        if (declaredPkg.isNotBlank()) {
            val samePkgFqn = "$declaredPkg.$cleanType"
            val inSamePkg = fqnIndex[samePkgFqn]
            if (inSamePkg != null) {
                return ResolvedTypeTarget(
                    typeName = cleanType,
                    resolvedFqn = samePkgFqn,
                    resolvedSymbol = inSamePkg,
                    targetFileRelativePath = inSamePkg.definingFileRelativePath,
                    status = ResolutionStatus.RESOLVED,
                    isExternal = false
                )
            }
        }

        // 4. Check imports in the referencing file
        val imports = inspection?.imports ?: emptyList()
        for (imp in imports) {
            if (imp.isWildcard) {
                val pkg = imp.importPath.removeSuffix(".*")
                val wildcardFqn = "$pkg.$cleanType"
                val wildcardMatch = fqnIndex[wildcardFqn]
                if (wildcardMatch != null) {
                    return ResolvedTypeTarget(
                        typeName = cleanType,
                        resolvedFqn = wildcardFqn,
                        resolvedSymbol = wildcardMatch,
                        targetFileRelativePath = wildcardMatch.definingFileRelativePath,
                        status = ResolutionStatus.RESOLVED,
                        isExternal = false
                    )
                }
            } else {
                val importedName = imp.alias ?: imp.importedSymbolName
                if (importedName == cleanType) {
                    val localTarget = fqnIndex[imp.importPath]
                    return if (localTarget != null) {
                        ResolvedTypeTarget(
                            typeName = cleanType,
                            resolvedFqn = imp.importPath,
                            resolvedSymbol = localTarget,
                            targetFileRelativePath = localTarget.definingFileRelativePath,
                            status = ResolutionStatus.RESOLVED,
                            isExternal = false
                        )
                    } else if (isKnownExternalPackage(imp.importPath)) {
                        ResolvedTypeTarget(
                            typeName = cleanType,
                            resolvedFqn = imp.importPath,
                            resolvedSymbol = null,
                            targetFileRelativePath = null,
                            status = ResolutionStatus.EXTERNAL,
                            isExternal = true
                        )
                    } else {
                        ResolvedTypeTarget(
                            typeName = cleanType,
                            resolvedFqn = imp.importPath,
                            resolvedSymbol = null,
                            targetFileRelativePath = null,
                            status = ResolutionStatus.UNRESOLVED,
                            isExternal = false
                        )
                    }
                }
            }
        }

        // 5. Global unique short name match across project
        val matchingByName = nameIndex[cleanType] ?: emptyList()
        if (matchingByName.size == 1) {
            val singleMatch = matchingByName.first()
            return ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = singleMatch.fullyQualifiedName,
                resolvedSymbol = singleMatch,
                targetFileRelativePath = singleMatch.definingFileRelativePath,
                status = ResolutionStatus.RESOLVED,
                isExternal = false
            )
        } else if (matchingByName.size > 1) {
            return ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = null,
                resolvedSymbol = null,
                targetFileRelativePath = null,
                status = ResolutionStatus.AMBIGUOUS,
                isExternal = false
            )
        }

        // 6. External standard library or Android framework type
        return if (isKnownExternalType(cleanType)) {
            ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = cleanType,
                resolvedSymbol = null,
                targetFileRelativePath = null,
                status = ResolutionStatus.EXTERNAL,
                isExternal = true
            )
        } else {
            ResolvedTypeTarget(
                typeName = cleanType,
                resolvedFqn = null,
                resolvedSymbol = null,
                targetFileRelativePath = null,
                status = ResolutionStatus.UNRESOLVED,
                isExternal = false
            )
        }
    }

    /**
     * Resolve a function or method invocation from a caller symbol.
     */
    fun resolveCallTarget(
        callerSymbol: CodeSymbol,
        targetName: String,
        receiverName: String?,
        fileInspection: FileInspectionResult? = null
    ): ResolvedCallTarget {
        val inspection = fileInspection ?: inspectionsByFile[callerSymbol.definingFileRelativePath]
        val filePath = callerSymbol.definingFileRelativePath

        // 1. Invocation on a receiver object or variable
        if (!receiverName.isNullOrBlank()) {
            // Check if receiver matches a known class/object in the registry (e.g. Companion or Object call)
            val receiverTypeTarget = resolveTypeReference(filePath, receiverName, inspection)
            if (receiverTypeTarget.resolvedSymbol != null) {
                val targetMethod = receiverTypeTarget.resolvedSymbol.childSymbols.firstOrNull { it.name == targetName }
                if (targetMethod != null) {
                    return ResolvedCallTarget(
                        targetName = targetName,
                        receiverName = receiverName,
                        resolvedCalleeFqn = targetMethod.fullyQualifiedName,
                        resolvedCalleeSymbol = targetMethod,
                        targetFileRelativePath = targetMethod.definingFileRelativePath,
                        status = ResolutionStatus.RESOLVED,
                        isExternal = false
                    )
                }
            }

            // Check if receiver is a parameter or property of caller's containing class
            val containingClass = callerSymbol.containingSymbolName?.let { containerName ->
                fileIndex[filePath]?.firstOrNull { it.name == containerName }
            }
            val param = callerSymbol.parameters.firstOrNull { it.name == receiverName }
                ?: containingClass?.parameters?.firstOrNull { it.name == receiverName }

            if (param != null) {
                val paramTypeTarget = resolveTypeReference(filePath, param.type, inspection)
                if (paramTypeTarget.resolvedSymbol != null) {
                    val methodInTarget = paramTypeTarget.resolvedSymbol.childSymbols.firstOrNull { it.name == targetName }
                    if (methodInTarget != null) {
                        return ResolvedCallTarget(
                            targetName = targetName,
                            receiverName = receiverName,
                            resolvedCalleeFqn = methodInTarget.fullyQualifiedName,
                            resolvedCalleeSymbol = methodInTarget,
                            targetFileRelativePath = methodInTarget.definingFileRelativePath,
                            status = ResolutionStatus.RESOLVED,
                            isExternal = false
                        )
                    }
                }
            }

            // Check if receiver is an external SDK/framework call
            if (receiverTypeTarget.isExternal || isKnownExternalType(receiverName)) {
                return ResolvedCallTarget(
                    targetName = targetName,
                    receiverName = receiverName,
                    resolvedCalleeFqn = "$receiverName.$targetName",
                    resolvedCalleeSymbol = null,
                    targetFileRelativePath = null,
                    status = ResolutionStatus.EXTERNAL,
                    isExternal = true
                )
            }
        }

        // 2. Direct local call in the same class or containing scope
        val containingSymbolName = callerSymbol.containingSymbolName
        if (containingSymbolName != null) {
            val siblingMethod = fileIndex[filePath]
                ?.firstOrNull { it.name == containingSymbolName }
                ?.childSymbols
                ?.firstOrNull { it.name == targetName }

            if (siblingMethod != null) {
                return ResolvedCallTarget(
                    targetName = targetName,
                    receiverName = receiverName,
                    resolvedCalleeFqn = siblingMethod.fullyQualifiedName,
                    resolvedCalleeSymbol = siblingMethod,
                    targetFileRelativePath = siblingMethod.definingFileRelativePath,
                    status = ResolutionStatus.RESOLVED,
                    isExternal = false
                )
            }
        }

        // 3. Top-level function in the same file
        val sameFileFunc = fileIndex[filePath]?.firstOrNull { it.name == targetName }
        if (sameFileFunc != null) {
            return ResolvedCallTarget(
                targetName = targetName,
                receiverName = receiverName,
                resolvedCalleeFqn = sameFileFunc.fullyQualifiedName,
                resolvedCalleeSymbol = sameFileFunc,
                targetFileRelativePath = sameFileFunc.definingFileRelativePath,
                status = ResolutionStatus.RESOLVED,
                isExternal = false
            )
        }

        // 4. Same package function or constructor call (e.g. Instantiating a class/data class)
        val declaredPkg = inspection?.declaredPackage ?: ""
        if (declaredPkg.isNotBlank()) {
            val samePkgFqn = "$declaredPkg.$targetName"
            val samePkgSymbol = fqnIndex[samePkgFqn]
            if (samePkgSymbol != null) {
                return ResolvedCallTarget(
                    targetName = targetName,
                    receiverName = receiverName,
                    resolvedCalleeFqn = samePkgFqn,
                    resolvedCalleeSymbol = samePkgSymbol,
                    targetFileRelativePath = samePkgSymbol.definingFileRelativePath,
                    status = ResolutionStatus.RESOLVED,
                    isExternal = false
                )
            }
        }

        // 5. Check imports for explicit function or class constructor call
        val imports = inspection?.imports ?: emptyList()
        for (imp in imports) {
            val importedName = imp.alias ?: imp.importedSymbolName
            if (importedName == targetName) {
                val localTarget = fqnIndex[imp.importPath]
                return if (localTarget != null) {
                    ResolvedCallTarget(
                        targetName = targetName,
                        receiverName = receiverName,
                        resolvedCalleeFqn = imp.importPath,
                        resolvedCalleeSymbol = localTarget,
                        targetFileRelativePath = localTarget.definingFileRelativePath,
                        status = ResolutionStatus.RESOLVED,
                        isExternal = false
                    )
                } else if (isKnownExternalPackage(imp.importPath)) {
                    ResolvedCallTarget(
                        targetName = targetName,
                        receiverName = receiverName,
                        resolvedCalleeFqn = imp.importPath,
                        resolvedCalleeSymbol = null,
                        targetFileRelativePath = null,
                        status = ResolutionStatus.EXTERNAL,
                        isExternal = true
                    )
                } else {
                    ResolvedCallTarget(
                        targetName = targetName,
                        receiverName = receiverName,
                        resolvedCalleeFqn = imp.importPath,
                        resolvedCalleeSymbol = null,
                        targetFileRelativePath = null,
                        status = ResolutionStatus.UNRESOLVED,
                        isExternal = false
                    )
                }
            }
        }

        // 6. Global unique symbol match (e.g. constructor call to unique data class)
        val matchingByName = nameIndex[targetName] ?: emptyList()
        if (matchingByName.size == 1) {
            val single = matchingByName.first()
            return ResolvedCallTarget(
                targetName = targetName,
                receiverName = receiverName,
                resolvedCalleeFqn = single.fullyQualifiedName,
                resolvedCalleeSymbol = single,
                targetFileRelativePath = single.definingFileRelativePath,
                status = ResolutionStatus.RESOLVED,
                isExternal = false
            )
        }

        // 7. Check if target is a known standard library function / external API
        return if (isKnownExternalType(targetName) || isKnownExternalFunction(targetName)) {
            ResolvedCallTarget(
                targetName = targetName,
                receiverName = receiverName,
                resolvedCalleeFqn = targetName,
                resolvedCalleeSymbol = null,
                targetFileRelativePath = null,
                status = ResolutionStatus.EXTERNAL,
                isExternal = true
            )
        } else {
            ResolvedCallTarget(
                targetName = targetName,
                receiverName = receiverName,
                resolvedCalleeFqn = null,
                resolvedCalleeSymbol = null,
                targetFileRelativePath = null,
                status = ResolutionStatus.UNRESOLVED,
                isExternal = false
            )
        }
    }

    companion object {
        /**
         * Build an immutable ProjectSymbolRegistry from a list of FileInspectionResults.
         */
        fun build(inspections: List<FileInspectionResult>): ProjectSymbolRegistry {
            val fqnIndex = mutableMapOf<String, CodeSymbol>()
            val nameIndex = mutableMapOf<String, MutableList<CodeSymbol>>()
            val packageIndex = mutableMapOf<String, MutableList<CodeSymbol>>()
            val fileIndex = mutableMapOf<String, MutableList<CodeSymbol>>()
            val typeAliases = mutableMapOf<String, String>()
            val inspectionsByFile = mutableMapOf<String, FileInspectionResult>()

            for (insp in inspections) {
                inspectionsByFile[insp.relativePath] = insp

                for (sym in insp.allSymbols) {
                    val fqn = sym.fullyQualifiedName
                    fqnIndex[fqn] = sym

                    nameIndex.getOrPut(sym.name) { mutableListOf() }.add(sym)

                    if (sym.packageName.isNotBlank()) {
                        packageIndex.getOrPut(sym.packageName) { mutableListOf() }.add(sym)
                    }

                    fileIndex.getOrPut(sym.definingFileRelativePath) { mutableListOf() }.add(sym)

                    if (sym.kind == CodeSymbolKind.TYPE_ALIAS) {
                        typeAliases[sym.name] = sym.fullyQualifiedName
                    }
                }
            }

            return ProjectSymbolRegistry(
                fqnIndex = fqnIndex,
                nameIndex = nameIndex,
                packageIndex = packageIndex,
                fileIndex = fileIndex,
                typeAliasMap = typeAliases,
                inspectionsByFile = inspectionsByFile
            )
        }

        private fun isPrimitiveOrBuiltInType(type: String): Boolean {
            val primitives = setOf(
                "String", "Int", "Long", "Double", "Float", "Boolean", "Byte", "Short",
                "Char", "Unit", "Any", "Nothing", "List", "Set", "Map", "MutableList",
                "MutableSet", "MutableMap", "Array", "ByteArray", "IntArray", "LongArray",
                "Pair", "Triple", "Result", "Sequence", "Iterable", "CharSequence"
            )
            return type in primitives
        }

        private fun isKnownExternalPackage(path: String): Boolean {
            val knownPrefixes = listOf(
                "android.", "androidx.", "kotlin.", "kotlinx.", "java.", "javax.",
                "com.google.", "org.jetbrains.", "dagger.", "hilt.", "retrofit2.",
                "okhttp3.", "io.reactivex.", "org.junit.", "junit.", "org.mockito.",
                "io.mockk.", "org.robolectric."
            )
            return knownPrefixes.any { path.startsWith(it) }
        }

        private fun isKnownExternalType(type: String): Boolean {
            val knownTypes = setOf(
                "ViewModel", "AndroidViewModel", "Activity", "ComponentActivity",
                "AppCompatActivity", "Fragment", "Context", "Intent", "Bundle",
                "Application", "Service", "BroadcastReceiver", "ContentProvider",
                "RoomDatabase", "CoroutineScope", "Flow", "StateFlow", "SharedFlow",
                "Modifier", "NavController", "NavHostController", "Color", "TextStyle",
                "PaddingValues", "LazyListState", "Icons", "ImageVector", "Composable"
            )
            return type in knownTypes
        }

        private fun isKnownExternalFunction(name: String): Boolean {
            val knownFuncs = setOf(
                "launch", "async", "withContext", "collect", "collectLatest", "emit",
                "map", "filter", "first", "firstOrNull", "delay", "flow", "flowOf",
                "remember", "mutableStateOf", "derivedStateOf", "LaunchedEffect",
                "DisposableEffect", "SideEffect", "Column", "Row", "Box", "Text",
                "Button", "Card", "Surface", "Scaffold", "TopAppBar", "Icon", "Spacer"
            )
            return name in knownFuncs
        }
    }
}
