package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.vcs.RecentProjectsBranchesProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises [ProjectCatalog.collect] against real platform state: real open projects and real
 * [com.intellij.ide.ReopenProjectAction]s built by the platform's own provider. Stubbing the provider
 * instead would hide exactly the coupling this covers — path normalisation and recent ordering.
 */
@TestApplication
class ProjectCatalogTest {

    private companion object {
        // The directory name becomes the project name; the casing is what the sorting test needs.
        val zebra = projectFixture(tempPathFixture(subdirName = "Zebra"), openAfterCreation = true)
        val apple = projectFixture(tempPathFixture(subdirName = "apple"), openAfterCreation = true)

        val branchProviderEp =
            ExtensionPointName<RecentProjectsBranchesProvider>("com.intellij.recentProjectsBranchesProvider")
    }

    private val catalog get() = ProjectCatalog.getInstance()

    @BeforeEach
    @AfterEach
    fun clearRecents() {
        recentState().additionalInfo.clear()
    }

    @Test
    fun `open projects are listed and sorted by name, ignoring case`() = timeoutRunBlocking {
        val names = catalog.collect(currentProject = null).open.map { it.displayName }

        assertEquals(
            listOf("apple", "Zebra"),
            names.filter { it == "apple" || it == "Zebra" },
            "sorting on the raw string would put Zebra first",
        )
    }

    @Test
    fun `the project the popup was invoked from is the only one marked current`() = timeoutRunBlocking {
        val open = catalog.collect(currentProject = apple.get()).open

        assertEquals(listOf("apple"), open.filter { it.isCurrent }.map { it.displayName })
        assertTrue(open.any { it.displayName == "Zebra" && !it.isCurrent }, "expected Zebra listed but not current")
    }

    @Test
    fun `an open project carries the location hash the opener matches on`() = timeoutRunBlocking {
        val item = catalog.collect(currentProject = null).open.single { it.displayName == "apple" }

        assertEquals(apple.get().locationHash, item.locationHash)
        assertEquals("open:${apple.get().locationHash}", item.id)
    }

    @Test
    fun `a project that is both open and recent is listed once, as open`() = timeoutRunBlocking {
        // The platform lists open projects among the recent ones too; the popup must not show both.
        seedRecent(pathOf(apple.get()), name = "apple")

        val list = catalog.collect(currentProject = null)

        assertEquals(listOf("apple"), list.all.filter { it.displayName == "apple" }.map { it.displayName })
        assertTrue(list.open.any { it.displayName == "apple" }, "the open entry is the one that must survive")
        assertEquals(emptyList<String>(), list.recent.map { it.displayName }.filter { it == "apple" })
    }

    @Test
    fun `recent projects keep the platform's most-recently-used order`() = timeoutRunBlocking {
        val oldest = seedRecent(name = "oldest")
        val middle = seedRecent(name = "middle")
        val newest = seedRecent(name = "newest")

        val recent = catalog.collect(currentProject = null).recent

        assertEquals(listOf("newest", "middle", "oldest"), recent.map { it.displayName })
        assertEquals(listOf(newest, middle, oldest), recent.map { it.path })
    }

    @Test
    fun `a recent project is identified by its normalised path`() = timeoutRunBlocking {
        val path = seedRecent(name = "solo")

        val item = catalog.collect(currentProject = null).recent.single()

        assertEquals(path, item.path)
        assertEquals("recent:$path", item.id)
        assertEquals(false, item.path.contains('\\'), "paths must reach the UI system-independent")
    }

    @Test
    fun `the branch reported for a path reaches both sections`(@TestDisposable disposable: Disposable) =
        timeoutRunBlocking {
            val recentPath = seedRecent(name = "detached")
            val openPath = pathOf(apple.get())
            seedRecent(openPath, name = "apple")

            ExtensionTestUtil.maskExtensions(
                branchProviderEp,
                listOf(FakeBranches(mapOf(recentPath to "release/24.1", openPath to "main"))),
                disposable,
                fireEvents = false,
            )

            val list = catalog.collect(currentProject = null)

            assertEquals("main", list.open.single { it.displayName == "apple" }.branch)
            assertEquals("release/24.1", list.recent.single { it.displayName == "detached" }.branch)
        }

    @Test
    fun `a project with no branch information simply has none`() = timeoutRunBlocking {
        seedRecent(name = "plain")

        assertNull(catalog.collect(currentProject = null).recent.single().branch)
    }

    @Test
    fun `forgetting a project drops it from the next collect`() = timeoutRunBlocking {
        val kept = seedRecent(name = "kept")
        val dropped = seedRecent(name = "dropped")

        catalog.forget(dropped)

        val recent = catalog.collect(currentProject = null).recent

        assertEquals(listOf(kept), recent.map { it.path })
    }

    @Test
    fun `forgetting an open project does not remove it from the open section`() = timeoutRunBlocking {
        val openPath = pathOf(apple.get())
        seedRecent(openPath, name = "apple")

        catalog.forget(openPath)

        val list = catalog.collect(currentProject = null)

        assertTrue(list.open.any { it.displayName == "apple" }, "the project is still open, so it stays listed")
    }

    /** Seeds one recent entry. Oldest first: the platform hands recents back in reverse. */
    private fun seedRecent(path: String? = null, name: String): String {
        val resolved = path ?: "/tmp/project-switcher-test/$name"

        recentState().additionalInfo[resolved] = RecentProjectMetaInfo().also {
            // customProjectName resolves synchronously and, unlike displayName, leaves branches enabled.
            it.customProjectName = name
        }

        return resolved
    }

    private fun recentState() = RecentProjectsManagerBase.getInstanceEx().state

    private fun pathOf(project: Project): String =
        checkNotNull(project.basePath) { "an opened project must have a base path" }

    private class FakeBranches(private val branches: Map<String, String>) : RecentProjectsBranchesProvider {
        override fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String? = branches[projectPath]
    }
}
