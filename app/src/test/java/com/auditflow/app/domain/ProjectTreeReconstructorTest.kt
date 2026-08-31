package com.auditflow.app.domain

import com.auditflow.app.domain.model.SourceFileNode
import com.auditflow.app.domain.util.ProjectTreeReconstructor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTreeReconstructorTest {

    @Test
    fun singleRootFile_reconstructsRootAndOneLeaf() {
        val files = listOf(
            SourceFileNode(
                relativePath = "README.md",
                name = "README.md",
                extension = "md",
                sizeBytes = 100L,
                isDirectory = false
            )
        )

        val root = ProjectTreeReconstructor.reconstruct("MyProject", files)
        assertEquals("MyProject", root.name)
        assertTrue(root.isDirectory)
        assertEquals(1, root.children.size)

        val readme = root.children[0]
        assertEquals("README.md", readme.name)
        assertFalse(readme.isDirectory)

        val lines = ProjectTreeReconstructor.generateTreeLines(root)
        assertEquals(2, lines.size)
        assertEquals("MyProject/", lines[0].displayName)
        assertEquals("README.md", lines[1].displayName)
        assertEquals("└── ", lines[1].prefix)
    }

    @Test
    fun multipleRootFiles_orderedAlphabetically() {
        val files = listOf(
            SourceFileNode("styles.css", "styles.css", "css", 250L, false),
            SourceFileNode("index.html", "index.html", "html", 350L, false),
            SourceFileNode("README.md", "README.md", "md", 780L, false)
        )

        val root = ProjectTreeReconstructor.reconstruct("Website", files)
        assertEquals(3, root.children.size)
        assertEquals("index.html", root.children[0].name)
        assertEquals("README.md", root.children[1].name)
        assertEquals("styles.css", root.children[2].name)

        val lines = ProjectTreeReconstructor.generateTreeLines(root)
        assertEquals(4, lines.size)
        assertEquals("├── ", lines[1].prefix)
        assertEquals("index.html", lines[1].displayName)
        assertEquals("├── ", lines[2].prefix)
        assertEquals("README.md", lines[2].displayName)
        assertEquals("└── ", lines[3].prefix)
        assertEquals("styles.css", lines[3].displayName)
    }

    @Test
    fun oneDirectoryContainingFiles_directoriesFirst_reconstructedCorrectly() {
        val files = listOf(
            SourceFileNode("README.md", "README.md", "md", 100L, false),
            SourceFileNode("docs/guide.md", "guide.md", "md", 500L, false),
            SourceFileNode("docs/api.md", "api.md", "md", 600L, false)
        )

        val root = ProjectTreeReconstructor.reconstruct("Project", files)
        assertEquals(2, root.children.size)

        // Directory 'docs' should come before file 'README.md'
        val docs = root.children[0]
        assertEquals("docs", docs.name)
        assertTrue(docs.isDirectory)
        assertEquals(2, docs.children.size)
        assertEquals("api.md", docs.children[0].name)
        assertEquals("guide.md", docs.children[1].name)

        val readme = root.children[1]
        assertEquals("README.md", readme.name)
        assertFalse(readme.isDirectory)

        val lines = ProjectTreeReconstructor.generateTreeLines(root)
        // Root + docs/ + docs/api.md + docs/guide.md + README.md
        assertEquals(5, lines.size)
        assertEquals("├── ", lines[1].prefix)
        assertEquals("docs/", lines[1].displayName)
        assertEquals("│   ├── ", lines[2].prefix)
        assertEquals("api.md", lines[2].displayName)
        assertEquals("│   └── ", lines[3].prefix)
        assertEquals("guide.md", lines[3].displayName)
        assertEquals("└── ", lines[4].prefix)
        assertEquals("README.md", lines[4].displayName)
    }

    @Test
    fun deepNestedDirectories_mergesSharedParents() {
        val files = listOf(
            SourceFileNode("app/src/main/java/com/auditflow/app/MainActivity.kt", "MainActivity.kt", "kt", 1200L, false),
            SourceFileNode("app/src/main/java/com/auditflow/app/HomeScreen.kt", "HomeScreen.kt", "kt", 950L, false),
            SourceFileNode("app/src/main/AndroidManifest.xml", "AndroidManifest.xml", "xml", 400L, false),
            SourceFileNode("app/src/test/java/com/auditflow/app/MainActivityTest.kt", "MainActivityTest.kt", "kt", 800L, false),
            SourceFileNode("app/build.gradle.kts", "build.gradle.kts", "kts", 1500L, false),
            SourceFileNode("README.md", "README.md", "md", 300L, false)
        )

        val root = ProjectTreeReconstructor.reconstruct("AuditFlow", files)
        assertEquals(2, root.children.size)

        val appDir = root.children[0]
        assertEquals("app", appDir.name)
        assertTrue(appDir.isDirectory)

        // Inside app: directories first -> src/, then files -> build.gradle.kts
        assertEquals(2, appDir.children.size)
        assertEquals("src", appDir.children[0].name)
        assertTrue(appDir.children[0].isDirectory)
        assertEquals("build.gradle.kts", appDir.children[1].name)
        assertFalse(appDir.children[1].isDirectory)

        // Inside src: main/ and test/
        val srcDir = appDir.children[0]
        assertEquals(2, srcDir.children.size)
        assertEquals("main", srcDir.children[0].name)
        assertEquals("test", srcDir.children[1].name)

        // Inside main: java/ (directory) and AndroidManifest.xml (file)
        val mainDir = srcDir.children[0]
        assertEquals(2, mainDir.children.size)
        assertEquals("java", mainDir.children[0].name)
        assertEquals("AndroidManifest.xml", mainDir.children[1].name)

        // Inside main/java/com/auditflow/app: HomeScreen.kt and MainActivity.kt
        val appPkg = mainDir.children[0].children[0].children[0].children[0]
        assertEquals("app", appPkg.name)
        assertEquals(2, appPkg.children.size)
        assertEquals("HomeScreen.kt", appPkg.children[0].name)
        assertEquals("MainActivity.kt", appPkg.children[1].name)
    }

    @Test
    fun explicitAndImplicitDirectories_noDuplication() {
        val files = listOf(
            SourceFileNode("app/src", "src", "", 0L, isDirectory = true),
            SourceFileNode("app/src/main", "main", "", 0L, isDirectory = true),
            SourceFileNode("app/src/main/App.kt", "App.kt", "kt", 500L, isDirectory = false)
        )

        val root = ProjectTreeReconstructor.reconstruct("Project", files)
        assertEquals(1, root.children.size)
        val app = root.children[0]
        assertEquals("app", app.name)
        assertEquals(1, app.children.size)
        val src = app.children[0]
        assertEquals("src", src.name)
        assertEquals(1, src.children.size)
        val main = src.children[0]
        assertEquals("main", main.name)
        assertEquals(1, main.children.size)
        assertEquals("App.kt", main.children[0].name)
    }

    @Test
    fun continuationLines_renderedAccurately() {
        val files = listOf(
            SourceFileNode("app/build.gradle.kts", "build.gradle.kts", "kts", 100L, false),
            SourceFileNode("app/src/main/AndroidManifest.xml", "AndroidManifest.xml", "xml", 200L, false),
            SourceFileNode("app/src/test/ExampleTest.kt", "ExampleTest.kt", "kt", 300L, false),
            SourceFileNode("README.md", "README.md", "md", 50L, false)
        )

        val root = ProjectTreeReconstructor.reconstruct("PROJECT", files)
        val lines = ProjectTreeReconstructor.generateTreeLines(root)

        // Check canonical tree shape
        // PROJECT/
        // ├── app/
        // │   ├── src/
        // │   │   ├── main/
        // │   │   │   └── AndroidManifest.xml
        // │   │   └── test/
        // │   │       └── ExampleTest.kt
        // │   └── build.gradle.kts
        // └── README.md

        assertEquals("PROJECT/", lines[0].displayName)
        assertEquals("├── ", lines[1].prefix)
        assertEquals("app/", lines[1].displayName)

        val lastLine = lines.last()
        assertEquals("└── ", lastLine.prefix)
        assertEquals("README.md", lastLine.displayName)
    }

    @Test
    fun emptyInput_returnsSingleRootNode() {
        val root = ProjectTreeReconstructor.reconstruct("EmptyProject", emptyList())
        assertEquals("EmptyProject", root.name)
        assertTrue(root.isDirectory)
        assertTrue(root.children.isEmpty())

        val lines = ProjectTreeReconstructor.generateTreeLines(root)
        assertEquals(1, lines.size)
        assertEquals("EmptyProject/", lines[0].displayName)
    }

    @Test
    fun duplicatePaths_deduplicatedSafely() {
        val files = listOf(
            SourceFileNode("src/Main.kt", "Main.kt", "kt", 100L, false),
            SourceFileNode("src/Main.kt", "Main.kt", "kt", 100L, false),
            SourceFileNode("/src/Main.kt", "Main.kt", "kt", 100L, false)
        )

        val root = ProjectTreeReconstructor.reconstruct("Project", files)
        assertEquals(1, root.children.size)
        val src = root.children[0]
        assertEquals("src", src.name)
        assertEquals(1, src.children.size)
        assertEquals("Main.kt", src.children[0].name)
    }
}
