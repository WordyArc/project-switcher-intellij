package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.intellij.openapi.util.SystemInfoRt
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import org.jetbrains.jewel.ui.component.SpeedSearchScope

internal interface SpeedSearch {
    fun hideSearch(): Boolean

    fun processKeyEvent(event: KeyEvent): Boolean
}

internal fun SpeedSearchScope.asSpeedSearch(): SpeedSearch = object : SpeedSearch {
    override fun hideSearch(): Boolean = speedSearchState.hideSearch()

    override fun processKeyEvent(event: KeyEvent): Boolean = this@asSpeedSearch.processKeyEvent(event)
}

internal fun handleKeyEvent(
    event: KeyEvent,
    search: SpeedSearch,
    model: ProjectSwitcherModel,
    onClose: () -> Unit,
    onResult: (SwitchOutcome) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.key == Key.F2 && event.isAltPressed) {
        onClose()
        return true
    }

    if (event.isCloseShortcut()) {
        model.closeSelected()?.let { onResult(SwitchOutcome.CloseCurrent(it)) }
        return true
    }

    return when (event.key) {
        Key.DirectionDown -> {
            model.moveSelection(delta = +1)
            true
        }

        Key.DirectionUp -> {
            model.moveSelection(delta = -1)
            true
        }

        Key.Enter -> {
            model.selectedItem?.let { onResult(SwitchOutcome.of(it, event.openTarget())) }
            true
        }

        Key.Escape -> {
            if (!search.hideSearch()) onClose()
            true
        }

        else -> search.processKeyEvent(event)
    }
}

internal fun KeyEvent.isCloseShortcut(mac: Boolean = SystemInfoRt.isMac): Boolean =
    key == Key.W && if (mac) isMetaPressed else isCtrlPressed

internal fun KeyEvent.openTarget(): OpenTarget = when {
    isCtrlPressed -> OpenTarget.NewWindow
    isShiftPressed -> OpenTarget.CurrentWindow
    else -> OpenTarget.Ask
}
