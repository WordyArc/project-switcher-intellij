package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
class ProjectCatalog(private val coroutineScope: CoroutineScope) {

    @Volatile
    private var last: ProjectList? = null

    fun snapshot(currentProject: Project?): ProjectList? {
        val list = last ?: return null
        val currentHash = currentProject?.takeUnless { it.isDisposed }?.locationHash

        return list.copy(
            open = list.open
                .map { it.copy(isCurrent = it.locationHash == currentHash) }
                .sortedByDescending { it.isCurrent },
        )
    }

    suspend fun collect(currentProject: Project?): ProjectList = withContext(Dispatchers.Default) {
        val actions = recentActionsOrEmpty()
        val localActions = actions
            .filterIsInstance<ReopenProjectAction>()
            .filter { it.projectPath.isNotBlank() }
            .associateBy { it.normalizedPath }

        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        val openPaths = openProjects.mapTo(mutableSetOf()) { pathOf(it) }

        val open = openProjects
            .map { project ->
                val path = pathOf(project)
                val action = localActions[path]

                ActivatedProject(
                    item = ProjectItem.Open(
                        locationHash = project.locationHash,
                        displayName = project.name,
                        path = path,
                        location = presentableProjectPath(path),
                        branch = action?.branchName,
                        isCurrent = project == currentProject,
                    ),
                    activatedAt = action?.activationTimestamp,
                )
            }
            .sortedWith(MOST_RECENTLY_ACTIVATED)
            .map { it.item }

        val recent = localActions
            .filterKeys { it !in openPaths }
            .values
            .map { action ->
                ProjectItem.Recent(
                    displayName = action.projectNameToDisplay,
                    path = action.normalizedPath,
                    location = presentableProjectPath(action.normalizedPath),
                    branch = action.branchName,
                )
            }

        ProjectList(open = open, recent = recent).also { last = it }
    }

    suspend fun missing(paths: Collection<String>): Set<String> {
        val checks = paths
            .filter { it.isLocalProjectPath() }
            .associateWith { path -> coroutineScope.async(PATH_CHECKS) { Files.notExists(Path.of(path)) } }

        withTimeoutOrNull(MISSING_CHECK_TIMEOUT) { checks.values.joinAll() }

        val gone = mutableSetOf<String>()
        for ((path, check) in checks) {
            if (check.isCompleted && check.await()) gone += path
        }
        return gone
    }

    fun forget(path: String) {
        service<RecentProjectsManager>().removePath(path)
    }

    // branchName is null until the platform's background branch cache loads; this topic is the only signal it did.
    fun onRecentProjectsChanged(parent: Disposable, onChange: () -> Unit) {
        ApplicationManager.getApplication().messageBus.connect(parent).subscribe(
            RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC,
            object : RecentProjectsManager.RecentProjectsChange {
                override fun change() = onChange()
            },
        )
    }

    private fun recentActionsOrEmpty(): List<AnAction> =
        try {
            recentProjectActions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("Cannot list recent projects, only the open ones are shown", e)
            emptyList()
        }

    private fun pathOf(project: Project): String =
        (project.basePath ?: project.projectFilePath.orEmpty()).toProjectPath()

    private class ActivatedProject(val item: ProjectItem.Open, val activatedAt: Long?)

    companion object {
        private val MOST_RECENTLY_ACTIVATED = compareByDescending<ActivatedProject> { it.item.isCurrent }
            .thenByDescending { it.activatedAt ?: Long.MIN_VALUE }
            .thenBy { it.item.displayName.lowercase() }

        private val PATH_CHECKS = Dispatchers.IO.limitedParallelism(4)

        private val MISSING_CHECK_TIMEOUT = 1.seconds

        fun getInstance(): ProjectCatalog = service()
    }
}
