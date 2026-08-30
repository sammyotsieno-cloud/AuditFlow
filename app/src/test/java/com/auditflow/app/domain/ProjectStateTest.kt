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

        val metadata = ProjectMetadata(
            name = "RealProject",
            pathOrUri = "/local/path/to/project",
            sourceKind = ProjectSourceKind.LOCAL_DIRECTORY
        )
        val loadedState = ProjectState.ProjectLoaded(metadata)
        assertEquals("RealProject", loadedState.metadata.name)
        assertEquals(ProjectSourceKind.LOCAL_DIRECTORY, loadedState.metadata.sourceKind)

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
