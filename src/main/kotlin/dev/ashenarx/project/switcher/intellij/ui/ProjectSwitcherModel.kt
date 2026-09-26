package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.ashenarx.project.switcher.intellij.model.Highlights
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.ProjectMatcher
import dev.ashenarx.project.switcher.intellij.model.defaultSelection
import dev.ashenarx.project.switcher.intellij.model.moveSelection
import dev.ashenarx.project.switcher.intellij.model.searchText
import dev.ashenarx.project.switcher.intellij.model.selectionAfterRemoving
import dev.ashenarx.project.switcher.intellij.model.splitHighlights
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.Icon
import kotlin.time.TimeSource

@Stable
internal class ProjectSwitcherModel(
    private val currentProject: Project?,
    private val coroutineScope: CoroutineScope,
    private val actions: ProjectActions = PlatformProjectActions,
) {

    var projects: ProjectList by mutableStateOf(ProjectList.EMPTY)
        private set

    // Separate from projects so each icon arrival does not restart effects keyed on the list.
    val icons: SnapshotStateMap<String, Icon> = mutableStateMapOf()

    var loadState: ProjectLoadState by mutableStateOf(ProjectLoadState.LOADING)
        private set

    var query: String by mutableStateOf("")

    private var choice: Choice? by mutableStateOf(null)

    private var pendingSelection: String? by mutableStateOf(null)

    private var refreshJob: Job? = null

    // Jewel's SpeedSearchState does not expose the matcher it builds, and ranking needs one too.
    private val matcher: ProjectMatcher by derivedStateOf { ProjectMatcher(query) }

    val rows: ProjectList by derivedStateOf {
        if (query.isBlank()) projects else projects.rankedBy(matcher::degreeOrNull)
    }

    val highlights: Map<String, Highlights> by derivedStateOf {
        if (query.isBlank()) {
            emptyMap()
        } else {
            rows.all.associate { it.id to it.splitHighlights(matcher.rangesOrNull(it.searchText).orEmpty()) }
        }
    }

    private val preferred: ProjectItem? by derivedStateOf {
        if (query.isBlank()) null else rows.topMatch(matcher::degreeOrNull)
    }

    // A hand-made choice beats the top match only while the ranking it was made against holds.
    val selectedId: String? by derivedStateOf {
        val ids = rows.all.map { it.id }

        choice?.takeIf { it.ranking == ids && it.instead == preferred?.id }?.id
            ?: preferred?.id
            ?: pendingSelection?.takeIf { it in ids }
            ?: defaultSelection(rows.all)
    }

    val selectedItem: ProjectItem? by derivedStateOf { rows.all.firstOrNull { it.id == selectedId } }

    fun load() {
        coroutineScope.coroutineContext.cancelChildren()
        loadState = ProjectLoadState.LOADING
        icons.clear()

        coroutineScope.launch {
            val started = TimeSource.Monotonic.markNow()

            val loaded = try {
                actions.collect(currentProject)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().warn("Cannot load projects", e)
                onEdt { loadState = ProjectLoadState.ERROR }
                return@launch
            }

            onEdt {
                projects = loaded
                loadState = ProjectLoadState.READY
            }

            thisLogger().debug {
                "Project list shown after ${started.elapsedNow().inWholeMilliseconds} ms: " +
                    "${loaded.open.size} open, ${loaded.recent.size} recent, " +
                    "${loaded.all.count { it.branch != null }} with a branch"
            }

            loadIcons(loaded.all.map { it.path })
        }
    }

    fun refresh() {
        if (loadState != ProjectLoadState.READY) return

        refreshJob?.cancel()
        refreshJob = coroutineScope.launch {
            val loaded = try {
                actions.collect(currentProject)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().debug("Cannot refresh projects", e)
                return@launch
            }

            val withoutIcon = onEdt {
                projects = loaded
                loaded.all.map { it.path }.filter { it !in icons }
            }

            loadIcons(withoutIcon)
        }
    }

    fun select(id: String?) {
        pendingSelection = null
        choice = id?.let { Choice(id = it, ranking = rows.all.map(ProjectItem::id), instead = preferred?.id) }
    }

    fun moveSelection(delta: Int) {
        select(moveSelection(rows.all, selectedId, delta))
    }

    // The current project is returned for the caller to close, and only when it is the last one open.
    fun closeSelected(): ProjectItem.Open? {
        val selected = selectedItem ?: return null

        if (selected.isCurrent) {
            return (selected as? ProjectItem.Open)?.takeIf { projects.open.size == 1 }
        }

        delete(selected)
        return null
    }

    private fun delete(item: ProjectItem) {
        choice = null
        pendingSelection = selectionAfterRemoving(rows.all, item.id)

        when (item) {
            is ProjectItem.Open -> actions.close(item)
            is ProjectItem.Recent -> actions.forget(item.path)
        }

        load()
    }

    private suspend fun loadIcons(paths: List<String>) {
        if (paths.isEmpty()) return

        try {
            actions.loadIcons(paths) { path, icon ->
                onEdt { icons[path] = icon }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().debug("Cannot load project icons", e)
        }
    }

    // any(): the popup blocks the default modality.
    private suspend fun <T> onEdt(block: () -> T): T =
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) { block() }

    private data class Choice(val id: String, val ranking: List<String>, val instead: String?)
}

internal enum class ProjectLoadState {
    LOADING,
    READY,
    ERROR,
}
