package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import dev.ashenarx.project.switcher.intellij.model.Choice
import dev.ashenarx.project.switcher.intellij.model.Highlights
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.ProjectMatcher
import dev.ashenarx.project.switcher.intellij.model.Ranking
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import dev.ashenarx.project.switcher.intellij.model.moveSelection
import dev.ashenarx.project.switcher.intellij.model.pageSelection
import dev.ashenarx.project.switcher.intellij.model.resolveSelection
import dev.ashenarx.project.switcher.intellij.model.selectionAfterRemoving
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

@Stable
internal class ProjectSwitcherModel(
    private val coroutineScope: CoroutineScope,
    private val actions: ProjectActions,
    // any(): the popup blocks the default modality.
    private val uiContext: CoroutineContext = Dispatchers.EDT + ModalityState.any().asContextElement(),
) {

    private val created = TimeSource.Monotonic.markNow()

    val queryState = TextFieldState()

    val query: String by derivedStateOf { queryState.text.toString() }

    var projects: ProjectList by mutableStateOf(ProjectList.EMPTY)
        private set

    // Separate from projects so each icon arrival does not restart effects keyed on the list.
    val icons: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()

    var missing: Set<String> by mutableStateOf(emptySet())
        private set

    var loadState: ProjectLoadState by mutableStateOf(ProjectLoadState.LOADING)
        private set

    private var choice: Choice? by mutableStateOf(null)

    private var refreshJob: Job? = null

    private var shownReported = false

    private val ranking: Ranking? by derivedStateOf {
        val text = query
        if (text.isBlank()) null else projects.rankedBy(ProjectMatcher(text)::match)
    }

    val rows: ProjectList by derivedStateOf { ranking?.rows ?: projects }

    val highlights: Map<String, Highlights> by derivedStateOf { ranking?.highlights.orEmpty() }

    val selectedId: String? by derivedStateOf { resolveSelection(rows.all, query, choice, ranking?.top) }

    val selectedItem: ProjectItem? by derivedStateOf { rows.all.firstOrNull { it.id == selectedId } }

    fun load() {
        coroutineScope.coroutineContext.cancelChildren()

        val snapshot = actions.snapshot()
        if (snapshot != null) {
            projects = snapshot
            icons.putAll(actions.cachedIcons(snapshot.all.map { it.path }))
            loadState = ProjectLoadState.READY
        } else {
            loadState = ProjectLoadState.LOADING
        }

        coroutineScope.launch {
            val loaded = try {
                actions.collect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().warn("Cannot load projects", e)
                if (snapshot == null) onUi { loadState = ProjectLoadState.ERROR }
                return@launch
            }

            onUi {
                projects = loaded
                loadState = ProjectLoadState.READY
            }

            thisLogger().debug {
                "Project list collected ${created.elapsedNow().inWholeMilliseconds} ms after the popup was requested: " +
                    "${loaded.open.size} open, ${loaded.recent.size} recent, " +
                    "${loaded.all.count { it.branch != null }} with a branch"
            }

            launch { loadIcons(loaded.all) }
            checkMissing(loaded)
        }
    }

    fun refresh() {
        if (loadState != ProjectLoadState.READY) return

        refreshJob?.cancel()
        refreshJob = coroutineScope.launch {
            val loaded = try {
                actions.collect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().debug("Cannot refresh projects", e)
                return@launch
            }

            val withoutIcon = onUi {
                projects = loaded
                loaded.all.filter { it.path !in icons }
            }

            launch { loadIcons(withoutIcon) }
            checkMissing(loaded)
        }
    }

    fun select(id: String?) {
        choice = id?.let { Choice(id = it, query = query) }
    }

    fun moveSelection(delta: Int) {
        select(moveSelection(rows.all, selectedId, delta))
    }

    fun moveSelectionByPage(delta: Int) {
        select(pageSelection(rows.all, selectedId, delta))
    }

    fun selectFirst() {
        select(rows.all.firstOrNull()?.id)
    }

    fun selectLast() {
        select(rows.all.lastOrNull()?.id)
    }

    fun clearQuery(): Boolean {
        if (queryState.text.isEmpty()) return false

        queryState.clearText()
        return true
    }

    fun closeSelected(): SwitchOutcome.CloseCurrent? {
        val selected = selectedItem ?: return null

        if (selected is ProjectItem.Open && selected.isCurrent) {
            return SwitchOutcome.CloseCurrent(selected, next = projects.open.firstOrNull { !it.isCurrent })
        }

        remove(selected)
        return null
    }

    fun reportShown() {
        if (shownReported) return
        shownReported = true

        thisLogger().debug { "Project list shown ${created.elapsedNow().inWholeMilliseconds} ms after the popup was requested" }
    }

    private fun remove(item: ProjectItem) {
        when (item) {
            is ProjectItem.Open -> actions.close(item)
            is ProjectItem.Recent -> actions.forget(item.path)
        }

        val neighbour = selectionAfterRemoving(rows.all, item.id)
        projects = projects.without(item)
        select(neighbour)
        refresh()
    }

    private suspend fun loadIcons(items: List<ProjectItem>) {
        if (items.isEmpty()) return

        try {
            actions.loadIcons(items) { key, icon ->
                onUi { icons[key] = icon }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().debug("Cannot load project icons", e)
        }
    }

    private suspend fun checkMissing(list: ProjectList) {
        val paths = list.recent.map { it.path }
        if (paths.isEmpty()) return

        val gone = try {
            actions.missing(paths)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().debug("Cannot check which recent projects are gone", e)
            return
        }

        onUi { missing = gone }
    }

    private suspend fun <T> onUi(block: () -> T): T = withContext(uiContext) { block() }
}

internal enum class ProjectLoadState {
    LOADING,
    READY,
    ERROR,
}
