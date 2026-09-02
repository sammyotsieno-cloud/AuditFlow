package com.auditflow.app.presentation

import android.content.Context
import android.net.Uri
import com.auditflow.app.domain.model.ProjectMetadata
import com.auditflow.app.domain.model.ProjectSourceKind
import com.auditflow.app.domain.model.ProjectState
import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.repository.ProjectIngestionRepository
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
    private lateinit var fakeProjectIngestionRepository: FakeProjectIngestionRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProjectStateRepository = FakeProjectStateRepository()
        fakeSettingsRepository = FakeSettingsRepository()
        fakeProjectIngestionRepository = FakeProjectIngestionRepository()
        viewModel = HomeViewModel(
            projectStateRepository = fakeProjectStateRepository,
            settingsRepository = fakeSettingsRepository,
            projectIngestionRepository = fakeProjectIngestionRepository
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
    fun ingestGitHubRepository_success_transitionsToProjectLoaded() = runTest {
        val sampleNodes = listOf(
            SourceFileNode(
                relativePath = "README.md",
                name = "README.md",
                extension = "md",
                sizeBytes = 512L,
                isDirectory = false
            )
        )
        fakeProjectIngestionRepository.githubResult = Result.success(
            Pair(
                ProjectMetadata(
                    name = "AuditFlow",
                    pathOrUri = "https://github.com/auditflow/app",
                    sourceKind = ProjectSourceKind.GITHUB_REPOSITORY,
                    fileCount = 1,
                    totalSizeBytes = 512L
                ),
                sampleNodes
            )
        )

        viewModel.ingestGitHubRepository("auditflow/app")

        val state = viewModel.uiState.value.projectState
        assertTrue(state is ProjectState.ProjectLoaded)
        val loaded = state as ProjectState.ProjectLoaded
        assertEquals("AuditFlow", loaded.metadata.name)
        assertEquals(1, loaded.files.size)
        assertEquals("README.md", loaded.files[0].relativePath)
    }

    @Test
    fun ingestGitHubRepository_failure_transitionsToErrorState() = runTest {
        fakeProjectIngestionRepository.githubResult = Result.failure(
            IllegalArgumentException("Repository not found (HTTP 404)")
        )

        viewModel.ingestGitHubRepository("invalid/nonexistent")

        val state = viewModel.uiState.value.projectState
        assertTrue(state is ProjectState.Error)
        val error = state as ProjectState.Error
        assertEquals("Repository not found (HTTP 404)", error.message)
    }

    @Test
    fun ingestLocalArtifact_success_transitionsToProjectLoaded() = runTest {
        val sampleNodes = listOf(
            SourceFileNode(
                relativePath = "AndroidManifest.xml",
                name = "AndroidManifest.xml",
                extension = "xml",
                sizeBytes = 1024L,
                isDirectory = false
            ),
            SourceFileNode(
                relativePath = "classes.dex",
                name = "classes.dex",
                extension = "dex",
                sizeBytes = 2048L,
                isDirectory = false
            )
        )
        fakeProjectIngestionRepository.localFileResult = Result.success(
            Pair(
                ProjectMetadata(
                    name = "app.apk",
                    pathOrUri = "content://sample/app.apk",
                    sourceKind = ProjectSourceKind.LOCAL_FILE,
                    fileCount = 2,
                    totalSizeBytes = 3072L
                ),
                sampleNodes
            )
        )

        val fakeContext = FakeContext()
        val fakeUri = FakeUri("content://sample/app.apk")

        viewModel.ingestLocalArtifact(fakeUri, fakeContext)

        val state = viewModel.uiState.value.projectState
        assertTrue(state is ProjectState.ProjectLoaded)
        val loaded = state as ProjectState.ProjectLoaded
        assertEquals("app.apk", loaded.metadata.name)
        assertEquals(2, loaded.files.size)
    }

    @Test
    fun onResetStateToEmpty_transitionsBackToNoProject() = runTest {
        fakeProjectStateRepository.setError("Some error")
        assertTrue(viewModel.uiState.value.projectState is ProjectState.Error)

        viewModel.onResetStateToEmpty()
        assertEquals(ProjectState.NoProject, viewModel.uiState.value.projectState)
    }

    private class FakeContext : android.content.ContextWrapper(null)

    private class FakeUri(private val uriString: String) : Uri() {
        override fun isHierarchical(): Boolean = false
        override fun isRelative(): Boolean = false
        override fun getScheme(): String? = null
        override fun getSchemeSpecificPart(): String? = null
        override fun getEncodedSchemeSpecificPart(): String? = null
        override fun getAuthority(): String? = null
        override fun getEncodedAuthority(): String? = null
        override fun getUserInfo(): String? = null
        override fun getEncodedUserInfo(): String? = null
        override fun getHost(): String? = null
        override fun getPort(): Int = -1
        override fun getPath(): String? = null
        override fun getEncodedPath(): String? = null
        override fun getQuery(): String? = null
        override fun getEncodedQuery(): String? = null
        override fun getFragment(): String? = null
        override fun getEncodedFragment(): String? = null
        override fun getPathSegments(): List<String> = emptyList()
        override fun getLastPathSegment(): String? = null
        override fun buildUpon(): Builder? = null
        override fun toString(): String = uriString
        override fun compareTo(other: Uri?): Int = 0
    }

    // In-memory test fakes for deterministic unit verification
    private class FakeProjectStateRepository : ProjectStateRepository {
        private val _state = MutableStateFlow<ProjectState>(ProjectState.NoProject)
        override val projectState: StateFlow<ProjectState> = _state.asStateFlow()

        override suspend fun setNoProject() {
            _state.value = ProjectState.NoProject
        }

        override suspend fun setProjectLoading(source: String, progress: Int, statusMessage: String) {
            _state.value = ProjectState.ProjectLoading(source, progress, statusMessage)
        }

        override suspend fun setProjectLoaded(metadata: ProjectMetadata, files: List<SourceFileNode>) {
            _state.value = ProjectState.ProjectLoaded(metadata, files)
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

    private class FakeProjectIngestionRepository : ProjectIngestionRepository {
        var localResult: Result<Pair<ProjectMetadata, List<SourceFileNode>>> = Result.failure(NotImplementedError())
        var localFileResult: Result<Pair<ProjectMetadata, List<SourceFileNode>>> = Result.failure(NotImplementedError())
        var githubResult: Result<Pair<ProjectMetadata, List<SourceFileNode>>> = Result.failure(NotImplementedError())
        var fileContentResult: Result<String> = Result.failure(NotImplementedError())

        override suspend fun ingestLocalDirectory(
            treeUri: Uri,
            context: Context,
            onProgress: (Int, String) -> Unit
        ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = localResult

        override suspend fun ingestLocalFile(
            fileUri: Uri,
            context: Context,
            onProgress: (Int, String) -> Unit
        ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = localFileResult

        override suspend fun ingestGitHubRepository(
            repoUrlOrSlug: String,
            branch: String?,
            onProgress: (Int, String) -> Unit
        ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> = githubResult

        override suspend fun readFileContent(
            projectMetadata: ProjectMetadata,
            relativePath: String,
            context: Context?
        ): Result<String> = fileContentResult
    }
}

