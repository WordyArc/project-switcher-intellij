package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.defaultSelection
import dev.ashenarx.project.switcher.intellij.service.RecentProjectsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plain Compose state rather than an `androidx.lifecycle.ViewModel`: the platform's Compose bundle
 * ships lifecycle-runtime but not lifecycle-viewmodel, and a [com.intellij.openapi.ui.popup.JBPopup]
 * has no `ViewModelStoreOwner` to scope one to anyway. The scope comes from an application service,
 * so loading is cancelled with the plugin rather than leaking a pooled thread.
 */
@Stable
internal class ProjectSwitcherModel(private val currentProject: Project?) {

    var projects: ProjectList by mutableStateOf(ProjectList.EMPTY)
        private set

    var isLoading: Boolean by mutableStateOf(true)
        private set

    var selectedId: String? by mutableStateOf(null)

    fun load() {
        val service = RecentProjectsService.getInstance()

        service.coroutineScope.launch {
            val loaded = service.collect(currentProject)

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                projects = loaded
                isLoading = false
            }
        }
    }

    /**
     * Re-points the selection at whatever the visible list now means. [preferred] carries the search
     * query's best match; without a query there is none, and the current project wins instead — the
     * right answer for an unfiltered list, but a trap for a filtered one, since re-selecting the
     * project you are already in makes Enter a no-op.
     *
     * Callers must invoke this only when the list or the preference actually changed, so that arrow
     * keys keep their selection in between.
     */
    fun resetSelection(visible: List<ProjectItem>, preferred: ProjectItem?) {
        selectedId = preferred?.id ?: defaultSelection(visible)
    }
}
