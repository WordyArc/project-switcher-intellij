package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Service(Service.Level.APP)
class ProjectCatalog {

    suspend fun collect(currentProject: Project?): ProjectList = withContext(Dispatchers.Default) {
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
                    path = action.normalizedPath,
                    branch = action.branchName,
                )
            }

        ProjectList(open = open, recent = recent)
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

    private fun recentActionsByPath(): Map<String, ReopenProjectAction> =
        recentProjectActions()
            .filter { it.projectPath.isNotBlank() }
            .associateBy { it.normalizedPath }

    private fun pathOf(project: Project): String =
        (project.basePath ?: project.projectFilePath.orEmpty()).toProjectPath()

    companion object {
        fun getInstance(): ProjectCatalog = service()
    }
}
