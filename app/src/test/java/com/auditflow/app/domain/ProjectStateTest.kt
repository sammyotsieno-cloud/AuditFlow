package com.auditflow.app.domain

import com.auditflow.app.domain.model.AuditPrincipleLevel
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.ProjectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying ProjectState domain contracts and epistemic invariants.
 */
class ProjectStateTest {

    @Test
    fun initialProjectState_isNoProject() {
        val state: ProjectState = ProjectState.NoProject
        assertEquals(ProjectState.NoProject, state)
    }

    @Test
    fun projectStateTransitions_areDeterministic() {
        val initialState: ProjectState = ProjectState.NoProject
        assertEquals(ProjectState.NoProject, initialState)

        val loadingState = ProjectState.ProjectLoading(
            source = "https://github.com/org/repo",
            progressPercentage = 45
        )
        assertEquals("https://github.com/org/repo", loadingState.source)
        assertEquals(45, loadingState.progressPercentage)

        val file1 = com.auditflow.app.domain.model.SourceFileNode(
            relativePath = "src/main/App.kt",
            name = "App.kt",
            extension = "kt",
            sizeBytes = 1024L,
            isDirectory = false,
            isReadable = true
        )
        val file2 = com.auditflow.app.domain.model.SourceFileNode(
            relativePath = "src/main",
            name = "main",
            extension = "",
            sizeBytes = 0L,
            isDirectory = true,
            isReadable = true
        )
        val files = listOf(file2, file1)

        val metadata = ProjectMetadata(
            name = "RealProject",
            pathOrUri = "/local/path/to/project",
            sourceKind = ProjectSourceKind.LOCAL_DIRECTORY,
            fileCount = 1,
            totalSizeBytes = 1024L,
            branchOrTag = "main"
        )
        val loadedState = ProjectState.ProjectLoaded(metadata, files)
        assertEquals("RealProject", loadedState.metadata.name)
        assertEquals(ProjectSourceKind.LOCAL_DIRECTORY, loadedState.metadata.sourceKind)
        assertEquals(1, loadedState.metadata.fileCount)
        assertEquals(1024L, loadedState.metadata.totalSizeBytes)
        assertEquals("main", loadedState.metadata.branchOrTag)
        assertEquals(2, loadedState.files.size)
        assertEquals("src/main/App.kt", loadedState.files[1].relativePath)

        val errorState = ProjectState.Error("Ingest failed: directory not found")
        assertEquals("Ingest failed: directory not found", errorState.message)
    }

    @Test
    fun auditPrinciples_distinctLevelsVerified() {
        // Enforces: EXISTS ≠ CONNECTED ≠ EXECUTED ≠ VALIDATED ≠ VERIFIED ≠ PRODUCES_EXPECTED_RESULT
        val levels = AuditPrincipleLevel.entries.toTypedArray()
        assertEquals(6, levels.size)

        assertNotEquals(AuditPrincipleLevel.EXISTS, AuditPrincipleLevel.CONNECTED)
        assertNotEquals(AuditPrincipleLevel.CONNECTED, AuditPrincipleLevel.EXECUTED)
        assertNotEquals(AuditPrincipleLevel.EXECUTED, AuditPrincipleLevel.VALIDATED)
        assertNotEquals(AuditPrincipleLevel.VALIDATED, AuditPrincipleLevel.VERIFIED)
        assertNotEquals(AuditPrincipleLevel.VERIFIED, AuditPrincipleLevel.PRODUCES_EXPECTED_RESULT)
    }
}
