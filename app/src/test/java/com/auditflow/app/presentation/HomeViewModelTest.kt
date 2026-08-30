package com.auditflow.app.presentation

import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.repository.ProjectStateRepository
import com.auditflow.app.domain.repository.SettingsRepository
import com.auditflow.app.presentation.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeProjectStateRepository: FakeProjectStateRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProjectStateRepository = FakeProjectStateRepository()
        fakeSettingsRepository = FakeSettingsRepository()
        viewModel = HomeViewModel(
            projectStateRepository = fakeProjectStateRepository,
            settingsRepository = fakeSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialHomeState_isGenuinelyEmpty() = runTest {
        assertEquals(ProjectState.NoProject, viewModel.uiState.value.projectState)
        assertFalse(viewModel.uiState.value.isNotImplementedDialogOpen)
        assertEquals("", viewModel.uiState.value.pendingFeatureName)
    }

    @Test
    fun onLocalProjectClicked_triggersNotImplementedDialog() = runTest {
        viewModel.onLocalProjectClicked()
        assertTrue(viewModel.uiState.value.isNotImplementedDialogOpen)
        assertEquals("Local Project Ingestion", viewModel.uiState.value.pendingFeatureName)
    }

    @Test
    fun onGitHubRepositoryClicked_triggersNotImplementedDialog() = runTest {
        viewModel.onGitHubRepositoryClicked()
        assertTrue(viewModel.uiState.value.isNotImplementedDialogOpen)
        assertEquals("GitHub Repository Ingestion", viewModel.uiState.value.pendingFeatureName)
    }

    @Test
    fun onDismissNotImplementedDialog_resetsDialogState() = runTest {
        viewModel.onLocalProjectClicked()
        assertTrue(viewModel.uiState.value.isNotImplementedDialogOpen)

        viewModel.onDismissNotImplementedDialog()
        assertFalse(viewModel.uiState.value.isNotImplementedDialogOpen)
        assertEquals("", viewModel.uiState.value.pendingFeatureName)
    }

    // In-memory test fakes for deterministic unit verification
    private class FakeProjectStateRepository : ProjectStateRepository {
        private val _state = MutableStateFlow<ProjectState>(ProjectState.NoProject)
        override val projectState: StateFlow<ProjectState> = _state.asStateFlow()

        override suspend fun setNoProject() {
            _state.value = ProjectState.NoProject
        }

        override suspend fun setProjectLoading(source: String, progress: Int) {
            _state.value = ProjectState.ProjectLoading(source, progress)
        }

        override suspend fun setProjectLoaded(metadata: ProjectMetadata) {
            _state.value = ProjectState.ProjectLoaded(metadata)
        }

        override suspend fun setError(message: String, cause: Throwable?) {
            _state.value = ProjectState.Error(message, cause)
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _darkMode = MutableStateFlow(false)
        override val darkModeEnabled: StateFlow<Boolean> = _darkMode.asStateFlow()

        private val _selectedMode = MutableStateFlow<String?>(null)
        override val selectedInputMode: StateFlow<String?> = _selectedMode.asStateFlow()

        override suspend fun setDarkMode(enabled: Boolean) {
            _darkMode.value = enabled
        }

        override suspend fun setSelectedInputMode(mode: String?) {
            _selectedMode.value = mode
        }
    }
}
