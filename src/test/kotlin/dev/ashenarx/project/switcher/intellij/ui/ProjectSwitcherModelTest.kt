package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.intellij.testFramework.common.timeoutRunBlocking
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProjectSwitcherModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val currentRow = open("current", isCurrent = true)
    private val previousRow = open("previous")
    private val middle = recent("middle")
    private val last = recent("last")
    private val items = ProjectList(open = listOf(currentRow, previousRow), recent = listOf(middle, last))

    @AfterEach
    fun cancelScope() {
        scope.cancel("test finished")
    }

    @Test
    fun `without a query the previous project is selected, not the current one`() = timeoutRunBlocking {
        val model = modelOver(items)

        assertEquals(previousRow.id, model.selectedId, "Enter right after opening the popup has to switch back")
    }

    @Test
    fun `the current project is selected when it is the only row`() = timeoutRunBlocking {
        val model = modelOver(ProjectList(open = listOf(currentRow), recent = emptyList()))

        assertEquals(currentRow.id, model.selectedId)
    }

    @Test
    fun `the top match outranks the previous project`() = timeoutRunBlocking {
        val model = modelOver(items)

        model.queryState.setTextAndPlaceCursorAtEnd("last")

        assertEquals(last.id, model.selectedId)
    }

    @Test
    fun `an empty list leaves nothing selected`() = timeoutRunBlocking {
        val model = modelOver(ProjectList.EMPTY)

        assertNull(model.selectedId)
    }

    @Test
    fun `highlights follow the query and vanish with it`() = timeoutRunBlocking {
        val model = modelOver(items)

        model.queryState.setTextAndPlaceCursorAtEnd("previous")

        val name = checkNotNull(model.highlights[previousRow.id]).name
        assertEquals("previous", name.joinToString("") { previousRow.displayName.substring(it) })

        model.queryState.setTextAndPlaceCursorAtEnd("")

        assertEquals(emptyMap<String, Any>(), model.highlights)
    }

    @Test
    fun `a name match outranks a match found only in the location`() = timeoutRunBlocking {
        val byLocation = recent("tools", location = "~/alpha/tools")
        val byName = recent("thealpha")
        val model = modelOver(ProjectList(open = emptyList(), recent = listOf(byLocation, byName)))

        model.queryState.setTextAndPlaceCursorAtEnd("alpha")

        assertEquals(
            listOf(byName.id, byLocation.id),
            model.rows.all.map { it.id },
            "a hit in the name says more about the project than one in the folder above it",
        )
        assertEquals(byName.id, model.selectedId)
        assertFalse(checkNotNull(model.highlights[byLocation.id]).isNameOnly, "the location hit must be shown")
    }

    @Test
    fun `a top match still wins after the user moved the selection by hand`() = timeoutRunBlocking {
        val model = modelOver(items)
        model.select(last.id)

        model.queryState.setTextAndPlaceCursorAtEnd("previous")

        assertEquals(previousRow.id, model.selectedId, "a new query outranks the selection made under the old one")
    }

    @Test
    fun `a selection made by hand survives a refresh that reorders the list`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.select(last.id)

        actions.publish(ProjectList(open = items.open.reversed(), recent = items.recent.reversed().map { it.copy(branch = "main") }))
        model.refresh()

        awaitRows(model) { rows -> rows.recent.all { it.branch != null } }
        assertEquals(last.id, model.selectedId)
    }

    @Test
    fun `after removing a project the selection moves to its neighbour`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.select(middle.id)

        model.closeSelected()

        assertEquals(listOf(middle.path), actions.forgotten)
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
        assertEquals(previousRow.id, model.selectedId, "expected the default, not a dangling id")
    }

    @Test
    fun `closing the current project is handed back to the caller even when another one is open`() =
        timeoutRunBlocking {
            val actions = FakeProjectActions(items)
            val model = modelOver(actions)
            model.select(currentRow.id)

            assertEquals(
                SwitchOutcome.CloseCurrent(currentRow, next = previousRow),
                model.closeSelected(),
                "the close shortcut must not silently do nothing, and the previous project takes over",
            )
            assertEquals(emptyList<ProjectItem.Open>(), actions.closed, "the caller closes it once the popup is gone")
        }

    @Test
    fun `closing the last open project is handed back to the caller`() = timeoutRunBlocking {
        val model = modelOver(ProjectList(open = listOf(currentRow), recent = listOf(middle)))
        model.select(currentRow.id)

        assertEquals(SwitchOutcome.CloseCurrent(currentRow, next = null), model.closeSelected())
    }

    @Test
    fun `removing a project keeps the list and its icons on screen`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        val model = modelOver(actions)
        model.icons[last.path] = StubBitmap
        model.select(middle.id)

        model.closeSelected()

        repeat(25) {
            assertEquals(ProjectLoadState.READY, model.loadState, "a removal must not flash the loading placeholder")
            assertSame(StubBitmap, model.icons[last.path], "a removal must keep the icons of the rows that stay")
            delay(20)
        }
    }

    @Test
    fun `the last known list and its icons are shown at once while the fresh one loads`() = timeoutRunBlocking {
        val snapshot = ProjectList(open = listOf(currentRow), recent = listOf(middle))
        val actions = FakeProjectActions(items, snapshot = snapshot, cachedIcons = mapOf(middle.path to StubBitmap))
        actions.collectGate = CompletableDeferred()
        val model = model(actions)

        model.load()

        assertEquals(ProjectLoadState.READY, model.loadState, "a known list must not wait behind a loading placeholder")
        assertEquals(snapshot, model.projects)
        assertSame(StubBitmap, model.icons[middle.path])

        checkNotNull(actions.collectGate).complete(Unit)
        awaitRows(model) { rows -> rows == items }
    }

    @Test
    fun `without a known list the popup loads first`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        actions.collectGate = CompletableDeferred()
        val model = model(actions)

        model.load()
        assertEquals(ProjectLoadState.LOADING, model.loadState)

        checkNotNull(actions.collectGate).complete(Unit)
        assertEquals(ProjectLoadState.READY, awaitLoaded(model))
        assertEquals(items, model.projects)
    }

    @Test
    fun `a failed load shows the error unless a known list is already on screen`() = timeoutRunBlocking {
        val failing = FakeProjectActions(items).apply { failure = IllegalStateException("broken platform") }
        val bare = model(failing)
        bare.load()
        assertEquals(ProjectLoadState.ERROR, awaitLoaded(bare))

        val snapshot = ProjectList(open = listOf(currentRow), recent = emptyList())
        val known = model(FakeProjectActions(items, snapshot = snapshot).apply { failure = IllegalStateException("broken platform") })
        known.load()
        delay(100)
        assertEquals(ProjectLoadState.READY, known.loadState, "an outdated list is better than an error")
        assertEquals(snapshot, known.projects)
    }

    @Test
    fun `refresh waits for the first load instead of racing it`() = timeoutRunBlocking {
        val actions = FakeProjectActions(items)
        actions.collectGate = CompletableDeferred()
        val model = model(actions)
        model.load()

        model.refresh()
        delay(200)

        assertEquals(ProjectList.EMPTY, model.projects, "a refresh must not publish over an unfinished load")
        assertEquals(ProjectLoadState.LOADING, model.loadState)
    }

    @Test
    fun `refresh republishes the list without a loading placeholder or an icon reset`() = timeoutRunBlocking {
        val actions = FakeProjectActions(ProjectList(open = listOf(currentRow), recent = emptyList()))
        val model = modelOver(actions)

        // No project owns this key, so only a wholesale reset can remove it.
        val probe = "/not/a/project"
        model.icons[probe] = StubBitmap

        actions.publish(items)
        model.refresh()

        // Polled, not awaited, to catch a refresh that breaks an invariant midway.
        repeat(25) {
            assertEquals(ProjectLoadState.READY, model.loadState, "a refresh must not show the loading placeholder")
            assertSame(StubBitmap, model.icons[probe], "a refresh must keep the icons it already has")
            delay(20)
        }

        assertEquals(items, model.projects)
    }

    @Test
    fun `recent projects whose directory is gone are reported`() = timeoutRunBlocking {
        val model = modelOver(FakeProjectActions(items, gone = setOf(last.path, "/not/listed")))

        awaitCondition { model.missing.isNotEmpty() }
        assertEquals(setOf(last.path), model.missing)
    }

    private suspend fun modelOver(projects: ProjectList): ProjectSwitcherModel =
        modelOver(FakeProjectActions(projects))

    private suspend fun modelOver(actions: FakeProjectActions): ProjectSwitcherModel {
        val model = model(actions)
        model.load()
        awaitLoaded(model)
        return model
    }

    private fun model(actions: FakeProjectActions) =
        ProjectSwitcherModel(coroutineScope = scope, actions = actions, uiContext = Dispatchers.Unconfined)

    private suspend fun awaitLoaded(model: ProjectSwitcherModel): ProjectLoadState {
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
        return model.loadState
    }

    private suspend fun awaitRows(model: ProjectSwitcherModel, matches: (ProjectList) -> Boolean) {
        awaitCondition { matches(model.rows) }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        while (!condition()) delay(20)
    }
}
