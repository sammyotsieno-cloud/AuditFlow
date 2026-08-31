package com.auditflow.app.presentation

import com.auditflow.app.presentation.navigation.AuditFlowDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditFlowNavigationTest {

    @Test
    fun startupRouteAccessOrder_doesNotProduceNullDestinations() {
        // Regression test for Android startup crash:
        // Accessing AuditFlowDestination.Home.route first must not cause null entries in allDestinations
        val homeRoute = AuditFlowDestination.Home.route
        assertEquals("home", homeRoute)

        val destinations = AuditFlowDestination.allDestinations
        assertEquals(9, destinations.size)

        // Verify every destination in the list is non-null and possesses valid non-empty route and title
        destinations.forEach { destination ->
            assertTrue("Destination route must not be blank", destination.route.isNotBlank())
            assertTrue("Destination title must not be blank", destination.title.isNotBlank())
        }

        // Verify all 9 distinct destination instances are present
        val expectedDestinations = listOf(
            AuditFlowDestination.Home,
            AuditFlowDestination.ProjectInput,
            AuditFlowDestination.SourceTree,
            AuditFlowDestination.FileInspection,
            AuditFlowDestination.Audit,
            AuditFlowDestination.Workflow,
            AuditFlowDestination.Evidence,
            AuditFlowDestination.Results,
            AuditFlowDestination.Settings
        )
        assertEquals(expectedDestinations, destinations)
    }

    @Test
    fun destinationCount_andImplementationStatus() {
        val all = AuditFlowDestination.allDestinations
        assertEquals(9, all.size)

        // Only Home is implemented in Phase 1A
        assertTrue(AuditFlowDestination.Home.isImplemented)

        // All future routes are strictly marked as not implemented
        assertFalse(AuditFlowDestination.ProjectInput.isImplemented)
        assertFalse(AuditFlowDestination.SourceTree.isImplemented)
        assertFalse(AuditFlowDestination.FileInspection.isImplemented)
        assertFalse(AuditFlowDestination.Audit.isImplemented)
        assertFalse(AuditFlowDestination.Workflow.isImplemented)
        assertFalse(AuditFlowDestination.Evidence.isImplemented)
        assertFalse(AuditFlowDestination.Results.isImplemented)
        assertFalse(AuditFlowDestination.Settings.isImplemented)
    }

    @Test
    fun fromRoute_resolvesCorrectly() {
        assertEquals(AuditFlowDestination.Home, AuditFlowDestination.fromRoute("home"))
        assertEquals(AuditFlowDestination.ProjectInput, AuditFlowDestination.fromRoute("project_input"))
        assertEquals(AuditFlowDestination.SourceTree, AuditFlowDestination.fromRoute("source_tree"))
        assertEquals(AuditFlowDestination.FileInspection, AuditFlowDestination.fromRoute("file_inspection"))
        assertEquals(AuditFlowDestination.Audit, AuditFlowDestination.fromRoute("audit"))
        assertEquals(AuditFlowDestination.Workflow, AuditFlowDestination.fromRoute("workflow"))
        assertEquals(AuditFlowDestination.Evidence, AuditFlowDestination.fromRoute("evidence"))
        assertEquals(AuditFlowDestination.Results, AuditFlowDestination.fromRoute("results"))
        assertEquals(AuditFlowDestination.Settings, AuditFlowDestination.fromRoute("settings"))
        assertEquals(AuditFlowDestination.Home, AuditFlowDestination.fromRoute("unknown_route"))
    }
}
