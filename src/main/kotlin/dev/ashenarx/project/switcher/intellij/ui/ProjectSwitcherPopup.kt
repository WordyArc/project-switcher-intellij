package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.openapi.keymap.KeymapUtil
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import dev.ashenarx.project.switcher.intellij.model.Highlights
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import javax.swing.KeyStroke
import kotlin.time.Duration.Companion.milliseconds

private val LOADING_MESSAGE_DELAY = 150.milliseconds

private const val HINT_SEPARATOR = "   "

@Composable
internal fun ProjectSwitcherPopup(
    model: ProjectSwitcherModel,
    toggleShortcuts: List<KeyStroke>,
    onClose: () -> Unit,
    onResult: (SwitchOutcome) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val keys = remember(toggleShortcuts, listState) {
        PopupKeys(toggle = toggleShortcuts, pageSize = { listState.pageSize() })
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Dimens.PopupPaddingHorizontal,
                vertical = Dimens.PopupPaddingVertical,
            )
            .onPreviewKeyEvent { event -> handleKeyEvent(event, model, keys, onClose, onResult) }
    ) {
        TextField(
            state = model.queryState,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(ProjectSwitcherBundle.message("popup.search.placeholder")) },
        )

        Spacer(Modifier.height(Dimens.SectionSpacing))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (model.loadState) {
                ProjectLoadState.LOADING -> LoadingMessage()
                ProjectLoadState.ERROR -> CenteredMessage(ProjectSwitcherBundle.message("popup.error"))
                ProjectLoadState.READY -> if (model.rows.isEmpty) {
                    CenteredMessage(ProjectSwitcherBundle.message("popup.empty"))
                } else {
                    ProjectRows(model = model, listState = listState, onResult = onResult)
                }
            }
        }

        Footer(model.selectedItem)
    }
}

@Composable
private fun ProjectRows(
    model: ProjectSwitcherModel,
    listState: LazyListState,
    onResult: (SwitchOutcome) -> Unit,
) {
    val data = model.rows
    val selectedId = model.selectedId
    val selectedRow = data.rowIndexOf(selectedId)

    LaunchedEffect(selectedRow, data) {
        if (selectedRow < 0) return@LaunchedEffect

        withFrameNanos { }
        listState.reveal(first = data.revealStart(selectedRow), last = selectedRow)
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        model.reportShown()
    }

    val rows = RowContext(
        icons = model.icons,
        highlights = model.highlights,
        missing = model.missing,
        selectedId = selectedId,
        duplicateNames = model.projects.duplicateNames,
        onResult = onResult,
    )

    VerticallyScrollableContainer(scrollState = listState, modifier = Modifier.fillMaxSize()) {
        // Reading each icon inside its item scope limits arrivals to one-row recompositions.
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            projectRows(data.open, rows)

            if (data.hasRecent) {
                item(key = "header:recent") {
                    SectionHeader(ProjectSwitcherBundle.message("popup.section.recent"))
                }

                projectRows(data.recent, rows)
            }
        }
    }
}

private class RowContext(
    val icons: Map<String, ImageBitmap>,
    val highlights: Map<String, Highlights>,
    val missing: Set<String>,
    val selectedId: String?,
    val duplicateNames: Set<String>,
    val onResult: (SwitchOutcome) -> Unit,
)

private fun LazyListScope.projectRows(items: List<ProjectItem>, context: RowContext) {
    items(items, key = { it.id }) { item ->
        val highlights = context.highlights[item.id] ?: Highlights.NONE

        ProjectRow(
            item = item,
            icon = context.icons[item.path],
            highlights = highlights,
            isSelected = item.id == context.selectedId,
            isMissing = item is ProjectItem.Recent && item.path in context.missing,
            showLocation = item.displayName in context.duplicateNames || !highlights.isNameOnly,
            onClick = { target -> context.onResult(SwitchOutcome.of(item, target)) },
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

private fun ProjectList.revealStart(row: Int): Int =
    if (hasRecent && row == open.size + 1) open.size else row

@Composable
private fun Footer(item: ProjectItem?) {
    val secondary = JewelTheme.globalColors.text.disabled
    val hints = remember(item) {
        hintsFor(item).joinToString(HINT_SEPARATOR) { hint ->
            "${KeymapUtil.getKeystrokeText(hint.stroke)} ${ProjectSwitcherBundle.message(hint.labelKey)}"
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.SectionSpacing, start = 6.dp, end = 6.dp)) {
        Text(item?.location.orEmpty(), color = secondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Text(hints, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(Dimens.SectionSpacing))
    Text(text, color = JewelTheme.globalColors.text.disabled, modifier = Modifier.padding(horizontal = 6.dp))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun LoadingMessage() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_MESSAGE_DELAY)
        visible = true
    }

    if (visible) CenteredMessage(ProjectSwitcherBundle.message("popup.loading"))
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = JewelTheme.globalColors.text.disabled)
    }
}
