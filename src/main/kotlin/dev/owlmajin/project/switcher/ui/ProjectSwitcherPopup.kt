package dev.owlmajin.project.switcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.dp
import dev.owlmajin.project.switcher.data.ProjectData
import dev.owlmajin.project.switcher.data.ProjectsData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.awt.event.InputEvent

private const val POPUP_TITLE = "Switch Project"
private const val SEARCH_PLACEHOLDER = "Search projects..."
private const val RECENT_HEADER = "Recent"

@Composable
fun ProjectSwitcherPopup(
    projectsData: ProjectsData,
    onClose: () -> Unit,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, modifiersEx: Int) -> Unit,
) {
    val queryState = remember { TextFieldState("") }
    val query by remember { derivedStateOf { queryState.text.toString() } }

    val filteredData = remember(projectsData, query) { projectsData.filter(query) }
    val allProjects = remember(filteredData) { filteredData.allProjects }
    val allIds = remember(allProjects) { allProjects.map { it.id } }

    var selectedId by remember { mutableStateOf(allIds.firstOrNull()) }

    // selection должна быть стабильной при фильтрации
    LaunchedEffect(allIds) {
        if (selectedId !in allIds) selectedId = allIds.firstOrNull()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                handleKeyEvent(
                    event = event,
                    query = query,
                    queryState = queryState,
                    allProjects = allProjects,
                    selectedId = selectedId,
                    onClose = onClose,
                    onSelectOpen = onSelectOpen,
                    onSelectRecent = onSelectRecent,
                    updateSelectedId = { selectedId = it }
                )
            }
    ) {
        Text(POPUP_TITLE)
        Spacer(Modifier.size(8.dp))

        TextField(
            state = queryState,
            placeholder = { Text(SEARCH_PLACEHOLDER) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(10.dp))

        ProjectList(
            data = filteredData,
            selectedId = selectedId,
            onSelectOpen = onSelectOpen,
            onSelectRecent = onSelectRecent
        )
    }
}

@Composable
private fun ProjectList(
    data: ProjectsData,
    selectedId: String?,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(data.openProjects, key = { it.id }) { project ->
            ProjectListItem(
                projectData = project,
                isSelected = project.id == selectedId,
                onClick = { onSelectOpen(project) }
            )
        }

        if (data.hasRecentProjects) {
            item(key = "header:$RECENT_HEADER") {
                SectionHeader(text = RECENT_HEADER)
            }

            items(data.recentProjects, key = { it.id }) { project ->
                ProjectListItem(
                    projectData = project,
                    isSelected = project.id == selectedId,
                    onClick = { onSelectRecent(project, 0) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, modifier = Modifier.padding(horizontal = 6.dp))
    Spacer(Modifier.height(4.dp))
}

// region Keyboard Navigation

private fun handleKeyEvent(
    event: KeyEvent,
    query: String,
    queryState: TextFieldState,
    allProjects: List<ProjectData>,
    selectedId: String?,
    onClose: () -> Unit,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, Int) -> Unit,
    updateSelectedId: (String?) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.key == Key.F2 && event.isAltPressed) {
        onClose()
        return true
    }

    return when (event.key) {
        Key.Escape -> {
            onClose()
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
            handleEnterKey(event, allProjects, selectedId, onSelectOpen, onSelectRecent)
            true
        }
        Key.Backspace -> {
            handleBackspace(query, queryState)
            true
        }
        else -> handleCharacterInput(event, queryState)
    }
}

private fun moveSelection(allProjects: List<ProjectData>, selectedId: String?, delta: Int): String? {
    if (allProjects.isEmpty()) return null

    val currentIndex = allProjects.indexOfFirst { it.id == selectedId }.let { if (it >= 0) it else 0 }
    val newIndex = (currentIndex + delta).coerceIn(0, allProjects.lastIndex)
    return allProjects[newIndex].id
}

private fun handleEnterKey(
    event: KeyEvent,
    allProjects: List<ProjectData>,
    selectedId: String?,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, Int) -> Unit
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

private fun handleBackspace(query: String, queryState: TextFieldState) {
    if (query.isNotEmpty()) {
        queryState.edit { replace(length - 1, length, "") }
    }
}

private fun handleCharacterInput(event: KeyEvent, queryState: TextFieldState): Boolean {
    // не трогаем модификаторы — пусть хоткеи проходят
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false

    val char = event.utf16CodePoint.toChar()
    return if (char.isDefined() && !char.isISOControl()) {
        queryState.edit { replace(length, length, char.toString()) }
        true
    } else {
        false
    }
}

// endregion
