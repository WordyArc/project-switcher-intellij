package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.RecentProjectListActionProvider
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.Icon
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.APP)
class RecentProjectsService(val coroutineScope: CoroutineScope) {

    suspend fun collect(currentProject: Project?): ProjectList = withContext(Dispatchers.IO) {
        val recentActions = recentActionsByPath()
        val recentManager = RecentProjectsManagerBase.getInstanceEx()

        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        val openPaths = openProjects.mapTo(mutableSetOf()) { pathOf(it) }

        // Every row waits on its own background icon load, so they are built concurrently — otherwise
        // the popup's "Loading…" lasts as long as the sum of them.
        coroutineScope {
            val open = openProjects
                .map { project ->
                    async {
                        val path = pathOf(project)
                        val action = recentActions[path]

                        ProjectItem.Open(
                            locationHash = project.locationHash,
                            displayName = project.name,
                            path = path,
                            branch = action?.branchName,
                            icon = projectIcon(recentManager, path)?.resolved(),
                            isCurrent = project == currentProject,
                        )
                    }
                }
                .awaitAll()
                .sortedBy { it.displayName.lowercase() }

            val recent = recentActions
                .filterKeys { it !in openPaths }
                .values
                .map { action ->
                    async {
                        val path = normalize(action.projectPath)

                        ProjectItem.Recent(
                            displayName = action.projectNameToDisplay,
                            path = path,
                            branch = action.branchName,
                            icon = projectIcon(recentManager, path)?.resolved(),
                        )
                    }
                }
                .awaitAll()

            ProjectList(open = open, recent = recent)
        }
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
     * wrapper. The timeout keeps a wedged load from holding up the rest of the list.
     */
    @Suppress("UnstableApiUsage")
    private suspend fun Icon.resolved(): Icon {
        if (this !is DeferredIconImpl<*>) return this

        try {
            withTimeoutOrNull(ICON_TIMEOUT) { awaitEvaluation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A row without an icon beats a popup stuck on "Loading…".
            thisLogger().debug("Cannot evaluate deferred project icon", e)
        }
        return currentlyPaintedIcon()
    }

    private fun pathOf(project: Project): String =
        normalize(project.basePath ?: project.projectFilePath.orEmpty())

    private fun projectIcon(recentManager: RecentProjectsManagerBase, path: String): Icon? {
        if (path.isBlank()) return null
        return runCatching { recentManager.getProjectIcon(path, true, rasterIconSize()) }
            .onFailure { thisLogger().debug("Cannot load icon for $path", it) }
            .getOrNull()
    }

    /**
     * Rows are [ICON_SIZE] wide, but the icon is rasterized into a fixed bitmap instead of painted,
     * so the pixel density has to be baked in here — a 20 px bitmap stretched into a 20.dp slot is
     * visibly soft on a HiDPI screen. Asking the platform for a proportionally larger *logical* icon
     * is the only lever: the size it rasterizes at is settled before this plugin sees the icon.
     *
     * This is also why [ReopenProjectAction.projectIcon] is not used, convenient as it is — it
     * hardcodes 20. The cache underneath is keyed by path *and* size, so the larger request gets its
     * own entry rather than displacing the one the platform's own project widget relies on.
     */
    private fun rasterIconSize(): Int = ICON_SIZE * ceil(JBUIScale.sysScale()).toInt().coerceIn(1, MAX_RASTER_SCALE)

    private fun normalize(path: String) = FileUtil.toSystemIndependentName(path)

    companion object {
        private const val ICON_SIZE = 20

        /** Past 3x the extra bitmap stops buying visible sharpness and only costs memory. */
        private const val MAX_RASTER_SCALE = 3

        private val ICON_TIMEOUT = 2.seconds

        fun getInstance(): RecentProjectsService = service()
    }
}
