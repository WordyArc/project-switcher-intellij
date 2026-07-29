package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ashenarx.project.switcher.intellij.data.ProjectData

internal class ProjectSelectionState(
    initialSelectedId: String?
) {
    var selectedId: String? by mutableStateOf(initialSelectedId)
}

@Composable
internal fun rememberProjectSelection(allProjects: List<ProjectData>): ProjectSelectionState {
    val currentId = remember(allProjects) { allProjects.firstOrNull { it.isCurrent }?.id }
    val firstId = remember(allProjects) { allProjects.firstOrNull()?.id }

    // initial: current -> first -> null
    val state = remember {
        ProjectSelectionState(initialSelectedId = currentId ?: firstId)
    }

    // sync on changes
    LaunchedEffect(allProjects) {
        val ids = allProjects.asSequence().map { it.id }.toSet()
        if (state.selectedId in ids) return@LaunchedEffect

        state.selectedId = currentId ?: firstId
    }

    return state
}
