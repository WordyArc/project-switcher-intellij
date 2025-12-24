package dev.owlmajin.project.switcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Main popup UI for project switcher.
 */
@Composable
fun ProjectSwitcherPopup(
    projectsData: ProjectsData,
    onClose: () -> Unit,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, modifiersEx: Int) -> Unit,
) {
    val queryState = remember { TextFieldState("") }
    val query by remember { derivedStateOf { queryState.text.toString() } }

    val filteredData = remember(projectsData, query) {
        projectsData.filterByQuery(query)
    }

    val flatList = remember(filteredData) {
        buildFlatList(filteredData)
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(flatList.size) {
        selectedIndex = selectedIndex.coerceIn(0, flatList.lastIndex.coerceAtLeast(0))
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
                    selectedIndex = selectedIndex,
                    flatList = flatList,
                    onClose = onClose,
                    onSelectOpen = onSelectOpen,
                    onSelectRecent = onSelectRecent,
                    updateSelectedIndex = { selectedIndex = it }
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
            flatList = flatList,
            selectedIndex = selectedIndex,
            onSelectOpen = onSelectOpen,
            onSelectRecent = onSelectRecent
        )
    }
}

@Composable
private fun ProjectList(
    flatList: List<FlatItem>,
    selectedIndex: Int,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, Int) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(flatList, key = { _, item -> item.key }) { index, item ->
            when (item) {
                is FlatItem.ProjectItem -> {
                    ProjectListItem(
                        projectData = item.data,
                        isSelected = index == selectedIndex,
                        isCurrent = (item.data as? ProjectData.Open)?.isCurrent == true,
                        onClick = {
                            when (val data = item.data) {
                                is ProjectData.Open -> onSelectOpen(data)
                                is ProjectData.Recent -> onSelectRecent(data, 0)
                            }
                        }
                    )
                }
                is FlatItem.Header -> {
                    SectionHeader(text = item.text)
                }
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

// region Flat List

private sealed interface FlatItem {
    val key: String

    data class ProjectItem(val data: ProjectData) : FlatItem {
        override val key: String = when (data) {
            is ProjectData.Open -> "open:${data.project.locationHash}"
            is ProjectData.Recent -> "recent:${data.path}"
        }
    }

    data class Header(val text: String) : FlatItem {
        override val key: String = "header:$text"
    }
}

private fun buildFlatList(projectsData: ProjectsData): List<FlatItem> = buildList {
    projectsData.openProjects.forEach { add(FlatItem.ProjectItem(it)) }

    if (projectsData.hasRecentProjects) {
        add(FlatItem.Header(RECENT_HEADER))
        projectsData.recentProjects.forEach { add(FlatItem.ProjectItem(it)) }
    }
}

// endregion

// region Filtering

private fun ProjectsData.filterByQuery(query: String): ProjectsData {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this

    return ProjectsData(
        openProjects = openProjects.filter { it.matchesQuery(trimmed) },
        recentProjects = recentProjects.filter { it.matchesQuery(trimmed) }
    )
}

private fun ProjectData.matchesQuery(query: String): Boolean {
    return displayName.contains(query, ignoreCase = true) ||
        path?.contains(query, ignoreCase = true) == true
}

// endregion

// region Keyboard Navigation

private fun handleKeyEvent(
    event: KeyEvent,
    query: String,
    queryState: TextFieldState,
    selectedIndex: Int,
    flatList: List<FlatItem>,
    onClose: () -> Unit,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, Int) -> Unit,
    updateSelectedIndex: (Int) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    // Handle Alt+F2 for toggle
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
            updateSelectedIndex(findNextProjectIndex(flatList, selectedIndex, forward = true))
            true
        }
        Key.DirectionUp -> {
            updateSelectedIndex(findNextProjectIndex(flatList, selectedIndex, forward = false))
            true
        }
        Key.Enter -> {
            handleEnterKey(event, flatList, selectedIndex, onSelectOpen, onSelectRecent)
            true
        }
        Key.Backspace -> {
            handleBackspace(query, queryState)
            true
        }
        else -> handleCharacterInput(event, queryState)
    }
}

private fun findNextProjectIndex(flatList: List<FlatItem>, currentIndex: Int, forward: Boolean): Int {
    val range = if (forward) {
        (currentIndex + 1)..flatList.lastIndex
    } else {
        (currentIndex - 1) downTo 0
    }

    return range.firstOrNull { flatList[it] is FlatItem.ProjectItem } ?: currentIndex
}

private fun handleEnterKey(
    event: KeyEvent,
    flatList: List<FlatItem>,
    selectedIndex: Int,
    onSelectOpen: (ProjectData.Open) -> Unit,
    onSelectRecent: (ProjectData.Recent, Int) -> Unit
) {
    val item = flatList.getOrNull(selectedIndex) as? FlatItem.ProjectItem ?: return
    val modifiersEx = event.toModifiersMask()

    when (val data = item.data) {
        is ProjectData.Open -> onSelectOpen(data)
        is ProjectData.Recent -> onSelectRecent(data, modifiersEx)
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
    val char = event.utf16CodePoint.toChar()
    return if (char.isDefined() && !char.isISOControl()) {
        queryState.edit { replace(length, length, char.toString()) }
        true
    } else {
        false
    }
}

// endregion
