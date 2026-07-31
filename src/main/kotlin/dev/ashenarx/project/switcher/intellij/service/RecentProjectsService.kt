package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.scale.JBUIScale
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.Icon
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.APP)
class RecentProjectsService(val coroutineScope: CoroutineScope) {

    /**
     * Everything the popup needs to render a usable, searchable list, and nothing that costs I/O —
     * the platform answers all of this from memory. Icons are the one exception, which is why they
     * are [loadIcons]' job and not this one.
     */
    suspend fun collect(currentProject: Project?): ProjectList = withContext(Dispatchers.IO) {
        val recentActions = recentActionsByPath()

        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        val openPaths = openProjects.mapTo(mutableSetOf()) { pathOf(it) }

        val open = openProjects
            .map { project ->
                val path = pathOf(project)

                ProjectItem.Open(
                    locationHash = project.locationHash,
                    displayName = project.name,
                    path = path,
                    branch = recentActions[path]?.branchName,
                    isCurrent = project == currentProject,
                )
            }
            .sortedBy { it.displayName.lowercase() }

        val recent = recentActions
            .filterKeys { it !in openPaths }
            .values
            .map { action ->
                ProjectItem.Recent(
                    displayName = action.projectNameToDisplay,
                    path = normalize(action.projectPath),
                    branch = action.branchName,
                )
            }

        ProjectList(open = open, recent = recent)
    }

    /**
     * Streams icons to [emit] keyed by path, rather than returning them together: one icon can cost
     * hundreds of milliseconds of disk I/O, and holding the list back for the slowest of them is
     * what made this popup open on "Loading…" instead of on the list.
     *
     * The first pass asks for [ICON_SIZE] because it is the only size the platform itself ever
     * requests, and its cache is keyed by `(path, size)` — a request at any other size cannot hit an
     * entry the welcome screen or the project widget already warmed. The second pass asks for the
     * HiDPI size this popup wants, which is always cold.
     */
    suspend fun loadIcons(paths: List<String>, emit: suspend (String, Icon) -> Unit) {
        // Not `getInstanceEx()`, which the inspection would prefer: it hides this very downcast
        // inside the platform, as a hard cast, where it cannot degrade.
        //
        // The downcast is unavoidable — `getProjectIcon` lives on the base class, not on the service
        // interface — but it stays a *safe* cast because the service is registered `open="true"`, so
        // an IDE may swap in an implementation that does not extend it. Rows without icons beat a
        // ClassCastException that takes the whole popup down.
        val recentManager = service<RecentProjectsManager>() as? RecentProjectsManagerBase
        if (recentManager == null) {
            thisLogger().debug("RecentProjectsManager is not a RecentProjectsManagerBase — no project icons")
            return
        }

        for (size in iconSizePasses(JBUIScale.sysScale())) {
            // Scoped per pass, not around the loop: a later emit wins, so the coarse icon must never
            // be in flight once the crisp one has landed.
            coroutineScope {
                for (path in paths) {
                    launch {
                        val icon = projectIcon(recentManager, path, size)?.resolved() ?: return@launch
                        emit(path, icon)
                    }
                }
            }
        }
    }

    /**
     * Drops [path] from the platform's recent-projects history. Only the history entry goes; the
     * project on disk is untouched, which is why this needs no confirmation of its own.
     */
    fun forget(path: String) {
        service<RecentProjectsManager>().removePath(path)
    }

    /** [LinkedHashMap] so the platform's most-recently-used order survives the keying. */
    private fun recentActionsByPath(): Map<String, ReopenProjectAction> {
        return RecentProjectListActionProvider.getInstance()
            .getActions()
            .asSequence()
            .filterIsInstance<ReopenProjectAction>()
            .filter { it.projectPath.isNotBlank() }
            .associateByTo(LinkedHashMap()) { normalize(it.projectPath) }
    }

    /**
     * The platform hands out project icons as [DeferredIconImpl]: a blank placeholder plus a
     * background load that only starts the first time Swing paints the icon. This popup rasterizes
     * icons into Compose bitmaps instead of painting them, which breaks that contract twice over —
     * the load is never triggered, and `IconLoader.toImage` unwraps the deferred icon off the EDT,
     * where it falls back to the *synchronous* evaluator that project icons do not have. Either way
     * a blank square is what gets captured.
     *
     * Hence the up-front evaluation, and hence handing on the settled delegate rather than the
     * wrapper. The timeout keeps a wedged load from stalling the pass that follows it.
     */
    @Suppress("UnstableApiUsage")
    private suspend fun Icon.resolved(): Icon {
        if (this !is DeferredIconImpl<*>) return this

        try {
            withTimeoutOrNull(ICON_TIMEOUT) { awaitEvaluation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The row is already on screen; it keeps its blank slot rather than losing the popup.
            thisLogger().debug("Cannot evaluate deferred project icon", e)
        }
        return currentlyPaintedIcon()
    }

    private fun pathOf(project: Project): String =
        normalize(project.basePath ?: project.projectFilePath.orEmpty())

    private fun projectIcon(recentManager: RecentProjectsManagerBase, path: String, size: Int): Icon? {
        if (path.isBlank()) return null
        return runCatching { recentManager.getProjectIcon(path, true, size) }
            .onFailure { thisLogger().debug("Cannot load icon for $path", it) }
            .getOrNull()
    }

    private fun normalize(path: String) = FileUtil.toSystemIndependentName(path)

    companion object {
        private const val ICON_SIZE = 20

        /** Past 3x the extra bitmap stops buying visible sharpness and only costs memory. */
        private const val MAX_RASTER_SCALE = 3

        private val ICON_TIMEOUT = 2.seconds

        /**
         * The icon sizes to request for a screen at [scale], in the order they must be applied.
         *
         * Rows are [ICON_SIZE] wide, but this popup rasterizes icons into a fixed bitmap instead of
         * painting them, so pixel density has to be baked into the requested size — a 20 px bitmap
         * stretched into a 20.dp slot is visibly soft on a HiDPI screen. Asking the platform for a
         * proportionally larger *logical* icon is the only lever, since the size it rasterizes at is
         * settled before this plugin sees the icon. (It is also why [ReopenProjectAction.projectIcon]
         * is not used, convenient as it is — it hardcodes 20.)
         */
        internal fun iconSizePasses(scale: Float): List<Int> {
            val crisp = ICON_SIZE * ceil(scale).toInt().coerceIn(1, MAX_RASTER_SCALE)
            return if (crisp == ICON_SIZE) listOf(ICON_SIZE) else listOf(ICON_SIZE, crisp)
        }

        fun getInstance(): RecentProjectsService = service()
    }
}
