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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
    fun `a bare F2 is neither the toggle nor typed`() = timeoutRunBlocking {
        loadRows()

        assertFalse(press(Key.F2))
        assertEquals(0, closed)
        assertEquals("", model.query)
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
    fun `printable keys type into the query when the popup has no search field`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.G, char = 'g'))
        assertTrue(press(Key.A, char = 'a'))

        assertEquals("ga", model.query, "the popup itself is the speed search")
    }

    @Test
    fun `printable keys are left to the search field when it is shown`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), speedSearch = false)

        assertFalse(press(Key.A, char = 'a'), "the focused field types the character itself")
        assertEquals("", model.query)
    }

    @Test
    fun `delete and backspace never remove a project`() = timeoutRunBlocking {
        loadRows()
        model.select(beta.id)

        press(Key.Backspace)
        press(Key.Delete)

        assertEquals(emptyList<String>(), actions.forgotten, "both keys edit the query, removal has its own shortcut")
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
    fun `the delete key follows the platform`() {
        assertEquals(KeyStroke.getKeyStroke(AwtKeyEvent.VK_BACK_SPACE, 0), deleteShortcut(mac = true), "the key labelled delete on a Mac")
        assertEquals(KeyStroke.getKeyStroke(AwtKeyEvent.VK_DELETE, 0), deleteShortcut(mac = false))
    }

    @ParameterizedTest(name = "close on delete: {0}")
    @ValueSource(booleans = [false, true])
    fun `the key the hints show for closing is one that closes`(closeOnDelete: Boolean) = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = closeOnDelete)
        model.select(beta.id)

        assertTrue(handle(event(keys.close)))

        assertEquals(listOf(beta.path), actions.forgotten)
    }

    @Test
    fun `with delete chosen the close shortcut no longer closes anything`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.select(beta.id)

        handle(closeShortcut())

        assertNothingRemoved("delete replaces the shortcut rather than joining it")
        assertEquals("", model.query, "the shortcut must not be typed either")
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
    fun `a bare W is typed, not taken for the close shortcut`() = timeoutRunBlocking {
        loadRows()

        assertTrue(press(Key.W, char = 'w'))

        assertEquals("w", model.query)
        assertEquals(emptyList<String>(), actions.forgotten)
    }

    @Test
    fun `with close on delete, delete and backspace remove the selected project once the query is empty`() =
        timeoutRunBlocking {
            loadRows()
            keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
            model.select(beta.id)

            assertTrue(tap(Key.Backspace))
            assertEquals(listOf(beta.path), actions.forgotten)
            assertEquals(gamma.id, model.selectedId, "expected the row below the one that was removed")

            assertTrue(tap(Key.Delete))
            assertEquals(listOf(beta.path, gamma.path), actions.forgotten, "each separate press removes the next row")
        }

    @Test
    fun `with close on delete, a query is still edited rather than a project closed`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.queryState.setTextAndPlaceCursorAtEnd("be")

        assertTrue(tap(Key.Backspace))

        assertEquals("b", model.query)
        assertNothingRemoved("while there is a query the keys belong to it")
    }

    @Test
    fun `after erasing the query delete closes nothing until the selection moves`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.queryState.setTextAndPlaceCursorAtEnd("b")

        tap(Key.Backspace)
        tap(Key.Backspace)
        tap(Key.Delete)

        assertEquals("", model.query)
        assertNothingRemoved("a press too many while erasing must not close or remove a project")

        press(Key.DirectionDown)
        tap(Key.Backspace)

        assertEquals(listOf(beta.path), actions.forgotten, "a row picked after erasing is fair game")
    }

    @Test
    fun `after escape clears the query delete closes nothing until the selection moves`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.queryState.setTextAndPlaceCursorAtEnd("gam")

        tap(Key.Escape)
        tap(Key.Delete)

        assertNothingRemoved("the selection fell back to the default row, which nobody picked")
    }

    @Test
    fun `holding backspace erases the query and stops there`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.queryState.setTextAndPlaceCursorAtEnd("be")

        repeat(5) { press(Key.Backspace) }
        press(Key.Backspace, type = KeyEventType.KeyUp)

        assertEquals("", model.query)
        assertNothingRemoved("the auto-repeat of the erasing key must not go on to close projects")
    }

    @Test
    fun `holding delete over an empty query closes one project, not one per repeat`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.select(beta.id)

        repeat(3) { press(Key.Delete) }

        assertEquals(listOf(beta.path), actions.forgotten)
    }

    @Test
    fun `delete with a modifier never closes a project`() = timeoutRunBlocking {
        loadRows()
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)
        model.select(beta.id)

        tap(Key.Backspace, alt = true)
        tap(Key.Delete, shift = true)

        assertNothingRemoved("only a bare key closes, a chord may mean something else")
    }

    @Test
    fun `delete hands back the current project the way the close shortcut does`() = timeoutRunBlocking {
        val current = open("current", isCurrent = true)
        val soleModel = modelOver(ProjectList(open = listOf(current), recent = emptyList()))
        keys = PopupKeys(toggle = listOf(ALT_F2), closeOnDelete = true)

        assertTrue(handle(event(Key.Backspace), soleModel))

        assertEquals(listOf(SwitchOutcome.CloseCurrent(current, next = null)), closedCurrent)
    }

    private fun assertNothingRemoved(message: String) {
        assertEquals(emptyList<ProjectItem.Open>(), actions.closed, message)
        assertEquals(emptyList<String>(), actions.forgotten, message)
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

    private fun event(stroke: KeyStroke): KeyEvent = event(
        key = Key(stroke.keyCode),
        ctrl = stroke.modifiers and InputEvent.CTRL_DOWN_MASK != 0,
        shift = stroke.modifiers and InputEvent.SHIFT_DOWN_MASK != 0,
        alt = stroke.modifiers and InputEvent.ALT_DOWN_MASK != 0,
        meta = stroke.modifiers and InputEvent.META_DOWN_MASK != 0,
    )

    private fun press(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        char: Char? = null,
    ): Boolean = handle(event(key, type, ctrl, shift, alt, char = char))

    private fun tap(key: Key, shift: Boolean = false, alt: Boolean = false): Boolean {
        val handled = press(key, shift = shift, alt = alt)
        press(key, type = KeyEventType.KeyUp, shift = shift, alt = alt)
        return handled
    }

    private fun event(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
        char: Char? = null,
    ): KeyEvent = KeyEvent(
        key = key,
        type = type,
        codePoint = char?.code ?: 0,
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
