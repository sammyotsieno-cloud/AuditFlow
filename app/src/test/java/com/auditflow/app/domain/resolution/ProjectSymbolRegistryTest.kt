package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.inspection.SourceCodeStructureExtractor
import com.auditflow.app.domain.model.CodeSymbolKind
import com.auditflow.app.domain.model.ImportDeclaration
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.ResolutionStatus
import com.auditflow.app.domain.model.SourceFileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSymbolRegistryTest {

    @Test
    fun `registry indexes symbols by FQN, short name, package, and file path`() {
        val modelSource = """
            package com.example.domain.model
            
            data class UserAccount(val id: String, val username: String)
            interface AccountRepository {
                fun findById(id: String): UserAccount?
            }
        """.trimIndent()

        val repoSource = """
            package com.example.data.repository
            
            import com.example.domain.model.UserAccount
            import com.example.domain.model.AccountRepository
            
            class AccountRepositoryImpl : AccountRepository {
                override fun findById(id: String): UserAccount? = null
            }
        """.trimIndent()

        val file1 = SourceFileNode("app/src/main/java/com/example/domain/model/UserAccount.kt", "UserAccount.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)
        val file2 = SourceFileNode("app/src/main/java/com/example/data/repository/AccountRepositoryImpl.kt", "AccountRepositoryImpl.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)

        val insp1 = SourceCodeStructureExtractor.inspect(file1, modelSource)
        val insp2 = SourceCodeStructureExtractor.inspect(file2, repoSource)

        val registry = ProjectSymbolRegistry.build(listOf(insp1, insp2))

        assertEquals(4, registry.getAllSymbols().size) // UserAccount, AccountRepository, findById, AccountRepositoryImpl (plus inner override findById)

        // Lookup by FQN
        val userAccount = registry.findSymbolByFqn("com.example.domain.model.UserAccount")
        assertNotNull(userAccount)
        assertEquals("UserAccount", userAccount?.name)
        assertEquals(CodeSymbolKind.DATA_CLASS, userAccount?.kind)

        val repoInterface = registry.findSymbolByFqn("com.example.domain.model.AccountRepository")
        assertNotNull(repoInterface)
        assertEquals(CodeSymbolKind.INTERFACE, repoInterface?.kind)

        // Lookup by short name
        val accounts = registry.findSymbolsByName("UserAccount")
        assertEquals(1, accounts.size)

        // Lookup by package
        val domainSymbols = registry.findSymbolsInPackage("com.example.domain.model")
        assertEquals(2, domainSymbols.size)

        // Lookup by file
        val file2Symbols = registry.findSymbolsInFile("app/src/main/java/com/example/data/repository/AccountRepositoryImpl.kt")
        assertTrue(file2Symbols.any { it.name == "AccountRepositoryImpl" })
    }

    @Test
    fun `resolveImport correctly distinguishes local symbols, external SDK packages, and unresolved targets`() {
        val domainSource = """
            package com.example.domain
            class DomainEngine
        """.trimIndent()

        val file1 = SourceFileNode("app/src/main/java/com/example/domain/DomainEngine.kt", "DomainEngine.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)
        val insp1 = SourceCodeStructureExtractor.inspect(file1, domainSource)
        val registry = ProjectSymbolRegistry.build(listOf(insp1))

        // 1. Local symbol import
        val localImport = ImportDeclaration(
            importPath = "com.example.domain.DomainEngine",
            importedSymbolName = "DomainEngine",
            lineNumber = 2
        )
        val resolvedLocal = registry.resolveImport(localImport, "app/src/main/java/com/example/ui/Ui.kt")
        assertEquals(1, resolvedLocal.size)
        assertEquals(ResolutionStatus.RESOLVED, resolvedLocal[0].status)
        assertFalse(resolvedLocal[0].isExternal)
        assertNotNull(resolvedLocal[0].resolvedSymbol)

        // 2. External framework import
        val externalImport = ImportDeclaration(
            importPath = "androidx.compose.runtime.Composable",
            importedSymbolName = "Composable",
            lineNumber = 3
        )
        val resolvedExternal = registry.resolveImport(externalImport, "app/src/main/java/com/example/ui/Ui.kt")
        assertEquals(1, resolvedExternal.size)
        assertEquals(ResolutionStatus.EXTERNAL, resolvedExternal[0].status)
        assertTrue(resolvedExternal[0].isExternal)
        assertNull(resolvedExternal[0].resolvedSymbol)

        // 3. Unresolved import (missing target)
        val brokenImport = ImportDeclaration(
            importPath = "com.nonexistent.fake.GhostService",
            importedSymbolName = "GhostService",
            lineNumber = 4
        )
        val resolvedBroken = registry.resolveImport(brokenImport, "app/src/main/java/com/example/ui/Ui.kt")
        assertEquals(1, resolvedBroken.size)
        assertEquals(ResolutionStatus.UNRESOLVED, resolvedBroken[0].status)
        assertFalse(resolvedBroken[0].isExternal)
    }

    @Test
    fun `resolveTypeReference resolves primitive types, local package types, and imported types`() {
        val modelSource = """
            package com.example.domain.model
            data class Currency(val code: String)
        """.trimIndent()

        val file1 = SourceFileNode("app/src/main/java/com/example/domain/model/Currency.kt", "Currency.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)
        val insp1 = SourceCodeStructureExtractor.inspect(file1, modelSource)
        val registry = ProjectSymbolRegistry.build(listOf(insp1))

        // Primitive type
        val primTarget = registry.resolveTypeReference("app/src/main/java/com/example/domain/model/Currency.kt", "String", insp1)
        assertEquals(ResolutionStatus.EXTERNAL, primTarget.status)
        assertTrue(primTarget.isExternal)

        // Same package local type
        val localTarget = registry.resolveTypeReference("app/src/main/java/com/example/domain/model/Other.kt", "Currency", insp1)
        assertEquals(ResolutionStatus.RESOLVED, localTarget.status)
        assertEquals("com.example.domain.model.Currency", localTarget.resolvedFqn)
        assertNotNull(localTarget.resolvedSymbol)
    }
}
