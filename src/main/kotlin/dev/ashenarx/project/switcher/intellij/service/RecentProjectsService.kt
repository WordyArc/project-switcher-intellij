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
                            icon = (action?.iconOrNull() ?: projectIcon(recentManager, path))?.resolved(),
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
                        ProjectItem.Recent(
                            displayName = action.projectNameToDisplay,
                            path = normalize(action.projectPath),
                            branch = action.branchName,
                            icon = action.iconOrNull()?.resolved(),
                        )
                    }
                }
                .awaitAll()

            ProjectList(open = open, recent = recent)
        }
    }

    /** Keyed by normalized path, preserving the platform's most-recently-used order. */
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

    private fun ReopenProjectAction.iconOrNull(): Icon? =
        runCatching { projectIcon }
            .onFailure { thisLogger().debug("Cannot load icon for $projectPath", it) }
            .getOrNull()

    private fun projectIcon(recentManager: RecentProjectsManagerBase, path: String): Icon? {
        if (path.isBlank()) return null
        return runCatching { recentManager.getProjectIcon(path, true, ICON_SIZE) }
            .onFailure { thisLogger().debug("Cannot load icon for $path", it) }
            .getOrNull()
    }

    private fun normalize(path: String) = FileUtil.toSystemIndependentName(path)

    companion object {
        private const val ICON_SIZE = 20
        private val ICON_TIMEOUT = 2.seconds

        fun getInstance(): RecentProjectsService = service()
    }
}
