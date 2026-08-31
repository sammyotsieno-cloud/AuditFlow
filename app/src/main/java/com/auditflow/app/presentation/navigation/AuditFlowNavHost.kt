package com.auditflow.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.auditflow.app.presentation.destination.NotImplementedScreen
import com.auditflow.app.presentation.home.HomeScreen
import com.auditflow.app.presentation.home.HomeViewModel
import com.auditflow.app.presentation.input.ProjectInputScreen
import com.auditflow.app.presentation.tree.SourceTreeScreen

@Composable
fun AuditFlowNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    NavHost(
        navController = navController,
        startDestination = AuditFlowDestination.Home.route
    ) {
        // HOME SCREEN
        composable(AuditFlowDestination.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToDestination = { destination ->
                    navController.navigate(destination.route)
                }
            )
        }

        // PROJECT INPUT SCREEN (Phase 1B)
        composable(AuditFlowDestination.ProjectInput.route) {
            ProjectInputScreen(
                viewModel = homeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSourceTree = {
                    navController.navigate(AuditFlowDestination.SourceTree.route)
                }
            )
        }

        // SOURCE TREE SCREEN (Phase 1B)
        composable(AuditFlowDestination.SourceTree.route) {
            SourceTreeScreen(
                viewModel = homeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToProjectInput = {
                    navController.navigate(AuditFlowDestination.ProjectInput.route)
                }
            )
        }

        // FUTURE SCREENS (Truthfully marked as NOT IMPLEMENTED YET)
        AuditFlowDestination.allDestinations
            .filter { !it.isImplemented }
            .forEach { destination ->
                composable(destination.route) {
                    NotImplementedScreen(
                        destination = destination,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
    }
}

