package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.common.timeoutRunBlocking
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
import java.awt.event.InputEvent
import javax.swing.KeyStroke
import java.awt.event.KeyEvent as AwtKeyEvent

class PopupKeysTest {

    private val alpha = open("alpha")
    private val beta = recent("beta")
    private val gamma = recent("gamma")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = FakeProjectActions(ProjectList(open = listOf(alpha), recent = listOf(beta, gamma)))
    private val model = modelWith(actions)

    private var pageSize = 1
    private var keys = PopupKeys(toggle = listOf(ALT_F2), pageSize = { pageSize })

    private var closed = 0
    private val outcomes = mutableListOf<SwitchOutcome>()

    private val opened: List<ProjectItem.Open>
        get() = outcomes.filterIsInstance<SwitchOutcome.Focus>().map { it.project }

    private val reopened: List<Pair<ProjectItem.Recent, OpenTarget>>
        get() = outcomes.filterIsInstance<SwitchOutcome.Reopen>().map { it.project to it.target }

    private val closedCurrent: List<SwitchOutcome.CloseCurrent>
        get() = outcomes.filterIsInstance<SwitchOutcome.CloseCurrent>()

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
    fun `navigation keys never reach the search field`() = timeoutRunBlocking {
        loadRows()

        for (key in listOf(Key.DirectionDown, Key.DirectionUp, Key.PageDown, Key.PageUp, Key.MoveHome, Key.MoveEnd, Key.Tab)) {
            assertTrue(press(key), "$key must be handled by the list, not typed into the query")
        }
        assertEquals("", model.query)
    }

    @Test
    fun `page keys move a page at a time and stop at the ends`() = timeoutRunBlocking {
        val rows = (1..6).map { recent("p$it") }
        val paged = modelOver(ProjectList(open = emptyList(), recent = rows))
        pageSize = 2

        handle(event(Key.PageDown), paged)
        assertEquals(rows[2].id, paged.selectedId)

        handle(event(Key.PageDown), paged)
        handle(event(Key.PageDown), paged)
        assertEquals(rows.last().id, paged.selectedId, "a page move must stop at the last row instead of wrapping")

        handle(event(Key.PageUp), paged)
        assertEquals(rows[3].id, paged.selectedId)
    }

    @Test
    fun `home and end jump to the first and last rows`() = timeoutRunBlocking {
        loadRows()

        press(Key.MoveEnd)
        assertEquals(gamma.id, model.selectedId)

        press(Key.MoveHome)
        assertEquals(alpha.id, model.selectedId)
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
    fun `a click with a modifier picks the window the same way enter does`() {
        assertEquals(OpenTarget.Ask, openTargetFor(ctrl = false, shift = false))
        assertEquals(OpenTarget.NewWindow, openTargetFor(ctrl = true, shift = false))
        assertEquals(OpenTarget.CurrentWindow, openTargetFor(ctrl = false, shift = true))
        assertEquals(OpenTarget.NewWindow, openTargetFor(ctrl = true, shift = true))
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

        assertTrue(handle(event(Key.Enter), empty), "swallowing enter keeps it out of the search field")
        assertEquals(emptyList<ProjectItem.Open>(), opened)
        assertEquals(emptyList<Pair<ProjectItem.Recent, OpenTarget>>(), reopened)
    }

    @Test
    fun `enter follows the query rather than the row selected before it`() = timeoutRunBlocking {
        loadRows()
        model.select(gamma.id)

        model.queryState.setTextAndPlaceCursorAtEnd("alpha")
        assertTrue(press(Key.Enter))

        assertEquals(listOf(alpha), opened, "the selection cannot survive outside the filtered list")
    }

    @Test
    fun `the keymap shortcut closes the popup so it toggles`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.F2, alt = true))
        assertEquals(1, closed)
    }

    @Test
    fun `a remapped shortcut toggles the popup and alt-F2 no longer does`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(KeyStroke.getKeyStroke(AwtKeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)))

        assertFalse(press(Key.F2, alt = true), "alt-F2 is no longer bound, so it belongs to the search field")
        assertEquals(0, closed)

        assertTrue(press(Key.P, ctrl = true, alt = true))
        assertEquals(1, closed)
    }

    @Test
    fun `a bare F2 goes to the search field`() = timeoutRunBlocking {
        loadRows()

        assertFalse(press(Key.F2))
        assertEquals(0, closed)
    }

    @Test
    fun `escape first clears the query and only then closes the popup`() = timeoutRunBlocking {
        loadRows()
        model.queryState.setTextAndPlaceCursorAtEnd("be")

        assertTrue(press(Key.Escape))
        assertEquals("", model.query)
        assertEquals(0, closed, "the first escape belongs to the query")

        assertTrue(press(Key.Escape))
        assertEquals(1, closed)
    }

    @Test
    fun `printable keys go to the search field`() = timeoutRunBlocking {
        loadRows()

        assertFalse(press(Key.A))
    }

    @Test
    fun `delete and backspace only ever edit the query`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        assertFalse(press(Key.Backspace))
        assertFalse(press(Key.Delete))

        assertEquals(emptyList<String>(), actions.forgotten, "both keys belong to the search field")
    }

    @Test
    fun `the close shortcut follows the platform`() {
        assertTrue(event(Key.W, meta = true).matches(closeShortcut(mac = true)))
        assertFalse(event(Key.W, ctrl = true).matches(closeShortcut(mac = true)))

        assertTrue(event(Key.W, ctrl = true).matches(closeShortcut(mac = false)))
        assertFalse(event(Key.W, meta = true).matches(closeShortcut(mac = false)))

        assertFalse(event(Key.W).matches(closeShortcut(mac = true)), "a bare W belongs to the query")
        assertFalse(event(Key.W).matches(closeShortcut(mac = false)), "a bare W belongs to the query")
    }

    @Test
    fun `a shortcut matches only with exactly its modifiers`() {
        assertTrue(event(Key.F2, alt = true).matches(ALT_F2))
        assertFalse(event(Key.F2, alt = true, shift = true).matches(ALT_F2), "an extra shift makes it another chord")
        assertFalse(event(Key.F3, alt = true).matches(ALT_F2))
    }

    @Test
    fun `the close shortcut removes the selected recent project`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        assertTrue(handle(closeShortcut()))

        assertEquals(listOf(beta.path), actions.forgotten)
        assertEquals("", model.query, "the query must not see the shortcut")
        assertEquals(gamma.id, model.selectedId, "expected the row below the one that was removed")
    }

    @Test
    fun `the close shortcut hands back the last open project instead of closing it itself`() =
        timeoutRunBlocking {
            val current = open("current", isCurrent = true)
            val soleModel = modelOver(ProjectList(open = listOf(current), recent = emptyList()))

            assertTrue(handle(closeShortcut(), soleModel))

            assertEquals(listOf(SwitchOutcome.CloseCurrent(current, next = null)), closedCurrent)
        }

    @Test
    fun `the close shortcut closes the current project even while another one is open`() =
        timeoutRunBlocking {
            val current = open("current", isCurrent = true)
            val other = open("other")
            val actions = FakeProjectActions(ProjectList(open = listOf(current, other), recent = emptyList()))
            val twoOpen = modelOver(actions)
            twoOpen.select(current.id)

            assertTrue(handle(closeShortcut(), twoOpen))

            assertEquals(
                listOf(SwitchOutcome.CloseCurrent(current, next = other)),
                closedCurrent,
                "the shortcut must not silently do nothing, and the other project has to take over",
            )
            assertEquals(
                emptyList<ProjectItem.Open>(),
                actions.closed,
                "the current project hosts the popup, so it is closed after the popup, not by the model",
            )
        }

    @Test
    fun `a bare W goes to the search field`() = timeoutRunBlocking {
        loadRows()

        assertFalse(press(Key.W))
        assertEquals(emptyList<String>(), actions.forgotten)
    }

    private suspend fun loadRows() {
        model.load()
        awaitLoaded(model)
    }

    private suspend fun modelOver(projects: ProjectList): ProjectSwitcherModel =
        modelOver(FakeProjectActions(projects))

    private suspend fun modelOver(actions: FakeProjectActions): ProjectSwitcherModel {
        val other = modelWith(actions)
        other.load()
        awaitLoaded(other)
        return other
    }

    private fun modelWith(actions: FakeProjectActions) =
        ProjectSwitcherModel(coroutineScope = scope, actions = actions, uiContext = Dispatchers.Unconfined)

    private suspend fun awaitLoaded(model: ProjectSwitcherModel) {
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
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
        model = model,
        keys = keys,
        onClose = { closed++ },
        onResult = { outcomes += it },
    )

    private companion object {
        val ALT_F2: KeyStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_F2, InputEvent.ALT_DOWN_MASK)
    }
}
