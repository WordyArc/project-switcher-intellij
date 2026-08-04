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

    // Every row is distinct from the others under at least one rule, so that a test asserting on one
    // of them cannot be satisfied by a different rule accidentally returning the same row.
    private val firstRow = open("first")
    private val currentRow = open("current", isCurrent = true)
    private val middle = recent("middle")
    private val last = recent("last")
    private val items = listOf(firstRow, currentRow, middle, last)

    @AfterEach
    fun cancelScope() {
        scope.cancel("test finished")
    }

    @Test
    fun `the top match outranks the current project`() {
        model.resetSelection(items, preferred = last)

        assertEquals(last.id, model.selectedId)
    }

    @Test
    fun `without a top match the current project is selected, not the first row`() {
        model.resetSelection(items, preferred = null)

        assertEquals(currentRow.id, model.selectedId)
    }

    @Test
    fun `without a current project the first row is selected`() {
        model.resetSelection(listOf(firstRow, middle), preferred = null)

        assertEquals(firstRow.id, model.selectedId)
    }

    @Test
    fun `an empty list leaves nothing selected`() {
        model.resetSelection(items, preferred = null)
        model.resetSelection(emptyList(), preferred = null)

        assertNull(model.selectedId)
    }

    @Test
    fun `a top match still wins after the user moved the selection by hand`() {
        model.resetSelection(items, preferred = null)
        model.selectedId = last.id

        model.resetSelection(items, preferred = firstRow)

        assertEquals(firstRow.id, model.selectedId)
    }

    @Test
    fun `after removing a project the selection moves to its neighbour`() = timeoutRunBlocking {
        model.selectedId = middle.id

        model.delete(middle, items)
        model.resetSelection(listOf(firstRow, currentRow, last), preferred = null)

        // Without the pending selection this would fall back to the current project instead.
        assertEquals(last.id, model.selectedId, "expected the row below the one that was removed")
    }

    @Test
    fun `a pending selection that the reload removed falls back to the default`() = timeoutRunBlocking {
        model.selectedId = middle.id

        model.delete(middle, items)
        // The reload came back without `last` either, so the pending id no longer resolves.
        model.resetSelection(listOf(firstRow, currentRow), preferred = null)

        assertEquals(currentRow.id, model.selectedId, "expected the default, not a dangling id")
    }

    @Test
    fun `a pending selection is consumed once`() = timeoutRunBlocking {
        model.selectedId = middle.id
        val remaining = listOf(firstRow, currentRow, last)

        model.delete(middle, items)
        model.resetSelection(remaining, preferred = null)
        assertEquals(last.id, model.selectedId)

        model.resetSelection(remaining, preferred = null)

        assertEquals(currentRow.id, model.selectedId, "the default applies again once the pending id is spent")
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

        // A path no project owns, so only a wholesale reset of the map can remove it.
        val probe = "/not/a/project"
        model.icons[probe] = StubIcon

        model.refresh()

        // Poll rather than await, so a refresh that breaks either invariant midway is still caught.
        repeat(50) {
            assertEquals(ProjectLoadState.READY, model.loadState, "a refresh must not show the loading placeholder")
            assertSame(StubIcon, model.icons[probe], "a refresh must keep the icons it already has")
            delay(20)
        }

        assertTrue(model.projects.open.any { it.displayName == "ModelTest" })
    }

    private suspend fun awaitLoaded(): ProjectLoadState? = withTimeoutOrNull(60.seconds) {
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
        model.loadState
    }
}

private object StubIcon : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit
    override fun getIconWidth(): Int = 16
    override fun getIconHeight(): Int = 16
}
