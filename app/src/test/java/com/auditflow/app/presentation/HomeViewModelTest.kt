package com.auditflow.app.presentation

import com.auditflow.app.domain.model.ContentAvailabilityState
import com.auditflow.app.domain.model.ParsingStatus
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
        assertEquals(
            ProjectState.NoProject,
            viewModel.uiState.value.projectState
        )

        assertFalse(
            viewModel.uiState.value.isNotImplementedDialogOpen
        )

        assertEquals(
            "",
            viewModel.uiState.value.pendingFeatureName
        )
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

        fakeProjectIngestionRepository.githubResult =
            Result.success(
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

        assertTrue(
            state is ProjectState.ProjectLoaded
        )

        val loaded =
            state as ProjectState.ProjectLoaded

        assertEquals(
            "AuditFlow",
            loaded.metadata.name
        )

        assertEquals(
            1,
            loaded.files.size
        )

        assertEquals(
            "README.md",
            loaded.files[0].relativePath
        )
    }

    @Test
    fun ingestGitHubRepository_failure_transitionsToErrorState() = runTest {
        fakeProjectIngestionRepository.githubResult =
            Result.failure(
                IllegalArgumentException(
                    "Repository not found (HTTP 404)"
                )
            )

        viewModel.ingestGitHubRepository(
            "invalid/nonexistent"
        )

        val state =
            viewModel.uiState.value.projectState

        assertTrue(
            state is ProjectState.Error
        )

        val error =
            state as ProjectState.Error

        assertEquals(
            "Repository not found (HTTP 404)",
            error.message
        )
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

        fakeProjectIngestionRepository.localFileResult =
            Result.success(
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

        viewModel.ingestLocalArtifact(
            "content://sample/app.apk"
        )

        val state =
            viewModel.uiState.value.projectState

        assertTrue(
            state is ProjectState.ProjectLoaded
        )

        val loaded =
            state as ProjectState.ProjectLoaded

        assertEquals(
            "app.apk",
            loaded.metadata.name
        )

        assertEquals(
            2,
            loaded.files.size
        )
    }

    @Test
    fun onResetStateToEmpty_transitionsBackToNoProject() = runTest {
        fakeProjectStateRepository.setError(
            "Some error"
        )

        assertTrue(
            viewModel.uiState.value.projectState is ProjectState.Error
        )

        viewModel.onResetStateToEmpty()

        assertEquals(
            ProjectState.NoProject,
            viewModel.uiState.value.projectState
        )
    }

    @Test
    fun inspectFile_withLoadedProjectAndValidContent_returnsInspectionResult() = runTest {
        val node = SourceFileNode(
            relativePath =
                "app/src/main/MainActivity.kt",
            name = "MainActivity.kt",
            extension = "kt",
            sizeBytes = 128L,
            isDirectory = false
        )

        fakeProjectIngestionRepository.githubResult =
            Result.success(
                Pair(
                    ProjectMetadata(
                        name = "AuditFlow",
                        pathOrUri =
                            "https://github.com/auditflow/app",
                        sourceKind =
                            ProjectSourceKind.GITHUB_REPOSITORY,
                        fileCount = 1,
                        totalSizeBytes = 128L
                    ),
                    listOf(node)
                )
            )

        viewModel.ingestGitHubRepository(
            "auditflow/app"
        )

        val kotlinSource = """
            package com.auditflow.app

            import android.os.Bundle

            class MainActivity {
                fun onCreate() {}
            }
        """.trimIndent()

        fakeProjectIngestionRepository.fileContentResult =
            Result.success(kotlinSource)

        val result =
            viewModel.inspectFile(node)

        assertTrue(
            result.isSuccess
        )

        val inspection =
            result.getOrNull()!!

        assertEquals(
            "app/src/main/MainActivity.kt",
            inspection.relativePath
        )

        assertEquals(
            ParsingStatus.PARSED_SUCCESS,
            inspection.parsingStatus
        )

        assertEquals(
            "com.auditflow.app",
            inspection.declaredPackage
        )

        assertEquals(
            1,
            inspection.imports.size
        )

        assertEquals(
            "android.os.Bundle",
            inspection.imports[0].importPath
        )

        assertEquals(
            "Bundle",
            inspection.imports[0].importedSymbolName
        )

        assertEquals(
            1,
            inspection.topLevelSymbols.size
        )

        assertEquals(
            "MainActivity",
            inspection.topLevelSymbols[0].name
        )

        assertEquals(
            ContentAvailabilityState.AVAILABLE,
            inspection.contentAvailability
        )

        assertEquals(
            1,
            viewModel.fileInspections.value.size
        )

        assertEquals(
            inspection,
            viewModel.fileInspections.value[node.relativePath]
        )
    }

    @Test
    fun inspectFile_withoutLoadedProject_returnsFailure() = runTest {
        val node = SourceFileNode(
            relativePath = "README.md",
            name = "README.md",
            extension = "md",
            sizeBytes = 64L,
            isDirectory = false
        )

        val result =
            viewModel.inspectFile(node)

        assertTrue(
            result.isFailure
        )

        assertEquals(
            0,
            viewModel.fileInspections.value.size
        )
    }

    @Test
    fun inspectFile_withDirectoryNode_returnsFailure() = runTest {
        val dirNode = SourceFileNode(
            relativePath = "app/src",
            name = "src",
            extension = "",
            sizeBytes = 0L,
            isDirectory = true
        )

        fakeProjectIngestionRepository.githubResult =
            Result.success(
                Pair(
                    ProjectMetadata(
                        name = "AuditFlow",
                        pathOrUri =
                            "https://github.com/auditflow/app",
                        sourceKind =
                            ProjectSourceKind.GITHUB_REPOSITORY,
                        fileCount = 1,
                        totalSizeBytes = 0L
                    ),
                    listOf(dirNode)
                )
            )

        viewModel.ingestGitHubRepository(
            "auditflow/app"
        )

        val result =
            viewModel.inspectFile(dirNode)

        assertTrue(
            result.isFailure
        )

        assertTrue(
            result.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun inspectFile_withFailedContentRetrieval_returnsFailureAndRecordsUnavailableState() =
        runTest {
            val node = SourceFileNode(
                relativePath = "Missing.kt",
                name = "Missing.kt",
                extension = "kt",
                sizeBytes = 100L,
                isDirectory = false
            )

            fakeProjectIngestionRepository.githubResult =
                Result.success(
                    Pair(
                        ProjectMetadata(
                            name = "AuditFlow",
                            pathOrUri =
                                "https://github.com/auditflow/app",
                            sourceKind =
                                ProjectSourceKind.GITHUB_REPOSITORY,
                            fileCount = 1,
                            totalSizeBytes = 100L
                        ),
                        listOf(node)
                    )
                )

            viewModel.ingestGitHubRepository(
                "auditflow/app"
            )

            fakeProjectIngestionRepository.fileContentResult =
                Result.failure(
                    java.io.IOException(
                        "Network timeout"
                    )
                )

            val result =
                viewModel.inspectFile(node)

            assertTrue(
                result.isFailure
            )

            val recorded =
                viewModel.fileInspections.value[node.relativePath]

            assertTrue(
                recorded != null
            )

            assertEquals(
                ContentAvailabilityState.UNAVAILABLE_NOT_FETCHED,
                recorded?.contentAvailability
            )

            assertEquals(
                ParsingStatus.UNAVAILABLE_CONTENT,
                recorded?.parsingStatus
            )
        }

    @Test
    fun onResetStateToEmpty_clearsInspectionsAndTransitionsBackToNoProject() =
        runTest {
            val node = SourceFileNode(
                relativePath = "Sample.kt",
                name = "Sample.kt",
                extension = "kt",
                sizeBytes = 50L,
                isDirectory = false
            )

            fakeProjectIngestionRepository.githubResult =
                Result.success(
                    Pair(
                        ProjectMetadata(
                            name = "AuditFlow",
                            pathOrUri =
                                "https://github.com/auditflow/app",
                            sourceKind =
                                ProjectSourceKind.GITHUB_REPOSITORY,
                            fileCount = 1,
                            totalSizeBytes = 50L
                        ),
                        listOf(node)
                    )
                )

            viewModel.ingestGitHubRepository(
                "auditflow/app"
            )

            fakeProjectIngestionRepository.fileContentResult =
                Result.success(
                    "val x = 1"
                )

            viewModel.inspectFile(node)

            assertEquals(
                1,
                viewModel.fileInspections.value.size
            )

            viewModel.onResetStateToEmpty()

            assertEquals(
                ProjectState.NoProject,
                viewModel.uiState.value.projectState
            )

            assertEquals(
                0,
                viewModel.fileInspections.value.size
            )
        }

    private class FakeProjectStateRepository :
        ProjectStateRepository {

        private val _state =
            MutableStateFlow<ProjectState>(
                ProjectState.NoProject
            )

        override val projectState:
            StateFlow<ProjectState> =
            _state.asStateFlow()

        override suspend fun setNoProject() {
            _state.value =
                ProjectState.NoProject
        }

        override suspend fun setProjectLoading(
            source: String,
            progress: Int,
            statusMessage: String
        ) {
            _state.value =
                ProjectState.ProjectLoading(
                    source,
                    progress,
                    statusMessage
                )
        }

        override suspend fun setProjectLoaded(
            metadata: ProjectMetadata,
            files: List<SourceFileNode>
        ) {
            _state.value =
                ProjectState.ProjectLoaded(
                    metadata,
                    files
                )
        }

        override suspend fun setError(
            message: String,
            cause: Throwable?
        ) {
            _state.value =
                ProjectState.Error(
                    message,
                    cause
                )
        }
    }

    private class FakeSettingsRepository :
        SettingsRepository {

        private val _darkMode =
            MutableStateFlow(false)

        override val darkModeEnabled:
            StateFlow<Boolean> =
            _darkMode.asStateFlow()

        private val _selectedMode =
            MutableStateFlow<String?>(null)

        override val selectedInputMode:
            StateFlow<String?> =
            _selectedMode.asStateFlow()

        override suspend fun setDarkMode(
            enabled: Boolean
        ) {
            _darkMode.value = enabled
        }

        override suspend fun setSelectedInputMode(
            mode: String?
        ) {
            _selectedMode.value = mode
        }
    }

    private class FakeProjectIngestionRepository :
        ProjectIngestionRepository {

        var localResult:
            Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
            Result.failure(
                NotImplementedError()
            )

        var localFileResult:
            Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
            Result.failure(
                NotImplementedError()
            )

        var githubResult:
            Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
            Result.failure(
                NotImplementedError()
            )

        var snapshotResult:
            Result<RepositorySnapshot> =
            Result.failure(
                NotImplementedError()
            )

        var fileContentResult:
            Result<String> =
            Result.failure(
                NotImplementedError()
            )

        override suspend fun ingestLocalDirectory(
            treeUriString: String,
            onProgress: (Int, String) -> Unit
        ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
            localResult

        override suspend fun ingestLocalFile(
            fileUriString: String,
            onProgress: (Int, String) -> Unit
        ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
            localFileResult

        override suspend fun ingestGitHubRepository(
            repoUrlOrSlug: String,
            branch: String?,
            onProgress: (Int, String) -> Unit
        ): Result<Pair<ProjectMetadata, List<SourceFileNode>>> =
            githubResult

        override suspend fun acquireRepositorySnapshot(
            repoUrlOrSlug: String,
            branch: String?,
            onProgress: (Int, String) -> Unit
        ): Result<RepositorySnapshot> =
            snapshotResult

        override suspend fun readFileContent(
            projectMetadata: ProjectMetadata,
            relativePath: String
        ): Result<String> =
            fileContentResult
    }
}
