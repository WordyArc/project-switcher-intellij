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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.ProjectMatcher
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import dev.ashenarx.project.switcher.intellij.model.searchText
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.rememberSpeedSearchState
import javax.swing.Icon

@Composable
internal fun ProjectSwitcherPopup(
    model: ProjectSwitcherModel,
    onClose: () -> Unit,
    onResult: (SwitchOutcome) -> Unit,
) {
    val searchState = rememberSpeedSearchState { text -> ProjectMatcher(text) }

    // SpeedSearchState only updates searchText while attach is collecting entries.
    val entries = remember { MutableStateFlow<List<String?>>(emptyList()) }
    LaunchedEffect(model.projects) { entries.value = model.projects.all.map { it.searchText } }
    LaunchedEffect(searchState, entries) { searchState.attach(entries) }

    LaunchedEffect(searchState) {
        snapshotFlow { searchState.searchText }.collect { model.query = it }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // The search overlay takes focus, so focus-loss dismissal would immediately hide it.
    SpeedSearchArea(state = searchState, dismissOnLoseFocus = false, modifier = Modifier.fillMaxSize()) {
        val search = remember(this) { asSpeedSearch() }

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
                        search = search,
                        model = model,
                        onClose = onClose,
                        onResult = onResult,
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
                ProjectLoadState.READY -> if (model.rows.isEmpty) {
                    CenteredMessage(ProjectSwitcherBundle.message("popup.empty"))
                } else ProjectRows(
                    data = model.rows,
                    icons = model.icons,
                    selectedId = model.selectedId,
                    duplicateNames = model.projects.duplicateNames,
                    onResult = onResult,
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
    onResult: (SwitchOutcome) -> Unit,
) {
    val listState = rememberLazyListState()

    // Keyed on the row index so a refresh that leaves the selection in place does not re-scroll.
    val selectedRow = data.rowIndexOf(selectedId)
    LaunchedEffect(selectedRow) {
        if (selectedRow >= 0) listState.animateScrollToItem(selectedRow)
    }

    // Reading each icon inside its item scope limits arrivals to one-row recompositions.
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        projectRows(data.open, icons, selectedId, duplicateNames, onResult)

        if (data.hasRecent) {
            item(key = "header:recent") {
                SectionHeader(ProjectSwitcherBundle.message("popup.section.recent"))
            }

            projectRows(data.recent, icons, selectedId, duplicateNames, onResult)
        }
    }
}

private fun LazyListScope.projectRows(
    items: List<ProjectItem>,
    icons: Map<String, Icon>,
    selectedId: String?,
    duplicateNames: Set<String>,
    onResult: (SwitchOutcome) -> Unit,
) {
    items(items, key = { it.id }) { item ->
        ProjectRow(
            item = item,
            icon = icons[item.path],
            isSelected = item.id == selectedId,
            showPath = item.displayName in duplicateNames,
            onClick = { onResult(SwitchOutcome.of(item, OpenTarget.Ask)) },
        )
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

