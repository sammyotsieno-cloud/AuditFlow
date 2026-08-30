package com.auditflow.app

import android.app.Application
import com.auditflow.app.data.local.AuditFlowPreferences
import com.auditflow.app.data.repository.ProjectStateRepositoryImpl
import com.auditflow.app.data.repository.SettingsRepositoryImpl
import com.auditflow.app.domain.repository.ProjectStateRepository
import com.auditflow.app.domain.repository.SettingsRepository

/**
 * Real Android Application entry point for AuditFlow.
 *
 * Initializes application-level singletons (preferences and repositories)
 * without global mutable state or fake demonstration fixtures.
 */
class AuditFlowApplication : Application() {

    lateinit var preferences: AuditFlowPreferences
        private set

    lateinit var projectStateRepository: ProjectStateRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferences = AuditFlowPreferences(applicationContext)
        projectStateRepository = ProjectStateRepositoryImpl(preferences)
        settingsRepository = SettingsRepositoryImpl(preferences)
    }

    companion object {
        lateinit var instance: AuditFlowApplication
            private set
    }
}
