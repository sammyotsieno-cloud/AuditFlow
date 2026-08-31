package com.auditflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.auditflow.app.data.local.AuditFlowPreferences
import com.auditflow.app.data.repository.ProjectStateRepositoryImpl
import com.auditflow.app.data.repository.SettingsRepositoryImpl
import com.auditflow.app.presentation.home.HomeViewModel
import com.auditflow.app.presentation.navigation.AuditFlowNavHost
import com.auditflow.app.presentation.theme.AuditFlowTheme

/**
 * Real Android Activity entry point for AuditFlow.
 *
 * Remains thin and delegates domain/state responsibility to ViewModel and Clean Architecture layers.
 */
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = (application as? AuditFlowApplication)
                    ?: (applicationContext as? AuditFlowApplication)
                    ?: AuditFlowApplication.instanceOrNull
                val projectRepo = app?.projectStateRepository
                    ?: ProjectStateRepositoryImpl(AuditFlowPreferences(applicationContext))
                val settingsRepo = app?.settingsRepository
                    ?: SettingsRepositoryImpl(AuditFlowPreferences(applicationContext))
                return HomeViewModel(
                    projectStateRepository = projectRepo,
                    settingsRepository = settingsRepo
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuditFlowTheme {
                val navController = rememberNavController()
                AuditFlowNavHost(
                    navController = navController,
                    homeViewModel = homeViewModel
                )
            }
        }
    }
}
