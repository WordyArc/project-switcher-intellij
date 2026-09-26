package dev.ashenarx.project.switcher.intellij.ui

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

@TestApplication
class ProjectSwitcherModelTest {

    private companion object {
        val project = projectFixture(tempPathFixture(subdirName = "ModelTest"), openAfterCreation = true)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val model = ProjectSwitcherModel(currentProject = null, coroutineScope = scope)

    // Each row differs from the others under some rule, so no rule can pass a test by accident.
    private val firstRow = open("first")
    private val currentRow = open("current", isCurrent = true)
    private val middle = recent("middle")
    private val last = recent("last")
    private val items = ProjectList(open = listOf(firstRow, currentRow), recent = listOf(middle, last))

    @AfterEach
    fun cancelScope() {
        scope.cancel("test finished")
    }

    @Test
    fun `the top match outranks the current project`() = timeoutRunBlocking {
        val model = modelOver(items)

        model.query = "last"

        assertEquals(last.id, model.selectedId)
    }

    @Test
    fun `without a top match the current project is selected, not the first row`() = timeoutRunBlocking {
        val model = modelOver(items)

        assertEquals(currentRow.id, model.selectedId)
    }

    @Test
    fun `without a current project the first row is selected`() = timeoutRunBlocking {
        val model = modelOver(ProjectList(open = listOf(firstRow), recent = listOf(middle)))

        assertEquals(firstRow.id, model.selectedId)
    }

    @Test
    fun `an empty list leaves nothing selected`() = timeoutRunBlocking {
        val model = modelOver(ProjectList.EMPTY)

        assertNull(model.selectedId)
    }

    @Test
    fun `highlights follow the query and vanish with it`() = timeoutRunBlocking {
        val model = modelOver(items)

        model.query = "first"

        val name = checkNotNull(model.highlights[firstRow.id]).name
        assertEquals("first", name.joinToString("") { firstRow.displayName.substring(it) })

        model.query = ""

        assertEquals(emptyMap<String, Any>(), model.highlights)
    }

    @Test
    fun `a top match still wins after the user moved the selection by hand`() = timeoutRunBlocking {
        val model = modelOver(items)
        model.select(last.id)

        model.query = "first"

        assertEquals(firstRow.id, model.selectedId, "a new ranking outranks the selection made under the old one")
    }

    @Test
    fun `a selection made by hand survives a refresh that only fills in branch names`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.select(last.id)

        actions.publish(items.withBranches())
        model.refresh()

        awaitRows(model) { rows -> rows.all.all { it.branch != null } }
        assertEquals(last.id, model.selectedId)
    }

    @Test
    fun `after removing a project the selection moves to its neighbour`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.select(middle.id)

        model.closeSelected()

        assertEquals(listOf(middle.path), actions.forgotten)
        awaitRows(model) { rows -> rows.all.none { it.id == middle.id } }
        assertEquals(last.id, model.selectedId, "expected the row below the one that was removed")
    }

    @Test
    fun `a remembered selection that the reload dropped falls back to the default`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.select(middle.id)

        model.closeSelected()
        awaitRows(model) { rows -> rows.all.none { it.id == middle.id } }

        actions.forget(last.path)
        model.refresh()

        awaitRows(model) { rows -> rows.recent.isEmpty() }
        assertEquals(currentRow.id, model.selectedId, "expected the default, not a dangling id")
    }

    @Test
    fun `closing the current project is left to the caller when another one is open`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.select(currentRow.id)

        assertNull(model.closeSelected(), "the popup must not offer to close the only window in reach")
        assertEquals(emptyList<ProjectItem.Open>(), actions.closed)
    }

    @Test
    fun `closing the last open project is handed back to the caller`() = timeoutRunBlocking {
        val model = modelOver(ProjectList(open = listOf(currentRow), recent = listOf(middle)))

        assertEquals(currentRow, model.closeSelected())
    }

    @Test
    fun `load reaches READY and publishes the open projects`() = timeoutRunBlocking {
        assertEquals(ProjectLoadState.LOADING, model.loadState)

        model.load()

        assertEquals(ProjectLoadState.READY, awaitLoaded())
        assertTrue(
            model.projects.open.any { it.displayName == "ModelTest" },
            "expected the open project in the list, got ${model.projects.open.map(ProjectItem::displayName)}",
        )
    }

    @Test
    fun `refresh waits for the first load instead of racing it`() = timeoutRunBlocking {
        assertEquals(ProjectLoadState.LOADING, model.loadState)

        model.refresh()
        delay(200)

        assertEquals(ProjectList.EMPTY, model.projects, "a refresh must not publish over an unfinished load")
        assertEquals(ProjectLoadState.LOADING, model.loadState)
    }

    @Test
    fun `refresh republishes the list without a loading placeholder or an icon reset`() = timeoutRunBlocking {
        model.load()
        assertEquals(ProjectLoadState.READY, awaitLoaded())

        // No project owns this path, so only a wholesale reset can remove it.
        val probe = "/not/a/project"
        model.icons[probe] = StubIcon

        model.refresh()

        // Polled, not awaited, to catch a refresh that breaks an invariant midway.
        repeat(50) {
            assertEquals(ProjectLoadState.READY, model.loadState, "a refresh must not show the loading placeholder")
            assertSame(StubIcon, model.icons[probe], "a refresh must keep the icons it already has")
            delay(20)
        }

        assertTrue(model.projects.open.any { it.displayName == "ModelTest" })
    }

    private suspend fun modelOver(projects: ProjectList): ProjectSwitcherModel =
        modelOver(FakeProjectActions(projects))

    private suspend fun modelOver(actions: FakeProjectActions): ProjectSwitcherModel {
        val model = ProjectSwitcherModel(currentProject = null, coroutineScope = scope, actions = actions)
        model.load()
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
        return model
    }

    private suspend fun awaitRows(model: ProjectSwitcherModel, matches: (ProjectList) -> Boolean) {
        while (!matches(model.rows)) delay(20)
    }

    private suspend fun awaitLoaded(): ProjectLoadState? = withTimeoutOrNull(60.seconds) {
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
        model.loadState
    }
}

private fun ProjectList.withBranches(): ProjectList = ProjectList(
    open = open.map { it.copy(branch = "main") },
    recent = recent.map { it.copy(branch = "main") },
)

private object StubIcon : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit
    override fun getIconWidth(): Int = 16
    override fun getIconHeight(): Int = 16
}
