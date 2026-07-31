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

    /** Streams cached-size icons first, then replaces them with HiDPI versions as they load. */
    suspend fun loadIcons(paths: List<String>, emit: suspend (String, Icon) -> Unit) {
        // getProjectIcon is only on the base class, but an IDE may replace the open service with an
        // unrelated implementation. Keep the cast safe instead of using getInstanceEx().
        val recentManager = service<RecentProjectsManager>() as? RecentProjectsManagerBase
        if (recentManager == null) {
            thisLogger().debug("RecentProjectsManager is not a RecentProjectsManagerBase — no project icons")
            return
        }

        for (size in iconSizePasses(JBUIScale.sysScale())) {
            // Finish the coarse pass before a later, crisp icon can be emitted.
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
     * Deferred project icons normally start loading when Swing paints them. Compose rasterization
     * bypasses that trigger, so evaluate first and return the settled delegate. The timeout prevents
     * one icon from blocking the next size pass.
     */
    @Suppress("UnstableApiUsage")
    private suspend fun Icon.resolved(): Icon {
        if (this !is DeferredIconImpl<*>) return this

        try {
            withTimeoutOrNull(ICON_TIMEOUT) { awaitEvaluation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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

        private const val MAX_RASTER_SCALE = 3

        private val ICON_TIMEOUT = 2.seconds

        /**
         * Starts with the platform's cacheable 20 px size, then requests enough source pixels for
         * HiDPI rasterization. [ReopenProjectAction.projectIcon] cannot provide the larger size.
         */
        internal fun iconSizePasses(scale: Float): List<Int> {
            val crisp = ICON_SIZE * ceil(scale).toInt().coerceIn(1, MAX_RASTER_SCALE)
            return if (crisp == ICON_SIZE) listOf(ICON_SIZE) else listOf(ICON_SIZE, crisp)
        }

        fun getInstance(): RecentProjectsService = service()
    }
}
