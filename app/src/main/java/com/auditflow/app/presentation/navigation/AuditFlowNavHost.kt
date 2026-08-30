package com.auditflow.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.auditflow.app.presentation.destination.NotImplementedScreen
import com.auditflow.app.presentation.home.HomeScreen
import com.auditflow.app.presentation.home.HomeViewModel

@Composable
fun AuditFlowNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    NavHost(
        navController = navController,
        startDestination = AuditFlowDestination.Home.route
    ) {
        // HOME SCREEN (Fully implemented in Phase 1A)
        composable(AuditFlowDestination.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToDestination = { destination ->
                    navController.navigate(destination.route)
                }
            )
        }

        // FUTURE SCREENS (Truthfully marked as NOT IMPLEMENTED YET)
        AuditFlowDestination.allDestinations
            .filter { it != AuditFlowDestination.Home }
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
