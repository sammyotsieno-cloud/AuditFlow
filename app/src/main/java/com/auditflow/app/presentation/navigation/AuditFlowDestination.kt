package com.auditflow.app.presentation.navigation

/**
 * Foundation navigation destinations for AuditFlow.
 *
 * Distinguishes between implemented destinations (HOME)
 * and future milestone destinations which render a strict NOT IMPLEMENTED YET state.
 */
sealed class AuditFlowDestination(
    val route: String,
    val title: String,
    val isImplemented: Boolean
) {
    data object Home : AuditFlowDestination(
        route = "home",
        title = "Home",
        isImplemented = true
    )

    data object ProjectInput : AuditFlowDestination(
        route = "project_input",
        title = "Project Input",
        isImplemented = false
    )

    data object SourceTree : AuditFlowDestination(
        route = "source_tree",
        title = "Source Tree",
        isImplemented = false
    )

    data object FileInspection : AuditFlowDestination(
        route = "file_inspection",
        title = "File Inspection",
        isImplemented = false
    )

    data object Audit : AuditFlowDestination(
        route = "audit",
        title = "Audit",
        isImplemented = false
    )

    data object Workflow : AuditFlowDestination(
        route = "workflow",
        title = "Workflow",
        isImplemented = false
    )

    data object Evidence : AuditFlowDestination(
        route = "evidence",
        title = "Evidence",
        isImplemented = false
    )

    data object Results : AuditFlowDestination(
        route = "results",
        title = "Results",
        isImplemented = false
    )

    data object Settings : AuditFlowDestination(
        route = "settings",
        title = "Settings",
        isImplemented = false
    )

    companion object {
        val allDestinations: List<AuditFlowDestination>
            get() = listOf(
                Home,
                ProjectInput,
                SourceTree,
                FileInspection,
                Audit,
                Workflow,
                Evidence,
                Results,
                Settings
            )

        fun fromRoute(route: String?): AuditFlowDestination {
            return allDestinations.firstOrNull { it.route == route } ?: Home
        }
    }
}
