package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.defaultSelection
import dev.ashenarx.project.switcher.intellij.model.selectionAfterRemoving
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import dev.ashenarx.project.switcher.intellij.service.RecentProjectsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.Icon

/** Compose state scoped explicitly because a JBPopup has no ViewModelStoreOwner. */
@Stable
internal class ProjectSwitcherModel(
    private val currentProject: Project?,
    private val coroutineScope: CoroutineScope,
) {

    var projects: ProjectList by mutableStateOf(ProjectList.EMPTY)
        private set

    // Keeping icons separate prevents each arrival from resetting effects keyed on the project list.
    val icons: SnapshotStateMap<String, Icon> = mutableStateMapOf()

    var loadState: ProjectLoadState by mutableStateOf(ProjectLoadState.LOADING)
        private set

    var selectedId: String? by mutableStateOf(null)

    private var pendingSelection: String? = null

    fun load() {
        val service = RecentProjectsService.getInstance()

        coroutineScope.coroutineContext.cancelChildren()
        loadState = ProjectLoadState.LOADING
        icons.clear()

        coroutineScope.launch {
            val loaded = try {
                service.collect(currentProject)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().warn("Cannot load projects", e)
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    loadState = ProjectLoadState.ERROR
                }
                return@launch
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                projects = loaded
                loadState = ProjectLoadState.READY
            }

            try {
                service.loadIcons(loaded.all.map { it.path }) { path, icon ->
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        icons[path] = icon
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().debug("Cannot load project icons", e)
            }
        }
    }

    /** Reloads platform state because a closed project moves into the recent section. */
    fun delete(item: ProjectItem, visible: List<ProjectItem>) {
        pendingSelection = selectionAfterRemoving(visible, item.id)

        when (item) {
            is ProjectItem.Open -> ProjectOpener.getInstance().close(item)
            is ProjectItem.Recent -> RecentProjectsService.getInstance().forget(item.path)
        }

        load()
    }

    /** Call only when list content or [preferred] changes, so arrow-key selection is not overwritten. */
    fun resetSelection(visible: List<ProjectItem>, preferred: ProjectItem?) {
        val pending = pendingSelection?.also { pendingSelection = null }

        selectedId = preferred?.id
            ?: pending?.takeIf { id -> visible.any { it.id == id } }
            ?: defaultSelection(visible)
    }
}

internal enum class ProjectLoadState {
    LOADING,
    READY,
    ERROR,
}
