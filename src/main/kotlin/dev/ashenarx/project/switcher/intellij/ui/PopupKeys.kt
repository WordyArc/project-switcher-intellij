package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import com.intellij.openapi.util.SystemInfoRt
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import java.awt.event.InputEvent
import javax.swing.KeyStroke
import java.awt.event.KeyEvent as AwtKeyEvent

private val DELETE_KEYS = setOf(Key.Delete, Key.Backspace)

private val NAVIGATION_KEYS = setOf(Key.DirectionDown, Key.DirectionUp, Key.PageDown, Key.PageUp, Key.MoveHome, Key.MoveEnd)

internal class PopupKeys(
    val toggle: List<KeyStroke> = emptyList(),
    val speedSearch: Boolean = true,
    closeOnDelete: Boolean = false,
    val pageSize: () -> Int = { 1 },
) {
    val close: KeyStroke = if (closeOnDelete) deleteShortcut() else closeShortcut()

    private val deletePresses = if (closeOnDelete) DeletePresses() else null

    fun closes(event: KeyEvent, query: String): Boolean =
        deletePresses?.closes(event, queryEmpty = query.isEmpty())
            ?: (event.type == KeyEventType.KeyDown && event.matches(close))
}

private class DeletePresses {
    private val held = mutableSetOf<Key>()
    private var armed = true

    fun closes(event: KeyEvent, queryEmpty: Boolean): Boolean {
        if (event.type == KeyEventType.KeyUp) held -= event.key
        if (event.type != KeyEventType.KeyDown) return false

        val fresh = held.add(event.key)
        when {
            !queryEmpty -> armed = false
            event.key in NAVIGATION_KEYS -> armed = true
            event.key in DELETE_KEYS -> return fresh && armed && !event.hasModifiers
        }
        return false
    }
}

internal fun handleKeyEvent(
    event: KeyEvent,
    model: ProjectSwitcherModel,
    keys: PopupKeys,
    onClose: () -> Unit,
    onResult: (SwitchOutcome) -> Unit,
): Boolean {
    val closes = keys.closes(event, model.query)
    if (event.type != KeyEventType.KeyDown) return false

    if (keys.toggle.any(event::matches)) {
        onClose()
        return true
    }

    if (closes) {
        model.closeSelected()?.let(onResult)
        return true
    }

    when (event.key) {
        Key.DirectionDown -> model.moveSelection(delta = +1)
        Key.DirectionUp -> model.moveSelection(delta = -1)
        Key.PageDown -> model.moveSelectionByPage(delta = +keys.pageSize())
        Key.PageUp -> model.moveSelectionByPage(delta = -keys.pageSize())
        Key.MoveHome -> model.selectFirst()
        Key.MoveEnd -> model.selectLast()
        Key.Enter, Key.NumPadEnter -> model.selectedItem?.let { onResult(SwitchOutcome.of(it, event.openTarget())) }
        Key.Escape -> if (!model.clearQuery()) onClose()
        Key.Tab -> Unit
        else -> return keys.speedSearch && model.queryState.typeSpeedSearchKey(event)
    }
    return true
}

internal fun closeShortcut(mac: Boolean = SystemInfoRt.isMac): KeyStroke =
    KeyStroke.getKeyStroke(AwtKeyEvent.VK_W, if (mac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK)

internal fun deleteShortcut(mac: Boolean = SystemInfoRt.isMac): KeyStroke =
    KeyStroke.getKeyStroke(if (mac) AwtKeyEvent.VK_BACK_SPACE else AwtKeyEvent.VK_DELETE, 0)

internal fun KeyEvent.matches(stroke: KeyStroke): Boolean =
    key.nativeKeyCode == stroke.keyCode &&
        isAltPressed == stroke.has(InputEvent.ALT_DOWN_MASK) &&
        isCtrlPressed == stroke.has(InputEvent.CTRL_DOWN_MASK) &&
        isMetaPressed == stroke.has(InputEvent.META_DOWN_MASK) &&
        isShiftPressed == stroke.has(InputEvent.SHIFT_DOWN_MASK)

internal fun KeyEvent.openTarget(): OpenTarget = openTargetFor(ctrl = isCtrlPressed, shift = isShiftPressed)

internal fun openTargetFor(ctrl: Boolean, shift: Boolean): OpenTarget = when {
    ctrl -> OpenTarget.NewWindow
    shift -> OpenTarget.CurrentWindow
    else -> OpenTarget.Ask
}

private fun KeyStroke.has(mask: Int): Boolean = modifiers and mask != 0

private val KeyEvent.hasModifiers: Boolean
    get() = isAltPressed || isCtrlPressed || isMetaPressed || isShiftPressed
