package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ashenarx.project.switcher.intellij.data.ProjectData
import dev.ashenarx.project.switcher.intellij.data.ProjectsData
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

private const val POPUP_TITLE = "Project Switcher"
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

    val selection = rememberProjectSelection(allProjects)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handleProjectSwitcherKeyEvent(
                    event = event,
                    query = query,
                    queryState = queryState,
                    allProjects = allProjects,
                    selectedId = selection.selectedId,
                    onClose = onClose,
                    onSelectOpen = onSelectOpen,
                    onSelectRecent = onSelectRecent,
                    updateSelectedId = { selection.selectedId = it }
                )
            }
    ) {
        Text(
            text = POPUP_TITLE,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(8.dp))

        if (query.isNotBlank()) {
            ProjectSearchBar(
                query = query,
                onClear = { clearQuery(queryState) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(6.dp))
        } else {
            HeaderDivider()
            Spacer(Modifier.size(6.dp))
        }

        ProjectList(
            data = filteredData,
            selectedId = selection.selectedId,
            onSelectOpen = onSelectOpen,
            onSelectRecent = onSelectRecent
        )
    }
}

@Composable
private fun HeaderDivider(modifier: Modifier = Modifier) {
    val c = JewelTheme.globalColors.text.disabled.copy(alpha = 0.20f)

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c)
    )
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
            item(key = "header:$RECENT_HEADER") { SectionHeader(text = RECENT_HEADER) }

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

private fun clearQuery(state: TextFieldState) {
    state.edit { replace(0, length, "") }
}
