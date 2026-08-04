package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.intellij.openapi.util.SystemInfoRt
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext


class PopupKeysTest {

    private val alpha = open("alpha")
    private val beta = recent("beta")
    private val gamma = recent("gamma")
    private val current = open("current", isCurrent = true)
    private val items = listOf(alpha, beta, gamma)

    private val model = ProjectSwitcherModel(currentProject = null, coroutineScope = CoroutineScope(EmptyCoroutineContext))
    private val search = FakeSpeedSearch()

    private var closed = 0
    private val opened = mutableListOf<ProjectItem.Open>()
    private val reopened = mutableListOf<Pair<ProjectItem.Recent, OpenTarget>>()
    private val closedCurrent = mutableListOf<ProjectItem.Open>()

    @Test
    fun `key releases are ignored so one keystroke acts once`() {
        model.selectedId = alpha.id

        assertFalse(press(Key.Enter, type = KeyEventType.KeyUp), "a key release must fall through unhandled")
        assertEquals(emptyList<ProjectItem.Open>(), opened)
    }

    @Test
    fun `arrow keys walk the visible list across the section boundary`() {
        model.selectedId = alpha.id

        assertTrue(press(Key.DirectionDown))
        assertEquals(beta.id, model.selectedId)

        assertTrue(press(Key.DirectionDown))
        assertEquals(gamma.id, model.selectedId)

        assertTrue(press(Key.DirectionUp))
        assertEquals(beta.id, model.selectedId)
    }

    @Test
    fun `arrow keys wrap around at both ends`() {
        model.selectedId = alpha.id
        press(Key.DirectionUp)
        assertEquals(gamma.id, model.selectedId)

        press(Key.DirectionDown)
        assertEquals(alpha.id, model.selectedId)
    }

    @Test
    fun `arrow keys never reach speed search`() {
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        assertEquals(emptyList<KeyEvent>(), search.forwarded)
    }

    @Test
    fun `enter focuses the selected open project`() {
        model.selectedId = alpha.id

        assertTrue(press(Key.Enter))
        assertEquals(listOf(alpha), opened)
        assertEquals(emptyList<Pair<ProjectItem.Recent, OpenTarget>>(), reopened)
    }

    @Test
    fun `modifiers on enter choose where a recent project opens`() {
        model.selectedId = beta.id

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
    fun `ctrl wins over shift when both are held`() {
        model.selectedId = beta.id

        press(Key.Enter, ctrl = true, shift = true)

        assertEquals(listOf(OpenTarget.NewWindow), reopened.map { (_, target) -> target })
    }

    @Test
    fun `modifiers do not change how an open project is activated`() {
        model.selectedId = alpha.id

        press(Key.Enter, ctrl = true)

        assertEquals(listOf(alpha), opened)
    }

    @Test
    fun `enter without a selection stays handled but does nothing`() {
        model.selectedId = null

        assertTrue(press(Key.Enter), "swallowing enter keeps it from reaching speed search")
        assertEquals(emptyList<ProjectItem.Open>(), opened)
        assertEquals(emptyList<Pair<ProjectItem.Recent, OpenTarget>>(), reopened)
    }

    @Test
    fun `enter on a selection that the query filtered away does nothing`() {
        model.selectedId = gamma.id

        assertTrue(handle(event(Key.Enter), visible = listOf(alpha)))
        assertEquals(emptyList<ProjectItem.Open>(), opened)
        assertEquals(emptyList<Pair<ProjectItem.Recent, OpenTarget>>(), reopened)
    }

    @Test
    fun `alt-F2 closes the popup so the shortcut toggles it`() {
        assertTrue(press(Key.F2, alt = true))
        assertEquals(1, closed)
    }

    @Test
    fun `a bare F2 is left to speed search`() {
        assertTrue(press(Key.F2))
        assertEquals(0, closed)
        assertEquals(1, search.forwarded.size)
    }

    @Test
    fun `escape first clears the search and only then closes the popup`() {
        search.hideSearchResult = true

        assertTrue(press(Key.Escape))
        assertEquals(0, closed, "the first escape belongs to the search overlay")

        search.hideSearchResult = false

        assertTrue(press(Key.Escape))
        assertEquals(1, closed)
    }

    @Test
    fun `printable keys reach speed search`() {
        assertTrue(press(Key.A))
        assertEquals(1, search.forwarded.size)
    }

    @Test
    fun `delete and backspace only ever edit the query`() {
        // A selected row and no query at all: the case that used to remove a project instead.
        model.selectedId = beta.id

        assertTrue(press(Key.Backspace))
        assertTrue(press(Key.Delete))

        assertEquals(2, search.forwarded.size, "both keys belong to the search field now")
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
    fun `the close shortcut is claimed by the popup rather than typed into the query`() {
        // Selecting the current project keeps the platform services out of a plain unit test;
        // ProjectSwitcherModelTest covers what closing a row actually does.
        model.selectedId = current.id

        assertTrue(handle(closeShortcut(), visible = items + current))

        assertEquals(emptyList<KeyEvent>(), search.forwarded)
    }

    @Test
    fun `a bare W is left to speed search`() {
        model.selectedId = current.id

        assertTrue(press(Key.W))

        assertEquals(1, search.forwarded.size)
    }

    private fun closeShortcut(): KeyEvent =
        event(Key.W, ctrl = !SystemInfoRt.isMac, meta = SystemInfoRt.isMac)

    @Test
    fun `an unhandled key reports whatever speed search reports`() {
        search.processResult = false

        assertFalse(press(Key.Tab), "an unconsumed key must stay available to the platform")
    }

    private fun press(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
    ): Boolean = handle(event(key, type, ctrl, shift, alt), items)

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

    private fun handle(event: KeyEvent, visible: List<ProjectItem>): Boolean = handleKeyEvent(
        event = event,
        search = search,
        items = visible,
        model = model,
        onClose = { closed++ },
        onSelectOpen = { opened += it },
        onSelectRecent = { item, target -> reopened += item to target },
        onCloseCurrent = { closedCurrent += it },
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
