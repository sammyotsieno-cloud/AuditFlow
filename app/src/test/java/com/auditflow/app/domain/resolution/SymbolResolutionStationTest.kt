package com.auditflow.app.domain.resolution

import com.auditflow.app.domain.inspection.SourceCodeStructureExtractor
import com.auditflow.app.domain.model.EpistemicClassification
import com.auditflow.app.domain.model.ForensicDefectKind
import com.auditflow.app.domain.model.PathClassification
import com.auditflow.app.domain.model.SourceFileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolResolutionStationTest {

    @Test
    fun `station 4 detects circular dependencies and produces epistemic FACT finding`() {
        val fileASource = """
            package com.example.cycle
            import com.example.cycle.ServiceB
            class ServiceA(private val b: ServiceB)
        """.trimIndent()

        val fileBSource = """
            package com.example.cycle
            import com.example.cycle.ServiceA
            class ServiceB(private val a: ServiceA)
        """.trimIndent()

        val fileA = SourceFileNode("app/src/main/java/com/example/cycle/ServiceA.kt", "ServiceA.kt", "kt", 100, false, PathClassification.ESTABLISHED)
        val fileB = SourceFileNode("app/src/main/java/com/example/cycle/ServiceB.kt", "ServiceB.kt", "kt", 100, false, PathClassification.ESTABLISHED)

        val inspA = SourceCodeStructureExtractor.inspect(fileA, fileASource)
        val inspB = SourceCodeStructureExtractor.inspect(fileB, fileBSource)

        val station = SymbolResolutionStation()
        val result = station.process(listOf(inspA, inspB))

        val circularFindings = result.findings.filter { it.defectKind == ForensicDefectKind.CIRCULAR_DEPENDENCY }
        assertTrue("Circular dependency must be detected", circularFindings.isNotEmpty())
        assertEquals(EpistemicClassification.FACT, circularFindings.first().epistemicType)
        assertTrue(result.summary.totalDefectsFound > 0)
    }

    @Test
    fun `station 4 detects invalid dependency direction when domain layer imports UI framework`() {
        val domainSource = """
            package com.example.domain.model
            
            import androidx.compose.runtime.Composable
            import android.view.View
            
            data class PureDomainEntity(val id: String)
        """.trimIndent()

        val file = SourceFileNode("app/src/main/java/com/example/domain/model/PureDomainEntity.kt", "PureDomainEntity.kt", "kt", 100, false, PathClassification.ESTABLISHED)
        val insp = SourceCodeStructureExtractor.inspect(file, domainSource)

        val station = SymbolResolutionStation()
        val result = station.process(listOf(insp))

        val invalidDirectionFindings = result.findings.filter { it.defectKind == ForensicDefectKind.INVALID_DEPENDENCY_DIRECTION }
        assertTrue("Invalid dependency direction must be detected", invalidDirectionFindings.isNotEmpty())
        assertEquals(EpistemicClassification.FACT, invalidDirectionFindings.first().epistemicType)
    }

    @Test
    fun `station 4 detects broken dependencies on missing unresolvable targets`() {
        val source = """
            package com.example.app
            
            import com.missing.ghost.NonExistentClass
            
            class AppRunner(private val ghost: NonExistentClass)
        """.trimIndent()

        val file = SourceFileNode("app/src/main/java/com/example/app/AppRunner.kt", "AppRunner.kt", "kt", 100, false, PathClassification.ESTABLISHED)
        val insp = SourceCodeStructureExtractor.inspect(file, source)

        val station = SymbolResolutionStation()
        val result = station.process(listOf(insp))

        val brokenDeps = result.findings.filter { it.defectKind == ForensicDefectKind.BROKEN_DEPENDENCY }
        assertTrue("Broken dependency on missing class must be detected", brokenDeps.isNotEmpty())
    }

    @Test
    fun `station 4 detects terminology inconsistency when filename diverges from primary class`() {
        val source = """
            package com.example.app
            class ActualCalculator
        """.trimIndent()

        val file = SourceFileNode("app/src/main/java/com/example/app/BananaHelper.kt", "BananaHelper.kt", "kt", 100, false, PathClassification.ESTABLISHED)
        val insp = SourceCodeStructureExtractor.inspect(file, source)

        val station = SymbolResolutionStation()
        val result = station.process(listOf(insp))

        val termFindings = result.findings.filter { it.defectKind == ForensicDefectKind.TERMINOLOGY_INCONSISTENCY }
        assertTrue("Terminology inconsistency must be detected", termFindings.isNotEmpty())
    }

    @Test
    fun `station 4 calculates complete resolution summary statistics accurately`() {
        val s1 = """
            package com.example.domain
            interface TaskService {
                fun execute(): Boolean
            }
        """.trimIndent()

        val s2 = """
            package com.example.data
            import com.example.domain.TaskService
            class TaskServiceImpl : TaskService {
                override fun execute(): Boolean = true
            }
        """.trimIndent()

        val f1 = SourceFileNode("app/src/main/java/com/example/domain/TaskService.kt", "TaskService.kt", "kt", 100, false, PathClassification.ESTABLISHED)
        val f2 = SourceFileNode("app/src/main/java/com/example/data/TaskServiceImpl.kt", "TaskServiceImpl.kt", "kt", 100, false, PathClassification.ESTABLISHED)

        val insp1 = SourceCodeStructureExtractor.inspect(f1, s1)
        val insp2 = SourceCodeStructureExtractor.inspect(f2, s2)

        val station = SymbolResolutionStation()
        val result = station.process(listOf(insp1, insp2))

        assertEquals(2, result.summary.totalFilesInspected)
        assertTrue(result.summary.totalSymbolsIndexed >= 3)
        assertEquals(1, result.summary.totalInheritances)
        assertEquals(1, result.summary.totalImplementations)
    }
}
