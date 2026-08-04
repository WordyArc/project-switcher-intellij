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
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.moveSelection
import org.jetbrains.jewel.ui.component.SpeedSearchScope

/**
 * The slice of [SpeedSearchScope] the key handler needs. Narrowing it keeps the handler free of the
 * Compose UI tree, which a `SpeedSearchScope` can only be obtained from.
 */
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
    items: List<ProjectItem>,
    model: ProjectSwitcherModel,
    onClose: () -> Unit,
    onSelectOpen: (ProjectItem.Open) -> Unit,
    onSelectRecent: (ProjectItem.Recent, OpenTarget) -> Unit,
    onCloseCurrent: (ProjectItem.Open) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.key == Key.F2 && event.isAltPressed) {
        onClose()
        return true
    }

    if (event.isCloseShortcut()) {
        closeSelection(items, model, onCloseCurrent)
        return true
    }

    return when (event.key) {
        Key.DirectionDown -> {
            model.selectedId = moveSelection(items, model.selectedId, delta = +1)
            true
        }

        Key.DirectionUp -> {
            model.selectedId = moveSelection(items, model.selectedId, delta = -1)
            true
        }

        Key.Enter -> {
            activateSelection(event, items, model.selectedId, onSelectOpen, onSelectRecent)
            true
        }

        Key.Escape -> {
            if (!search.hideSearch()) onClose()
            true
        }

        else -> search.processKeyEvent(event)
    }
}

/** Refuses to close the current project when another project is available to switch to. */
private fun closeSelection(
    items: List<ProjectItem>,
    model: ProjectSwitcherModel,
    onCloseCurrent: (ProjectItem.Open) -> Unit,
) {
    val selected = items.firstOrNull { it.id == model.selectedId } ?: return

    if (selected.isCurrent) {
        if (model.projects.open.size == 1) (selected as? ProjectItem.Open)?.let(onCloseCurrent)
        return
    }

    model.delete(selected, items)
}

private fun activateSelection(
    event: KeyEvent,
    items: List<ProjectItem>,
    selectedId: String?,
    onSelectOpen: (ProjectItem.Open) -> Unit,
    onSelectRecent: (ProjectItem.Recent, OpenTarget) -> Unit,
) {
    when (val selected = items.firstOrNull { it.id == selectedId }) {
        is ProjectItem.Open -> onSelectOpen(selected)
        is ProjectItem.Recent -> onSelectRecent(selected, event.openTarget())
        null -> Unit
    }
}


internal fun KeyEvent.isCloseShortcut(mac: Boolean = SystemInfoRt.isMac): Boolean =
    key == Key.W && if (mac) isMetaPressed else isCtrlPressed

internal fun KeyEvent.openTarget(): OpenTarget = when {
    isCtrlPressed -> OpenTarget.NewWindow
    isShiftPressed -> OpenTarget.CurrentWindow
    else -> OpenTarget.Ask
}
