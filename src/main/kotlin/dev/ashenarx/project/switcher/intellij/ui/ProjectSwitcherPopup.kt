package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.ProjectMatcher
import dev.ashenarx.project.switcher.intellij.model.moveSelection
import dev.ashenarx.project.switcher.intellij.model.searchText
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.SpeedSearchScope
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import javax.swing.Icon

@Composable
internal fun ProjectSwitcherPopup(
    model: ProjectSwitcherModel,
    onClose: () -> Unit,
    onSelectOpen: (ProjectItem.Open) -> Unit,
    onSelectRecent: (ProjectItem.Recent, OpenTarget) -> Unit,
    onCloseCurrent: (ProjectItem.Open) -> Unit,
) {
    val searchState = rememberSpeedSearchState { text -> ProjectMatcher(text) }

    // SpeedSearchState only updates searchText while attach is collecting entries.
    val entries = remember { MutableStateFlow<List<String?>>(emptyList()) }
    LaunchedEffect(model.projects) { entries.value = model.projects.all.map { it.searchText } }
    LaunchedEffect(searchState, entries) { searchState.attach(entries) }

    // SpeedSearchState does not expose its matcher, which ranking also needs.
    val matcher = remember(searchState.searchText) { ProjectMatcher(searchState.searchText) }

    val hasQuery = searchState.searchText.isNotBlank()
    val filtered = if (hasQuery) model.projects.rankedBy(matcher::degreeOrNull) else model.projects
    val preferred = if (hasQuery) filtered.topMatch(matcher::degreeOrNull) else null

    // Reset only when ranking changes, not on every query keystroke.
    LaunchedEffect(filtered, preferred) { model.resetSelection(filtered.all, preferred) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // The search overlay takes focus, so focus-loss dismissal would immediately hide it.
    SpeedSearchArea(state = searchState, dismissOnLoseFocus = false, modifier = Modifier.fillMaxSize()) {
        val searchScope = this

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Dimens.PopupPaddingHorizontal,
                    vertical = Dimens.PopupPaddingVertical,
                )
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    handleKeyEvent(
                        event = event,
                        scope = searchScope,
                        items = filtered.all,
                        model = model,
                        onClose = onClose,
                        onSelectOpen = onSelectOpen,
                        onSelectRecent = onSelectRecent,
                        onCloseCurrent = onCloseCurrent,
                    )
                }
        ) {
            Text(
                text = ProjectSwitcherBundle.message("popup.title"),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Dimens.SectionSpacing))

            when (model.loadState) {
                ProjectLoadState.LOADING -> CenteredMessage(ProjectSwitcherBundle.message("popup.loading"))
                ProjectLoadState.ERROR -> CenteredMessage(ProjectSwitcherBundle.message("popup.error"))
                ProjectLoadState.READY -> if (filtered.isEmpty) {
                    CenteredMessage(ProjectSwitcherBundle.message("popup.empty"))
                } else ProjectRows(
                    data = filtered,
                    icons = model.icons,
                    selectedId = model.selectedId,
                    duplicateNames = model.projects.duplicateNames,
                    onSelectOpen = onSelectOpen,
                    onSelectRecent = onSelectRecent,
                )
            }
        }
    }
}

@Composable
private fun ProjectRows(
    data: ProjectList,
    icons: Map<String, Icon>,
    selectedId: String?,
    duplicateNames: Set<String>,
    onSelectOpen: (ProjectItem.Open) -> Unit,
    onSelectRecent: (ProjectItem.Recent, OpenTarget) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedId, data) {
        val row = data.rowIndexOf(selectedId)
        if (row >= 0) listState.animateScrollToItem(row)
    }

    // Reading each icon inside its item scope limits arrivals to one-row recompositions.
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(data.open, key = { it.id }) { item ->
            ProjectRow(
                item = item,
                icon = icons[item.path],
                isSelected = item.id == selectedId,
                showPath = item.displayName in duplicateNames,
                onClick = { onSelectOpen(item) },
            )
        }

        if (data.hasRecent) {
            item(key = "header:recent") {
                SectionHeader(ProjectSwitcherBundle.message("popup.section.recent"))
            }

            items(data.recent, key = { it.id }) { item ->
                ProjectRow(
                    item = item,
                    icon = icons[item.path],
                    isSelected = item.id == selectedId,
                    showPath = item.displayName in duplicateNames,
                    onClick = { onSelectRecent(item, OpenTarget.Ask) },
                )
            }
        }
    }
}

private fun ProjectList.rowIndexOf(id: String?): Int {
    if (id == null) return -1

    val openIndex = open.indexOfFirst { it.id == id }
    if (openIndex >= 0) return openIndex

    val recentIndex = recent.indexOfFirst { it.id == id }
    if (recentIndex < 0) return -1

    return open.size + 1 + recentIndex
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(Dimens.SectionSpacing))
    Text(text, color = JewelTheme.globalColors.text.disabled, modifier = Modifier.padding(horizontal = 6.dp))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = JewelTheme.globalColors.text.disabled)
    }
}

private fun handleKeyEvent(
    event: KeyEvent,
    scope: SpeedSearchScope,
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

        Key.Delete, Key.Backspace -> {
            if (scope.speedSearchState.searchText.isEmpty()) {
                deleteSelection(items, model, onCloseCurrent)
            } else {
                scope.processKeyEvent(event)
            }
            true
        }

        Key.Escape -> {
            if (!scope.speedSearchState.hideSearch()) onClose()
            true
        }

        else -> scope.processKeyEvent(event)
    }
}

/** Refuses to close the current project when another project is available to switch to. */
private fun deleteSelection(
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

private fun KeyEvent.openTarget(): OpenTarget = when {
    isCtrlPressed -> OpenTarget.NewWindow
    isShiftPressed -> OpenTarget.CurrentWindow
    else -> OpenTarget.Ask
}
