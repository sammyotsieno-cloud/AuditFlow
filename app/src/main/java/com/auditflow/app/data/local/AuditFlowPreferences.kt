package com.auditflow.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Clean local persistence foundation for genuine configuration and persistent state.
 *
 * Rule:
 * If no project has been provided, persist NO_PROJECT.
 * Never fabricate or persist synthetic project data.
 */
class AuditFlowPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getPersistedStateKind(): String {
        return prefs.getString(KEY_PROJECT_STATE_KIND, STATE_KIND_NO_PROJECT) ?: STATE_KIND_NO_PROJECT
    }

    fun setPersistedStateKind(kind: String) {
        prefs.edit().putString(KEY_PROJECT_STATE_KIND, kind).apply()
    }

    fun getDarkMode(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun getSelectedInputMode(): String? {
        return prefs.getString(KEY_SELECTED_INPUT_MODE, null)
    }

    fun setSelectedInputMode(mode: String?) {
        prefs.edit().putString(KEY_SELECTED_INPUT_MODE, mode).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auditflow_prefs"
        const val KEY_PROJECT_STATE_KIND = "project_state_kind"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_SELECTED_INPUT_MODE = "selected_input_mode"

        const val STATE_KIND_NO_PROJECT = "NO_PROJECT"
        const val STATE_KIND_LOADED = "PROJECT_LOADED"
    }
}
