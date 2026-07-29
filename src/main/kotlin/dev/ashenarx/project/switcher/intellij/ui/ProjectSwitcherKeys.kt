package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import dev.ashenarx.project.switcher.intellij.data.ProjectData
import java.awt.event.InputEvent

internal fun handleProjectSwitcherKeyEvent(
    event: KeyEvent,
    query: String,
    queryState: TextFieldState,
    allProjects: List<ProjectData>,
    selectedId: String?,
    onClose: () -> Unit,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, modifiersEx: Int) -> Unit,
    updateSelectedId: (String?) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.key == Key.F2 && event.isAltPressed) {
        onClose()
        return true
    }

    return when (event.key) {
        Key.Escape -> {
            // как в speed-search: Esc сначала чистит, потом закрывает
            if (query.isNotEmpty()) {
                clearQuery(queryState)
            } else {
                onClose()
            }
            true
        }
        Key.DirectionDown -> {
            updateSelectedId(moveSelection(allProjects, selectedId, delta = +1))
            true
        }
        Key.DirectionUp -> {
            updateSelectedId(moveSelection(allProjects, selectedId, delta = -1))
            true
        }
        Key.Enter -> {
            activateSelected(event, allProjects, selectedId, onSelectOpen, onSelectRecent)
            true
        }
        Key.Backspace -> {
            deleteLastChar(query, queryState)
            true
        }
        else -> appendTypedChar(event, queryState)
    }
}

private fun moveSelection(allProjects: List<ProjectData>, selectedId: String?, delta: Int): String? {
    if (allProjects.isEmpty()) return null

    val currentIndex = allProjects.indexOfFirst { it.id == selectedId }
        .let { if (it >= 0) it else 0 }

    val newIndex = (currentIndex + delta).coerceIn(0, allProjects.lastIndex)
    return allProjects[newIndex].id
}

private fun activateSelected(
    event: KeyEvent,
    allProjects: List<ProjectData>,
    selectedId: String?,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, modifiersEx: Int) -> Unit
) {
    val selected = allProjects.firstOrNull { it.id == selectedId } ?: return
    val modifiersEx = event.toModifiersMask()

    when (selected) {
        is ProjectData.Open -> onSelectOpen(selected)
        is ProjectData.Recent -> onSelectRecent(selected, modifiersEx)
    }
}

private fun KeyEvent.toModifiersMask(): Int {
    var mask = 0
    if (isShiftPressed) mask = mask or InputEvent.SHIFT_DOWN_MASK
    if (isCtrlPressed) mask = mask or InputEvent.CTRL_DOWN_MASK
    if (isMetaPressed) mask = mask or InputEvent.META_DOWN_MASK
    if (isAltPressed) mask = mask or InputEvent.ALT_DOWN_MASK
    return mask
}

private fun deleteLastChar(query: String, queryState: TextFieldState) {
    if (query.isNotEmpty()) {
        queryState.edit { replace(length - 1, length, "") }
    }
}

private fun appendTypedChar(event: KeyEvent, queryState: TextFieldState): Boolean {
    // модификаторы не перехватываем — пусть системные хоткеи проходят
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false

    val ch = event.utf16CodePoint.toChar()
    return if (ch.isDefined() && !ch.isISOControl()) {
        queryState.edit { replace(length, length, ch.toString()) }
        true
    } else {
        false
    }
}

private fun clearQuery(state: TextFieldState) {
    state.edit { replace(0, length, "") }
}
