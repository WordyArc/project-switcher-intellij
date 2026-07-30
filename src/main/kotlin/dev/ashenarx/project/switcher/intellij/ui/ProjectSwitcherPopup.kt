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

@Composable
internal fun ProjectSwitcherPopup(
    model: ProjectSwitcherModel,
    onClose: () -> Unit,
    onSelectOpen: (ProjectItem.Open) -> Unit,
    onSelectRecent: (ProjectItem.Recent, OpenTarget) -> Unit,
) {
    val searchState = rememberSpeedSearchState { text -> ProjectMatcher(text) }

    // A SpeedSearchState computes matches — and publishes `searchText` at all — only while `attach`
    // is collecting. Detached, it reports a permanently empty query.
    val entries = remember { MutableStateFlow<List<String?>>(emptyList()) }
    LaunchedEffect(model.projects) { entries.value = model.projects.all.map { it.searchText } }
    LaunchedEffect(searchState, entries) { searchState.attach(entries) }

    // Ranking needs the matcher itself, which the state keeps to itself, so it is rebuilt from the
    // query here. Constructing one only precomputes per-character tables over the pattern.
    val matcher = remember(searchState.searchText) { ProjectMatcher(searchState.searchText) }

    val filtered = if (searchState.searchText.isBlank()) {
        model.projects
    } else {
        model.projects.rankedBy(matcher::degreeOrNull)
    }

    LaunchedEffect(filtered) { model.ensureSelectionVisible(filtered.all) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // The query overlay is a child popup that takes focus itself, so the built-in dismiss-on-focus-loss
    // rule would hide it the moment it opens; Escape and the enclosing JBPopup dismiss instead.
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
                    )
                }
        ) {
            Text(
                text = ProjectSwitcherBundle.message("popup.title"),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Dimens.SectionSpacing))

            when {
                model.isLoading -> CenteredMessage(ProjectSwitcherBundle.message("popup.loading"))
                filtered.isEmpty -> CenteredMessage(ProjectSwitcherBundle.message("popup.empty"))
                else -> ProjectRows(
                    data = filtered,
                    selectedId = model.selectedId,
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
    selectedId: String?,
    onSelectOpen: (ProjectItem.Open) -> Unit,
    onSelectRecent: (ProjectItem.Recent, OpenTarget) -> Unit,
) {
    val listState = rememberLazyListState()

    // Without this a selection moved by the arrow keys silently scrolls out of view.
    LaunchedEffect(selectedId, data) {
        val row = data.rowIndexOf(selectedId)
        if (row >= 0) listState.animateScrollToItem(row)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(data.open, key = { it.id }) { item ->
            ProjectRow(item, isSelected = item.id == selectedId, onClick = { onSelectOpen(item) })
        }

        if (data.hasRecent) {
            item(key = "header:recent") {
                SectionHeader(ProjectSwitcherBundle.message("popup.section.recent"))
            }

            items(data.recent, key = { it.id }) { item ->
                ProjectRow(item, isSelected = item.id == selectedId, onClick = { onSelectRecent(item, OpenTarget.Ask) })
            }
        }
    }
}

/** Lazy-list index of [id] — the recent block is offset by one for the section header row. */
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

        // hideSearch() reports whether there was a query to dismiss; only then is the popup kept.
        Key.Escape -> {
            if (!scope.speedSearchState.hideSearch()) onClose()
            true
        }

        else -> scope.processKeyEvent(event)
    }
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

/** A bare Enter states no preference, which leaves the frame to the "Open project in" setting. */
private fun KeyEvent.openTarget(): OpenTarget = when {
    isCtrlPressed -> OpenTarget.NewWindow
    isShiftPressed -> OpenTarget.CurrentWindow
    else -> OpenTarget.Ask
}
