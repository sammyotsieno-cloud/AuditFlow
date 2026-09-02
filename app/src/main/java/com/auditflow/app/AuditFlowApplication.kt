package com.auditflow.app

import android.app.Application
import com.auditflow.app.data.local.AuditFlowPreferences
import com.auditflow.app.data.repository.ProjectIngestionRepositoryImpl
import com.auditflow.app.data.repository.ProjectStateRepositoryImpl
import com.auditflow.app.data.repository.SettingsRepositoryImpl
import com.auditflow.app.domain.repository.ProjectIngestionRepository
import com.auditflow.app.domain.repository.ProjectStateRepository
import com.auditflow.app.domain.repository.SettingsRepository

/**
 * Real Android Application entry point for AuditFlow.
 *
 * Provides application-scoped preferences and repositories with lazy, null-safe initialization.
 */
class AuditFlowApplication : Application() {

    val preferences: AuditFlowPreferences by lazy {
        AuditFlowPreferences(applicationContext)
    }

    val projectStateRepository: ProjectStateRepository by lazy {
        ProjectStateRepositoryImpl(preferences)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(preferences)
    }

    val projectIngestionRepository: ProjectIngestionRepository by lazy {
        ProjectIngestionRepositoryImpl(applicationContext)
    }

    init {
        _instance = this
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
    }

    companion object {
        private var _instance: AuditFlowApplication? = null

        val instanceOrNull: AuditFlowApplication?
            get() = _instance

        var instance: AuditFlowApplication
            get() = _instance ?: throw IllegalStateException("AuditFlowApplication instance is not ready yet.")
            private set(value) {
                _instance = value
            }
    }
}
