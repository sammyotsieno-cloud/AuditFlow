package com.auditflow.app.domain.inspection

import com.auditflow.app.domain.model.CallInvocation
import com.auditflow.app.domain.model.CodeSymbol
import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.ContentAvailabilityState
import com.auditflow.app.domain.model.FileInspectionResult
import com.auditflow.app.domain.model.ImportDeclaration
import com.auditflow.app.domain.model.ParameterSymbol
import com.auditflow.app.domain.model.ParsingStatus
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.PhysicalNodeType
import com.auditflow.app.domain.model.RelativePathHelper
import com.auditflow.app.domain.model.SemanticFileType
import com.auditflow.app.domain.model.SourceFileNode
import java.security.MessageDigest

/**
 * Authoritative evidence-based Code Structure & Symbol Discovery Engine.
 *
 * Implements STATION 3 of the Universal AuditFlow Pipeline:
 * FILE CONTENT → CODE STRUCTURE → SYMBOL DISCOVERY
 *
 * Core Architectural Mandates:
 * 1. NAME IS NOT ARCHITECTURE: Symbols and structures are extracted from empirical
 *    file contents, tokens, and declarations — NEVER guessed from filenames or directories.
 * 2. SYMBOL HIERARCHY: Maintains exact parent-child ownership (Class -> Methods, Properties, Constructors).
 * 3. EVIDENCE LOCATION: Preserves 1-indexed line numbers and source coordinates for every symbol.
 * 4. EPISTEMIC ACCURACY: Honest reporting of missing content, syntax anomalies, and UNKNOWN states.
 */
object SourceCodeStructureExtractor {

    /**
     * Inspects a [SourceFileNode] and its raw textual content, producing a canonical [FileInspectionResult].
     */
    fun inspect(
        sourceNode: SourceFileNode,
        rawContent: String?
    ): FileInspectionResult {
        val relativePath = sourceNode.relativePath
        val fileType = sourceNode.semanticFileType

        // 1. Content Availability Assessment
        if (sourceNode.isDirectory) {
            return FileInspectionResult(
                relativePath = relativePath,
                physicalNodeType = PhysicalNodeType.DIRECTORY,
                pathClassification = sourceNode.pathClassification,
                semanticFileType = SemanticFileType.UNKNOWN,
                byteSize = 0L,
                contentAvailability = ContentAvailabilityState.UNAVAILABLE_NOT_FETCHED,
                parsingStatus = ParsingStatus.SKIPPED_NON_SOURCE
            )
        }

        if (rawContent == null) {
            return FileInspectionResult(
                relativePath = relativePath,
                physicalNodeType = PhysicalNodeType.FILE,
                pathClassification = sourceNode.pathClassification,
                semanticFileType = fileType,
                byteSize = sourceNode.sizeBytes,
                contentAvailability = ContentAvailabilityState.UNAVAILABLE_NOT_FETCHED,
                parsingStatus = ParsingStatus.UNAVAILABLE_CONTENT,
                parsingErrors = listOf("Raw file content was not supplied or not yet retrieved")
            )
        }

        if (rawContent.isEmpty()) {
            return FileInspectionResult(
                relativePath = relativePath,
                physicalNodeType = PhysicalNodeType.FILE,
                pathClassification = sourceNode.pathClassification,
                semanticFileType = fileType,
                byteSize = 0L,
                contentAvailability = ContentAvailabilityState.UNAVAILABLE_EMPTY,
                parsingStatus = ParsingStatus.UNAVAILABLE_CONTENT,
                linesOfCode = 0
            )
        }

        if (fileType == SemanticFileType.BINARY_OR_IMAGE || fileType == SemanticFileType.BYTECODE_ARCHIVE) {
            return FileInspectionResult(
                relativePath = relativePath,
                physicalNodeType = PhysicalNodeType.FILE,
                pathClassification = sourceNode.pathClassification,
                semanticFileType = fileType,
                byteSize = sourceNode.sizeBytes,
                contentAvailability = ContentAvailabilityState.UNAVAILABLE_BINARY,
                contentSha256 = calculateSha256(rawContent),
                parsingStatus = ParsingStatus.SKIPPED_NON_SOURCE,
                linesOfCode = 0
            )
        }

        val sha256 = calculateSha256(rawContent)
        val lines = rawContent.lines()
        val loc = lines.size

        return when (fileType) {
            SemanticFileType.KOTLIN_SOURCE,
            SemanticFileType.GRADLE_KOTLIN_DSL -> parseKotlinSource(relativePath, sourceNode, rawContent, lines, fileType, sha256)

            SemanticFileType.JAVA_SOURCE -> parseJavaSource(relativePath, sourceNode, rawContent, lines, fileType, sha256)

            SemanticFileType.ANDROID_MANIFEST,
            SemanticFileType.XML_RESOURCE_OR_LAYOUT -> parseXmlSource(relativePath, sourceNode, rawContent, lines, fileType, sha256)

            SemanticFileType.GRADLE_GROOVY_DSL,
            SemanticFileType.PROGUARD_R8_RULES,
            SemanticFileType.PROPERTIES,
            SemanticFileType.JSON,
            SemanticFileType.YAML,
            SemanticFileType.MARKDOWN,
            SemanticFileType.TEXT,
            SemanticFileType.UNKNOWN -> parseGenericText(relativePath, sourceNode, rawContent, lines, fileType, sha256)

            SemanticFileType.BINARY_OR_IMAGE,
            SemanticFileType.BYTECODE_ARCHIVE -> {
                FileInspectionResult(
                    relativePath = relativePath,
                    physicalNodeType = PhysicalNodeType.FILE,
                    pathClassification = sourceNode.pathClassification,
                    semanticFileType = fileType,
                    byteSize = sourceNode.sizeBytes,
                    contentAvailability = ContentAvailabilityState.UNAVAILABLE_BINARY,
                    contentSha256 = sha256,
                    parsingStatus = ParsingStatus.SKIPPED_NON_SOURCE,
                    linesOfCode = loc
                )
            }
        }
    }

    // ========================================================================
    // KOTLIN SOURCE PARSER
    // ========================================================================

    private fun parseKotlinSource(
        relativePath: String,
        sourceNode: SourceFileNode,
        rawContent: String,
        lines: List<String>,
        fileType: SemanticFileType,
        sha256: String
    ): FileInspectionResult {
        val parsingErrors = mutableListOf<String>()
        var declaredPackage: String? = null
        val imports = mutableListOf<ImportDeclaration>()
        val fileAnnotations = mutableListOf<String>()

        // 1. Extract Package & Imports & File Annotations
        for (i in lines.indices) {
            val lineNum = i + 1
            val trimmed = lines[i].trim()

            if (trimmed.startsWith("package ")) {
                val pkg = trimmed.removePrefix("package ")
                    .substringBefore(';')
                    .substringBefore("//")
                    .trim()
                if (pkg.isNotBlank()) {
                    declaredPackage = pkg
                }
            } else if (trimmed.startsWith("import ")) {
                val importStatement = trimmed.removePrefix("import ")
                    .substringBefore(';')
                    .substringBefore("//")
                    .trim()
                if (importStatement.isNotBlank()) {
                    val isWildcard = importStatement.endsWith(".*")
                    val alias = if (importStatement.contains(" as ")) {
                        importStatement.substringAfter(" as ").trim()
                    } else null
                    val fullPath = if (alias != null) importStatement.substringBefore(" as ").trim() else importStatement
                    val symbolName = if (isWildcard) "*" else fullPath.substringAfterLast('.')

                    imports.add(
                        ImportDeclaration(
                            importPath = fullPath,
                            importedSymbolName = symbolName,
                            isWildcard = isWildcard,
                            alias = alias,
                            lineNumber = lineNum
                        )
                    )
                }
            } else if (trimmed.startsWith("@file:")) {
                fileAnnotations.add(trimmed)
            }
        }

        // Package vs Path Discrepancy Check
        val packageDiscrepancy = checkPackageDiscrepancy(relativePath, declaredPackage)

        // 2. Tokenize and Extract Symbols with Hierarchy & Nesting
        val (topLevelSymbols, allSymbols) = extractKotlinSymbols(relativePath, declaredPackage ?: "", lines, parsingErrors)

        val parsingStatus = if (parsingErrors.isEmpty()) ParsingStatus.PARSED_SUCCESS else ParsingStatus.PARSED_PARTIAL

        return FileInspectionResult(
            relativePath = relativePath,
            physicalNodeType = PhysicalNodeType.FILE,
            pathClassification = sourceNode.pathClassification,
            semanticFileType = fileType,
            byteSize = sourceNode.sizeBytes.coerceAtLeast(rawContent.toByteArray().size.toLong()),
            contentAvailability = ContentAvailabilityState.AVAILABLE,
            contentSha256 = sha256,
            declaredPackage = declaredPackage,
            packageDiscrepancy = packageDiscrepancy,
            imports = imports,
            fileAnnotations = fileAnnotations,
            topLevelSymbols = topLevelSymbols,
            allSymbols = allSymbols,
            parsingStatus = parsingStatus,
            parsingErrors = parsingErrors,
            linesOfCode = lines.size
        )
    }

    // ========================================================================
    // KOTLIN SYMBOL PARSER WITH BRACE TRACKING & NESTING
    // ========================================================================

    private fun extractKotlinSymbols(
        relativePath: String,
        packageName: String,
        lines: List<String>,
        parsingErrors: MutableList<String>
    ): Pair<List<CodeSymbol>, List<CodeSymbol>> {
        val topLevelSymbols = mutableListOf<CodeSymbol>()
        val allSymbols = mutableListOf<CodeSymbol>()

        // Symbol builder stack tracking scope nesting: Pair<CodeSymbolBuilder, depthAtOpen>
        val scopeStack = mutableListOf<SymbolBuilder>()
        var currentBraceDepth = 0
        var pendingAnnotations = mutableListOf<String>()

        for (i in lines.indices) {
            val lineNum = i + 1
            val rawLine = lines[i]
            val lineWithoutComments = stripComments(rawLine).trim()

            if (lineWithoutComments.isBlank()) continue

            // Collect annotations
            if (lineWithoutComments.startsWith("@") && !lineWithoutComments.startsWith("@file:")) {
                // Collect annotation line
                val anno = lineWithoutComments.substringBefore(' ')
                pendingAnnotations.add(anno)
                // If line contains declaration after annotation, keep evaluating
                if (lineWithoutComments == anno) continue
            }

            // Check for declarations on this line
            val declaration = parseDeclarationLine(
                line = lineWithoutComments,
                lineNum = lineNum,
                relativePath = relativePath,
                packageName = packageName,
                annotations = pendingAnnotations.toList(),
                currentOwner = scopeStack.lastOrNull()?.name
            )

            if (declaration != null) {
                pendingAnnotations = mutableListOf() // reset used annotations

                val builder = SymbolBuilder(
                    name = declaration.name,
                    kind = declaration.kind,
                    definingFileRelativePath = relativePath,
                    packageName = packageName,
                    visibility = declaration.visibility,
                    isMethod = declaration.isMethod,
                    isSuspend = declaration.isSuspend,
                    isComposable = declaration.isComposable,
                    isOverride = declaration.isOverride,
                    parameters = declaration.parameters,
                    returnType = declaration.returnType,
                    containingSymbolName = declaration.containingSymbolName,
                    startLine = lineNum,
                    endLine = lineNum,
                    annotations = declaration.annotations,
                    modifiers = declaration.modifiers,
                    superTypes = declaration.superTypes,
                    braceDepth = currentBraceDepth
                )

                // Count open/close braces on this declaration line
                val openBracesOnLine = lineWithoutComments.count { it == '{' }
                val closeBracesOnLine = lineWithoutComments.count { it == '}' }

                if (openBracesOnLine > closeBracesOnLine) {
                    // This declaration opens a body/scope
                    if (scopeStack.isNotEmpty()) {
                        scopeStack.last().childBuilders.add(builder)
                    }
                    scopeStack.add(builder)
                } else {
                    // Terminal single-line or interface signature declaration
                    if (scopeStack.isNotEmpty()) {
                        scopeStack.last().childBuilders.add(builder)
                    } else {
                        val sym = builder.toCodeSymbol()
                        topLevelSymbols.add(sym)
                        allSymbols.add(sym)
                    }
                }
            } else if (scopeStack.isNotEmpty()) {
                // Non-declaration line inside an open symbol scope (e.g. function body)
                val currentScope = scopeStack.last()
                if (currentScope.isMethod || currentScope.kind == CodeSymbolKind.FUNCTION || currentScope.kind == CodeSymbolKind.CONSTRUCTOR || currentScope.kind == CodeSymbolKind.COMPOSABLE) {
                    val callsOnLine = extractInvocationsFromLine(lineWithoutComments, lineNum)
                    if (callsOnLine.isNotEmpty()) {
                        currentScope.invocations.addAll(callsOnLine)
                    }
                }
            }

            // Track brace level transitions for the line
            for (char in lineWithoutComments) {
                if (char == '{') {
                    currentBraceDepth++
                } else if (char == '}') {
                    currentBraceDepth--
                    if (scopeStack.isNotEmpty() && currentBraceDepth == scopeStack.last().braceDepth) {
                        val closedBuilder = scopeStack.removeAt(scopeStack.lastIndex)
                        closedBuilder.endLine = lineNum
                        val symbol = closedBuilder.toCodeSymbol()

                        if (scopeStack.isEmpty()) {
                            topLevelSymbols.add(symbol)
                        }
                    }
                }
            }
        }

        // Close any remaining unclosed symbols gracefully
        while (scopeStack.isNotEmpty()) {
            val unclosed = scopeStack.removeAt(scopeStack.lastIndex)
            unclosed.endLine = lines.size
            val symbol = unclosed.toCodeSymbol()
            if (scopeStack.isEmpty()) {
                topLevelSymbols.add(symbol)
            }
            parsingErrors.add("Unclosed symbol scope for '${symbol.name}' at line ${symbol.startLine}")
        }

        // Flatten all symbols
        fun collectAll(symbols: List<CodeSymbol>) {
            for (s in symbols) {
                allSymbols.add(s)
                collectAll(s.childSymbols)
            }
        }
        allSymbols.clear()
        collectAll(topLevelSymbols)

        return Pair(topLevelSymbols, allSymbols)
    }

    private fun parseDeclarationLine(
        line: String,
        lineNum: Int,
        relativePath: String,
        packageName: String,
        annotations: List<String>,
        currentOwner: String?
    ): CodeSymbol? {
        val isOverride = line.contains("override ")
        val isSuspend = line.contains("suspend ")
        val isComposable = annotations.any { it.contains("Composable") } || line.contains("@Composable")
        val visibility = when {
            line.contains("private ") -> "private"
            line.contains("protected ") -> "protected"
            line.contains("internal ") -> "internal"
            else -> "public"
        }

        // 1. Functions & Methods
        if (line.contains("fun ") || line.startsWith("fun ")) {
            val funIndex = line.indexOf("fun ")
            val afterFun = line.substring(funIndex + 4).trim()
            val funName = afterFun.substringBefore('(').substringBefore('<').trim()

            if (funName.isNotBlank() && isValidIdentifier(funName)) {
                val isMethod = currentOwner != null
                val kind = if (isComposable) CodeSymbolKind.COMPOSABLE else if (isMethod) CodeSymbolKind.METHOD else CodeSymbolKind.FUNCTION
                val paramsStr = afterFun.substringAfter('(', "").substringBeforeLast(')', "")
                val parameters = extractParameters(paramsStr)
                val returnType = extractReturnType(afterFun)

                return CodeSymbol(
                    name = funName,
                    kind = kind,
                    definingFileRelativePath = relativePath,
                    packageName = packageName,
                    visibility = visibility,
                    isMethod = isMethod,
                    isSuspend = isSuspend,
                    isComposable = isComposable,
                    isOverride = isOverride,
                    parameters = parameters,
                    returnType = returnType,
                    containingSymbolName = currentOwner,
                    startLine = lineNum,
                    endLine = lineNum,
                    annotations = annotations,
                    modifiers = extractModifiers(line)
                )
            }
        }

        // 2. Classes & Containers
        val classKinds = listOf(
            "data class " to CodeSymbolKind.DATA_CLASS,
            "sealed class " to CodeSymbolKind.SEALED_CLASS,
            "enum class " to CodeSymbolKind.ENUM,
            "class " to CodeSymbolKind.CLASS,
            "interface " to CodeSymbolKind.INTERFACE,
            "sealed interface " to CodeSymbolKind.INTERFACE,
            "companion object" to CodeSymbolKind.OBJECT,
            "object " to CodeSymbolKind.OBJECT
        )

        for ((keyword, kind) in classKinds) {
            if (line.contains(keyword)) {
                val afterKeyword = line.substring(line.indexOf(keyword) + keyword.length).trim()
                val className = if (keyword == "companion object" && (afterKeyword.isBlank() || afterKeyword.startsWith("{"))) {
                    "Companion"
                } else {
                    afterKeyword.substringBefore('(').substringBefore('{').substringBefore(':').substringBefore('<').trim()
                }

                if (className.isNotBlank() && isValidIdentifier(className)) {
                    val paramsStr = if (afterKeyword.contains('(')) afterKeyword.substringAfter('(').substringBefore(')') else ""
                    val parameters = extractParameters(paramsStr)
                    val superTypes = extractSuperTypes(afterKeyword)

                    return CodeSymbol(
                        name = className,
                        kind = kind,
                        definingFileRelativePath = relativePath,
                        packageName = packageName,
                        visibility = visibility,
                        isMethod = false,
                        parameters = parameters,
                        containingSymbolName = currentOwner,
                        startLine = lineNum,
                        endLine = lineNum,
                        annotations = annotations,
                        modifiers = extractModifiers(line),
                        superTypes = superTypes
                    )
                }
            }
        }

        // 3. Properties & Constants
        if (line.contains("val ") || line.contains("var ") || line.startsWith("val ") || line.startsWith("var ")) {
            val isConst = line.contains("const val ")
            val keyword = if (line.contains("val ")) "val " else "var "
            val afterKeyword = line.substring(line.indexOf(keyword) + keyword.length).trim()
            val propName = afterKeyword.substringBefore(':').substringBefore('=').substringBefore(' ').trim()

            if (propName.isNotBlank() && isValidIdentifier(propName) && propName != "get()" && propName != "set()") {
                val propType = if (afterKeyword.contains(':')) {
                    afterKeyword.substringAfter(':').substringBefore('=').trim()
                } else null

                return CodeSymbol(
                    name = propName,
                    kind = if (isConst) CodeSymbolKind.CONSTANT else CodeSymbolKind.PROPERTY,
                    definingFileRelativePath = relativePath,
                    packageName = packageName,
                    visibility = visibility,
                    isMethod = false,
                    returnType = propType,
                    containingSymbolName = currentOwner,
                    startLine = lineNum,
                    endLine = lineNum,
                    annotations = annotations,
                    modifiers = extractModifiers(line)
                )
            }
        }

        // 4. Type Aliases
        if (line.contains("typealias ") || line.startsWith("typealias ")) {
            val afterKeyword = line.substring(line.indexOf("typealias ") + 10).trim()
            val aliasName = afterKeyword.substringBefore('=').substringBefore('<').trim()
            if (aliasName.isNotBlank() && isValidIdentifier(aliasName)) {
                return CodeSymbol(
                    name = aliasName,
                    kind = CodeSymbolKind.TYPE_ALIAS,
                    definingFileRelativePath = relativePath,
                    packageName = packageName,
                    visibility = visibility,
                    isMethod = false,
                    containingSymbolName = currentOwner,
                    startLine = lineNum,
                    endLine = lineNum,
                    annotations = annotations
                )
            }
        }

        // 5. Explicit Secondary Constructors
        if (line.contains("constructor(") || line.contains("constructor (")) {
            val paramsStr = line.substringAfter("constructor").substringAfter('(').substringBefore(')')
            return CodeSymbol(
                name = "<init>",
                kind = CodeSymbolKind.CONSTRUCTOR,
                definingFileRelativePath = relativePath,
                packageName = packageName,
                visibility = visibility,
                isMethod = true,
                parameters = extractParameters(paramsStr),
                containingSymbolName = currentOwner,
                startLine = lineNum,
                endLine = lineNum,
                annotations = annotations
            )
        }

        return null
    }

    // ========================================================================
    // JAVA SOURCE PARSER
    // ========================================================================

    private fun parseJavaSource(
        relativePath: String,
        sourceNode: SourceFileNode,
        rawContent: String,
        lines: List<String>,
        fileType: SemanticFileType,
        sha256: String
    ): FileInspectionResult {
        var declaredPackage: String? = null
        val imports = mutableListOf<ImportDeclaration>()
        val topLevelSymbols = mutableListOf<CodeSymbol>()
        val allSymbols = mutableListOf<CodeSymbol>()

        for (i in lines.indices) {
            val lineNum = i + 1
            val trimmed = lines[i].trim()

            if (trimmed.startsWith("package ")) {
                declaredPackage = trimmed.removePrefix("package ").substringBefore(';').trim()
            } else if (trimmed.startsWith("import ")) {
                val fullPath = trimmed.removePrefix("import ").removePrefix("static ").substringBefore(';').trim()
                val isWildcard = fullPath.endsWith(".*")
                val symbolName = if (isWildcard) "*" else fullPath.substringAfterLast('.')
                imports.add(
                    ImportDeclaration(
                        importPath = fullPath,
                        importedSymbolName = symbolName,
                        isWildcard = isWildcard,
                        lineNumber = lineNum
                    )
                )
            } else if (trimmed.contains("class ") || trimmed.contains("interface ") || trimmed.contains("enum ")) {
                val kind = when {
                    trimmed.contains("interface ") -> CodeSymbolKind.INTERFACE
                    trimmed.contains("enum ") -> CodeSymbolKind.ENUM
                    else -> CodeSymbolKind.CLASS
                }
                val keyword = when (kind) {
                    CodeSymbolKind.INTERFACE -> "interface "
                    CodeSymbolKind.ENUM -> "enum "
                    else -> "class "
                }
                val name = trimmed.substringAfter(keyword).substringBefore('{').substringBefore(' ').substringBefore('<').trim()
                val javaSuperTypes = extractJavaSuperTypes(trimmed)
                if (name.isNotBlank() && isValidIdentifier(name)) {
                    val symbol = CodeSymbol(
                        name = name,
                        kind = kind,
                        definingFileRelativePath = relativePath,
                        packageName = declaredPackage ?: "",
                        visibility = if (trimmed.contains("private ")) "private" else "public",
                        startLine = lineNum,
                        endLine = lineNum,
                        superTypes = javaSuperTypes
                    )
                    topLevelSymbols.add(symbol)
                    allSymbols.add(symbol)
                }
            }
        }

        val packageDiscrepancy = checkPackageDiscrepancy(relativePath, declaredPackage)

        return FileInspectionResult(
            relativePath = relativePath,
            physicalNodeType = PhysicalNodeType.FILE,
            pathClassification = sourceNode.pathClassification,
            semanticFileType = fileType,
            byteSize = sourceNode.sizeBytes.coerceAtLeast(rawContent.toByteArray().size.toLong()),
            contentAvailability = ContentAvailabilityState.AVAILABLE,
            contentSha256 = sha256,
            declaredPackage = declaredPackage,
            packageDiscrepancy = packageDiscrepancy,
            imports = imports,
            topLevelSymbols = topLevelSymbols,
            allSymbols = allSymbols,
            parsingStatus = ParsingStatus.PARSED_SUCCESS,
            linesOfCode = lines.size
        )
    }

    // ========================================================================
    // XML & MANIFEST PARSER
    // ========================================================================

    private fun parseXmlSource(
        relativePath: String,
        sourceNode: SourceFileNode,
        rawContent: String,
        lines: List<String>,
        fileType: SemanticFileType,
        sha256: String
    ): FileInspectionResult {
        val topLevelSymbols = mutableListOf<CodeSymbol>()
        var declaredPackage: String? = null

        if (fileType == SemanticFileType.ANDROID_MANIFEST) {
            // Extract package from manifest
            val packageMatch = Regex("""package\s*=\s*"([^"]+)"""").find(rawContent)
            declaredPackage = packageMatch?.groupValues?.get(1)

            // Extract activities, services, receivers
            val componentPatterns = listOf(
                """<activity[^>]*android:name\s*=\s*"([^"]+)"""" to CodeSymbolKind.CLASS,
                """<service[^>]*android:name\s*=\s*"([^"]+)"""" to CodeSymbolKind.CLASS,
                """<receiver[^>]*android:name\s*=\s*"([^"]+)"""" to CodeSymbolKind.CLASS
            )

            for ((pattern, kind) in componentPatterns) {
                val matches = Regex(pattern).findAll(rawContent)
                for (match in matches) {
                    val compName = match.groupValues[1]
                    topLevelSymbols.add(
                        CodeSymbol(
                            name = compName,
                            kind = kind,
                            definingFileRelativePath = relativePath,
                            packageName = declaredPackage ?: "",
                            visibility = "public"
                        )
                    )
                }
            }
        }

        return FileInspectionResult(
            relativePath = relativePath,
            physicalNodeType = PhysicalNodeType.FILE,
            pathClassification = sourceNode.pathClassification,
            semanticFileType = fileType,
            byteSize = sourceNode.sizeBytes.coerceAtLeast(rawContent.toByteArray().size.toLong()),
            contentAvailability = ContentAvailabilityState.AVAILABLE,
            contentSha256 = sha256,
            declaredPackage = declaredPackage,
            topLevelSymbols = topLevelSymbols,
            allSymbols = topLevelSymbols,
            parsingStatus = ParsingStatus.PARSED_SUCCESS,
            linesOfCode = lines.size
        )
    }

    // ========================================================================
    // GENERIC TEXT PARSER
    // ========================================================================

    private fun parseGenericText(
        relativePath: String,
        sourceNode: SourceFileNode,
        rawContent: String,
        lines: List<String>,
        fileType: SemanticFileType,
        sha256: String
    ): FileInspectionResult {
        return FileInspectionResult(
            relativePath = relativePath,
            physicalNodeType = PhysicalNodeType.FILE,
            pathClassification = sourceNode.pathClassification,
            semanticFileType = fileType,
            byteSize = sourceNode.sizeBytes.coerceAtLeast(rawContent.toByteArray().size.toLong()),
            contentAvailability = ContentAvailabilityState.AVAILABLE,
            contentSha256 = sha256,
            parsingStatus = ParsingStatus.SKIPPED_NON_SOURCE,
            linesOfCode = lines.size
        )
    }

    // ========================================================================
    // HELPER UTILITIES
    // ========================================================================

    private fun checkPackageDiscrepancy(relativePath: String, declaredPackage: String?): Boolean {
        if (declaredPackage.isNullOrBlank()) return false
        val packageAsPath = declaredPackage.replace('.', '/')
        // Check if relative path contains the package path structure
        return !relativePath.contains(packageAsPath)
    }

    private fun extractParameters(paramsStr: String): List<ParameterSymbol> {
        if (paramsStr.isBlank()) return emptyList()
        val params = mutableListOf<ParameterSymbol>()
        val tokens = splitParameters(paramsStr)

        for (token in tokens) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) continue

            val hasDefault = cleanToken.contains('=')
            val beforeDefault = cleanToken.substringBefore('=').trim()
            val paramName = beforeDefault.substringBefore(':').removePrefix("val ").removePrefix("var ").trim()
            val paramType = if (beforeDefault.contains(':')) beforeDefault.substringAfter(':').trim() else "Any"

            if (paramName.isNotBlank() && isValidIdentifier(paramName)) {
                params.add(
                    ParameterSymbol(
                        name = paramName,
                        type = paramType,
                        hasDefaultValue = hasDefault
                    )
                )
            }
        }
        return params
    }

    private fun splitParameters(paramsStr: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()

        for (ch in paramsStr) {
            if (ch == '<' || ch == '(' || ch == '{' || ch == '[') depth++
            if (ch == '>' || ch == ')' || ch == '}' || ch == ']') depth--

            if (ch == ',' && depth == 0) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotBlank()) {
            result.add(current.toString())
        }
        return result
    }

    private fun extractReturnType(afterFun: String): String? {
        if (!afterFun.contains(')')) return null
        val afterParen = afterFun.substringAfterLast(')').trim()
        if (afterParen.startsWith(':')) {
            return afterParen.removePrefix(":").substringBefore('{').substringBefore('=').trim()
        }
        return null
    }

    private fun extractModifiers(line: String): List<String> {
        val recognized = listOf("suspend", "override", "open", "abstract", "inline", "tailrec", "data", "sealed", "const", "inner", "lateinit")
        return recognized.filter { line.contains("$it ") }
    }

    private fun stripComments(line: String): String {
        return if (line.contains("//")) line.substringBefore("//") else line
    }

    private fun isValidIdentifier(str: String): Boolean {
        if (str.isEmpty()) return false
        val first = str[0]
        if (!first.isLetter() && first != '_' && first != '<') return false
        return str.all { it.isLetterOrDigit() || it == '_' || it == '<' || it == '>' || it == '`' }
    }

    private fun calculateSha256(content: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractSuperTypes(afterKeyword: String): List<String> {
        val withoutParams = if (afterKeyword.contains('(')) {
            var depth = 0
            var afterIndex = -1
            for (i in afterKeyword.indices) {
                if (afterKeyword[i] == '(') depth++
                else if (afterKeyword[i] == ')') {
                    depth--
                    if (depth == 0) {
                        afterIndex = i + 1
                        break
                    }
                }
            }
            if (afterIndex != -1) afterKeyword.substring(afterIndex) else ""
        } else {
            val colonIdx = afterKeyword.indexOf(':')
            if (colonIdx != -1) afterKeyword.substring(colonIdx) else ""
        }

        val colonIdx = withoutParams.indexOf(':')
        if (colonIdx == -1) return emptyList()

        val superTypesPart = withoutParams.substring(colonIdx + 1)
            .substringBefore('{')
            .substringBefore("where ")
            .trim()

        if (superTypesPart.isBlank()) return emptyList()

        return splitParameters(superTypesPart)
            .map { it.trim().substringBefore('(').substringBefore('<').trim() }
            .filter { it.isNotBlank() && isValidIdentifier(it) }
    }

    private fun extractJavaSuperTypes(line: String): List<String> {
        val superTypes = mutableListOf<String>()
        if (line.contains("extends ")) {
            val extendsPart = line.substringAfter("extends ").substringBefore("implements").substringBefore('{').trim()
            val typeName = extendsPart.substringBefore('<').trim()
            if (typeName.isNotBlank() && isValidIdentifier(typeName)) {
                superTypes.add(typeName)
            }
        }
        if (line.contains("implements ")) {
            val implementsPart = line.substringAfter("implements ").substringBefore('{').trim()
            val types = splitParameters(implementsPart)
            for (t in types) {
                val typeName = t.substringBefore('<').trim()
                if (typeName.isNotBlank() && isValidIdentifier(typeName)) {
                    superTypes.add(typeName)
                }
            }
        }
        return superTypes
    }

    private val IGNORED_CALL_NAMES = setOf(
        "if", "when", "while", "for", "catch", "synchronized", "listOf", "mutableListOf",
        "mapOf", "mutableMapOf", "setOf", "mutableSetOf", "arrayOf", "println", "print",
        "require", "check", "assert", "run", "let", "also", "apply", "with", "takeIf",
        "takeUnless", "get", "set", "toString", "hashCode", "equals"
    )

    private val CALL_REGEX = Regex("""(?:([a-zA-Z_][a-zA-Z0-9_]*)\s*\.)?\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\(""")

    private fun extractInvocationsFromLine(line: String, lineNum: Int): List<CallInvocation> {
        val invocations = mutableListOf<CallInvocation>()
        val matches = CALL_REGEX.findAll(line)
        for (m in matches) {
            val receiver = m.groups[1]?.value
            val target = m.groups[2]?.value ?: continue

            if (target in IGNORED_CALL_NAMES) continue
            if (!isValidIdentifier(target)) continue

            invocations.add(
                CallInvocation(
                    targetName = target,
                    receiverName = receiver,
                    lineNumber = lineNum,
                    rawExpression = line.trim()
                )
            )
        }
        return invocations
    }

    private class SymbolBuilder(
        val name: String,
        val kind: CodeSymbolKind,
        val definingFileRelativePath: String,
        val packageName: String,
        val visibility: String,
        val isMethod: Boolean,
        val isSuspend: Boolean = false,
        val isComposable: Boolean = false,
        val isOverride: Boolean = false,
        val parameters: List<ParameterSymbol> = emptyList(),
        val returnType: String? = null,
        val containingSymbolName: String? = null,
        val startLine: Int,
        var endLine: Int,
        val annotations: List<String> = emptyList(),
        val modifiers: List<String> = emptyList(),
        val superTypes: List<String> = emptyList(),
        val braceDepth: Int,
        val childBuilders: MutableList<SymbolBuilder> = mutableListOf(),
        val invocations: MutableList<CallInvocation> = mutableListOf()
    ) {
        fun toCodeSymbol(): CodeSymbol {
            return CodeSymbol(
                name = name,
                kind = kind,
                definingFileRelativePath = definingFileRelativePath,
                packageName = packageName,
                visibility = visibility,
                isMethod = isMethod,
                isSuspend = isSuspend,
                isComposable = isComposable,
                isOverride = isOverride,
                parameters = parameters,
                returnType = returnType,
                containingSymbolName = containingSymbolName,
                startLine = startLine,
                endLine = endLine,
                annotations = annotations,
                modifiers = modifiers,
                childSymbols = childBuilders.map { it.toCodeSymbol() },
                superTypes = superTypes,
                invocations = invocations.toList()
            )
        }
    }
}
