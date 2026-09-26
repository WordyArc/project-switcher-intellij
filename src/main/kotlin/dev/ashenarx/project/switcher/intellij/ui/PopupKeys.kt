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

internal class PopupKeys(
    val toggle: List<KeyStroke> = emptyList(),
    val close: KeyStroke = closeShortcut(),
    val speedSearch: Boolean = true,
    val pageSize: () -> Int = { 1 },
)

internal fun handleKeyEvent(
    event: KeyEvent,
    model: ProjectSwitcherModel,
    keys: PopupKeys,
    onClose: () -> Unit,
    onResult: (SwitchOutcome) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (keys.toggle.any(event::matches)) {
        onClose()
        return true
    }

    if (event.matches(keys.close)) {
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
