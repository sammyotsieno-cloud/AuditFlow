package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.inspection.SourceCodeStructureExtractor
import com.auditflow.app.domain.model.DependencyKind
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.ResolutionStatus
import com.auditflow.app.domain.model.SourceFileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossFileRelationshipResolverTest {

    @Test
    fun `resolver maps inheritance, interface implementation, constructor dependencies, and call edges`() {
        val interfaceSource = """
            package com.example.domain
            
            interface AuditScanner {
                fun scanProject(): Int
            }
        """.trimIndent()

        val implSource = """
            package com.example.service
            
            import com.example.domain.AuditScanner
            
            class FastAuditScanner : AuditScanner {
                override fun scanProject(): Int {
                    return calculateScore()
                }
                
                private fun calculateScore(): Int {
                    return 100
                }
            }
        """.trimIndent()

        val callerSource = """
            package com.example.ui
            
            import com.example.domain.AuditScanner
            import com.example.service.FastAuditScanner
            
            class AuditViewModel(private val scanner: FastAuditScanner) {
                fun onAuditRequested() {
                    scanner.scanProject()
                }
            }
        """.trimIndent()

        val file1 = SourceFileNode("app/src/main/java/com/example/domain/AuditScanner.kt", "AuditScanner.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)
        val file2 = SourceFileNode("app/src/main/java/com/example/service/FastAuditScanner.kt", "FastAuditScanner.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)
        val file3 = SourceFileNode("app/src/main/java/com/example/ui/AuditViewModel.kt", "AuditViewModel.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)

        val insp1 = SourceCodeStructureExtractor.inspect(file1, interfaceSource)
        val insp2 = SourceCodeStructureExtractor.inspect(file2, implSource)
        val insp3 = SourceCodeStructureExtractor.inspect(file3, callerSource)

        val registry = ProjectSymbolRegistry.build(listOf(insp1, insp2, insp3))
        val resolver = CrossFileRelationshipResolver()
        val graph = resolver.resolve(listOf(insp1, insp2, insp3), registry)

        // 1. Verify Implementation Edge
        assertEquals(1, graph.implementations.size)
        val implEdge = graph.implementations.first()
        assertEquals("com.example.service.FastAuditScanner", implEdge.implementingTypeFqn)
        assertEquals("com.example.domain.AuditScanner", implEdge.interfaceTypeFqn)
        assertEquals(ResolutionStatus.RESOLVED, implEdge.resolutionStatus)
        assertFalse(implEdge.isExternal)

        // 2. Verify Inheritance Edge
        assertEquals(1, graph.inheritances.size)
        val inhEdge = graph.inheritances.first()
        assertEquals("com.example.service.FastAuditScanner", inhEdge.subTypeFqn)
        assertEquals("com.example.domain.AuditScanner", inhEdge.superTypeFqn)
        assertTrue(inhEdge.isInterfaceImplementation)

        // 3. Verify Constructor Parameter Dependency
        val constructorParamDeps = graph.dependencies.filter { it.dependencyKind == DependencyKind.CONSTRUCTOR_PARAM }
        assertTrue(constructorParamDeps.isNotEmpty())
        val vmDep = constructorParamDeps.first { it.sourceFileRelativePath == file3.relativePath }
        assertEquals("com.example.service.FastAuditScanner", vmDep.targetSymbolFqn)

        // 4. Verify Call Invocations
        assertTrue(graph.calls.isNotEmpty())
        val intraFileCall = graph.calls.firstOrNull { it.callerSymbol == "scanProject" && it.calleeSymbol == "calculateScore" }
        assertTrue("Intra-file call calculateScore must be mapped", intraFileCall != null)

        val crossFileCall = graph.calls.firstOrNull { it.callerSymbol == "onAuditRequested" && it.calleeSymbol == "scanProject" }
        assertTrue("Cross-file call scanner.scanProject must be mapped", crossFileCall != null)
    }

    @Test
    fun `graph query helpers return accurate dependents, callers, and subtypes`() {
        val baseSource = """
            package com.example.base
            open class BaseService
        """.trimIndent()

        val subSource = """
            package com.example.impl
            import com.example.base.BaseService
            class EnhancedService : BaseService()
        """.trimIndent()

        val file1 = SourceFileNode("app/src/main/java/com/example/base/BaseService.kt", "BaseService.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)
        val file2 = SourceFileNode("app/src/main/java/com/example/impl/EnhancedService.kt", "EnhancedService.kt", "kt", 100L, false, pathClassification = PathClassification.ESTABLISHED)

        val insp1 = SourceCodeStructureExtractor.inspect(file1, baseSource)
        val insp2 = SourceCodeStructureExtractor.inspect(file2, subSource)

        val registry = ProjectSymbolRegistry.build(listOf(insp1, insp2))
        val graph = CrossFileRelationshipResolver().resolve(listOf(insp1, insp2), registry)

        // Subtypes query
        val subtypes = graph.getSubtypesOf("com.example.base.BaseService")
        assertEquals(1, subtypes.size)
        assertEquals("com.example.impl.EnhancedService", subtypes.first().subTypeFqn)

        // Dependents query
        val dependents = graph.getDependentsForFile("app/src/main/java/com/example/base/BaseService.kt")
        assertTrue(dependents.any { it.sourceFileRelativePath == "app/src/main/java/com/example/impl/EnhancedService.kt" })
    }
}
