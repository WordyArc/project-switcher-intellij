package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class PopupKeysTest {

    private val alpha = open("alpha")
    private val beta = recent("beta")
    private val gamma = recent("gamma")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = FakeProjectActions(ProjectList(open = listOf(alpha), recent = listOf(beta, gamma)))
    private val model = ProjectSwitcherModel(currentProject = null, coroutineScope = scope, actions = actions)
    private val search = FakeSpeedSearch()

    private var closed = 0
    private val outcomes = mutableListOf<SwitchOutcome>()

    private val opened: List<ProjectItem.Open>
        get() = outcomes.filterIsInstance<SwitchOutcome.Focus>().map { it.project }

    private val reopened: List<Pair<ProjectItem.Recent, OpenTarget>>
        get() = outcomes.filterIsInstance<SwitchOutcome.Reopen>().map { it.project to it.target }

    private val closedCurrent: List<ProjectItem.Open>
        get() = outcomes.filterIsInstance<SwitchOutcome.CloseCurrent>().map { it.project }

    @AfterEach
    fun cancelScope() {
        scope.cancel("test finished")
    }

    @Test
    fun `key releases are ignored so one keystroke acts once`() = timeoutRunBlocking {
        loadRows()

        assertFalse(press(Key.Enter, type = KeyEventType.KeyUp), "a key release must fall through unhandled")
        assertEquals(emptyList<ProjectItem.Open>(), opened)
    }

    @Test
    fun `arrow keys walk the visible list across the section boundary`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.DirectionDown))
        assertEquals(beta.id, model.selectedId)

        assertTrue(press(Key.DirectionDown))
        assertEquals(gamma.id, model.selectedId)

        assertTrue(press(Key.DirectionUp))
        assertEquals(beta.id, model.selectedId)
    }

    @Test
    fun `arrow keys wrap around at both ends`() = timeoutRunBlocking {
        loadRows()

        press(Key.DirectionUp)
        assertEquals(gamma.id, model.selectedId)

        press(Key.DirectionDown)
        assertEquals(alpha.id, model.selectedId)
    }

    @Test
    fun `arrow keys never reach speed search`() = timeoutRunBlocking {
        loadRows()

        press(Key.DirectionDown)
        press(Key.DirectionUp)

        assertEquals(emptyList<KeyEvent>(), search.forwarded)
    }

    @Test
    fun `enter focuses the selected open project`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.Enter))
        assertEquals(listOf(alpha), opened)
        assertEquals(emptyList<Pair<ProjectItem.Recent, OpenTarget>>(), reopened)
    }

    @Test
    fun `modifiers on enter choose where a recent project opens`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        press(Key.Enter)
        press(Key.Enter, ctrl = true)
        press(Key.Enter, shift = true)

        assertEquals(
            listOf(OpenTarget.Ask, OpenTarget.NewWindow, OpenTarget.CurrentWindow),
            reopened.map { (_, target) -> target },
        )
        assertEquals(listOf(beta, beta, beta), reopened.map { (item, _) -> item })
    }

    @Test
    fun `ctrl wins over shift when both are held`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        press(Key.Enter, ctrl = true, shift = true)

        assertEquals(listOf(OpenTarget.NewWindow), reopened.map { (_, target) -> target })
    }

    @Test
    fun `modifiers do not change how an open project is activated`() = timeoutRunBlocking {
        loadRows()

        press(Key.Enter, ctrl = true)

        assertEquals(listOf(alpha), opened)
    }

    @Test
    fun `enter without a selection stays handled but does nothing`() = timeoutRunBlocking {
        val empty = modelOver(ProjectList.EMPTY)
        assertNull(empty.selectedId)

        assertTrue(handle(event(Key.Enter), empty), "swallowing enter keeps it from reaching speed search")
        assertEquals(emptyList<ProjectItem.Open>(), opened)
        assertEquals(emptyList<Pair<ProjectItem.Recent, OpenTarget>>(), reopened)
    }

    @Test
    fun `enter follows the query rather than the row selected before it`() = timeoutRunBlocking {
        loadRows()
        model.select(gamma.id)

        model.query = "alpha"
        assertTrue(press(Key.Enter))

        assertEquals(listOf(alpha), opened, "the selection cannot survive outside the filtered list")
    }

    @Test
    fun `alt-F2 closes the popup so the shortcut toggles it`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.F2, alt = true))
        assertEquals(1, closed)
    }

    @Test
    fun `a bare F2 is left to speed search`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.F2))
        assertEquals(0, closed)
        assertEquals(1, search.forwarded.size)
    }

    @Test
    fun `escape first clears the search and only then closes the popup`() = timeoutRunBlocking {
        loadRows()
        search.hideSearchResult = true

        assertTrue(press(Key.Escape))
        assertEquals(0, closed, "the first escape belongs to the search overlay")

        search.hideSearchResult = false

        assertTrue(press(Key.Escape))
        assertEquals(1, closed)
    }

    @Test
    fun `printable keys reach speed search`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.A))
        assertEquals(1, search.forwarded.size)
    }

    @Test
    fun `delete and backspace only ever edit the query`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        assertTrue(press(Key.Backspace))
        assertTrue(press(Key.Delete))

        assertEquals(2, search.forwarded.size, "both keys belong to the search field now")
        assertEquals(emptyList<String>(), actions.forgotten)
    }

    @Test
    fun `the close shortcut follows the platform`() {
        assertTrue(event(Key.W, meta = true).isCloseShortcut(mac = true))
        assertFalse(event(Key.W, ctrl = true).isCloseShortcut(mac = true))

        assertTrue(event(Key.W, ctrl = true).isCloseShortcut(mac = false))
        assertFalse(event(Key.W, meta = true).isCloseShortcut(mac = false))

        assertFalse(event(Key.W).isCloseShortcut(mac = true), "a bare W belongs to the query")
        assertFalse(event(Key.W).isCloseShortcut(mac = false), "a bare W belongs to the query")
    }

    @Test
    fun `the close shortcut removes the selected recent project`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        assertTrue(handle(closeShortcut()))

        assertEquals(listOf(beta.path), actions.forgotten)
        assertEquals(emptyList<KeyEvent>(), search.forwarded, "the query must not see the shortcut")

        awaitRows { rows -> rows.all.none { it.id == beta.id } }
        assertEquals(gamma.id, model.selectedId, "expected the row below the one that was removed")
    }

    @Test
    fun `the close shortcut hands back the last open project instead of closing it itself`() =
        timeoutRunBlocking {
            val current = open("current", isCurrent = true)
            val soleModel = modelOver(ProjectList(open = listOf(current), recent = emptyList()))

            assertTrue(handle(closeShortcut(), soleModel))

            assertEquals(listOf(current), closedCurrent)
        }

    @Test
    fun `the close shortcut leaves the current project alone while another one is open`() =
        timeoutRunBlocking {
            val current = open("current", isCurrent = true)
            val other = open("other")
            val actions = FakeProjectActions(ProjectList(open = listOf(current, other), recent = emptyList()))
            val twoOpen = modelOver(actions)
            twoOpen.select(current.id)

            assertTrue(handle(closeShortcut(), twoOpen))

            assertEquals(emptyList<ProjectItem.Open>(), closedCurrent, "a window to switch to has to remain")
            assertEquals(emptyList<ProjectItem.Open>(), actions.closed)
        }

    @Test
    fun `a bare W is left to speed search`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.W))

        assertEquals(1, search.forwarded.size)
    }

    @Test
    fun `an unhandled key reports whatever speed search reports`() = timeoutRunBlocking {
        loadRows()
        search.processResult = false

        assertFalse(press(Key.Tab), "an unconsumed key must stay available to the platform")
    }

    private suspend fun loadRows() {
        model.load()
        awaitLoaded(model)
    }

    private suspend fun modelOver(projects: ProjectList): ProjectSwitcherModel =
        modelOver(FakeProjectActions(projects))

    private suspend fun modelOver(actions: FakeProjectActions): ProjectSwitcherModel {
        val other = ProjectSwitcherModel(currentProject = null, coroutineScope = scope, actions = actions)
        other.load()
        awaitLoaded(other)
        return other
    }

    private suspend fun awaitLoaded(model: ProjectSwitcherModel) {
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
    }

    private suspend fun awaitRows(matches: (ProjectList) -> Boolean) {
        while (!matches(model.rows)) delay(20)
    }

    private fun closeShortcut(): KeyEvent =
        event(Key.W, ctrl = !SystemInfoRt.isMac, meta = SystemInfoRt.isMac)

    private fun press(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
    ): Boolean = handle(event(key, type, ctrl, shift, alt))

    private fun event(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): KeyEvent = KeyEvent(
        key = key,
        type = type,
        isCtrlPressed = ctrl,
        isShiftPressed = shift,
        isAltPressed = alt,
        isMetaPressed = meta,
    )

    private fun handle(event: KeyEvent, model: ProjectSwitcherModel = this.model): Boolean = handleKeyEvent(
        event = event,
        search = search,
        model = model,
        onClose = { closed++ },
        onResult = { outcomes += it },
    )

    private class FakeSpeedSearch : SpeedSearch {
        var hideSearchResult: Boolean = false
        var processResult: Boolean = true
        val forwarded = mutableListOf<KeyEvent>()

        override fun hideSearch(): Boolean = hideSearchResult

        override fun processKeyEvent(event: KeyEvent): Boolean {
            forwarded += event
            return processResult
        }
    }
}

internal fun open(name: String, isCurrent: Boolean = false) = ProjectItem.Open(
    locationHash = "hash-$name",
    displayName = name,
    path = "/projects/$name",
    branch = null,
    isCurrent = isCurrent,
)

internal fun recent(name: String) = ProjectItem.Recent(
    displayName = name,
    path = "/projects/$name",
    branch = null,
)
