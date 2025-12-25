package dev.owlmajin.project.switcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class ProjectSelectionState(
    initialSelectedId: String?
) {
    var selectedId: String? by mutableStateOf(initialSelectedId)
}

@Composable
internal fun rememberProjectSelection(allIds: List<String>): ProjectSelectionState {
    val state = remember { ProjectSelectionState(allIds.firstOrNull()) }

    // Держим selection стабильной при изменении списка (фильтрация / обновление данных)
    LaunchedEffect(allIds) {
        if (state.selectedId !in allIds) {
            state.selectedId = allIds.firstOrNull()
        }
    }

    return state
}
