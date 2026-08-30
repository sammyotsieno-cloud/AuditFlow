package com.auditflow.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for genuine user preferences.
 */
interface SettingsRepository {
    val darkModeEnabled: StateFlow<Boolean>
    val selectedInputMode: StateFlow<String?>

    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setSelectedInputMode(mode: String?)
}
