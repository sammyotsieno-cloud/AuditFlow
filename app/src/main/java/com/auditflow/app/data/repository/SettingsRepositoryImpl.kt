package com.auditflow.app.data.repository

import com.auditflow.app.data.local.AuditFlowPreferences
import com.auditflow.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepositoryImpl(
    private val preferences: AuditFlowPreferences
) : SettingsRepository {

    private val _darkMode = MutableStateFlow(preferences.getDarkMode())
    override val darkModeEnabled: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _selectedInputMode = MutableStateFlow(preferences.getSelectedInputMode())
    override val selectedInputMode: StateFlow<String?> = _selectedInputMode.asStateFlow()

    override suspend fun setDarkMode(enabled: Boolean) {
        preferences.setDarkMode(enabled)
        _darkMode.value = enabled
    }

    override suspend fun setSelectedInputMode(mode: String?) {
        preferences.setSelectedInputMode(mode)
        _selectedInputMode.value = mode
    }
}
