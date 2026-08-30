package com.auditflow.app.data

import com.auditflow.app.data.local.AuditFlowPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditFlowPreferencesTest {

    @Test
    fun preferenceConstants_areDefinedTruthfully() {
        assertEquals("NO_PROJECT", AuditFlowPreferences.STATE_KIND_NO_PROJECT)
        assertEquals("PROJECT_LOADED", AuditFlowPreferences.STATE_KIND_LOADED)
        assertEquals("project_state_kind", AuditFlowPreferences.KEY_PROJECT_STATE_KIND)
        assertEquals("dark_mode", AuditFlowPreferences.KEY_DARK_MODE)
        assertEquals("selected_input_mode", AuditFlowPreferences.KEY_SELECTED_INPUT_MODE)
    }
}
