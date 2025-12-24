package dev.owlmajin.project.switcher.data

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import dev.owlmajin.project.switcher.util.GitUtils
import javax.swing.Icon

private const val ICON_SIZE = 20

/**
 * Service responsible for collecting project data for the switcher popup.
 *
 * Collects:
 * - Open projects: currently loaded in the IDE
 * - Recent projects: previously opened, filtered to exclude currently open ones
 */
object ProjectDataService {

    /**
     * Collects all project data for display in the switcher.
     *
     * @param currentProject The currently active project (may be null).
     * @return ProjectsData containing open and recent projects.
     */
    fun collectProjectsData(currentProject: Project?): ProjectsData {
        val openProjects = collectOpenProjects(currentProject)
        val recentProjects = collectRecentProjects(openProjects)

        return ProjectsData(
            openProjects = openProjects,
            recentProjects = recentProjects
        )
    }

    private fun collectOpenProjects(currentProject: Project?): List<ProjectData.Open> {
        val recentManager = RecentProjectsManagerBase.getInstanceEx()

        return ProjectManager.getInstance().openProjects
            .asSequence()
            .filter { !it.isDisposed }
            .sortedBy { it.name.lowercase() }
            .map { project ->
                val path = project.basePath ?: project.projectFilePath
                ProjectData.Open(
                    project = project,
                    isCurrent = project == currentProject,
                    branch = GitUtils.getCurrentBranch(path),
                    icon = loadProjectIcon(recentManager, path)
                )
            }
            .toList()
    }

    private fun collectRecentProjects(openProjects: List<ProjectData.Open>): List<ProjectData.Recent> {
        val openPaths = openProjects
            .asSequence()
            .mapNotNull { it.path }
            .map { FileUtil.toSystemIndependentName(it) }
            .toSet()

        val recentManager = RecentProjectsManagerBase.getInstanceEx()

        // Actions are already sorted by last opened time (most recent first)
        return recentManager.getRecentProjectsActions(false)
            .asSequence()
            .filterIsInstance<ReopenProjectAction>()
            .filter { action ->
                action.projectPath.isNotBlank() &&
                    FileUtil.toSystemIndependentName(action.projectPath) !in openPaths
            }
            .map { action ->
                ProjectData.Recent(
                    action = action,
                    branch = GitUtils.getCurrentBranch(action.projectPath),
                    icon = loadProjectIcon(recentManager, action.projectPath)
                )
            }
            .toList()
    }

    private fun loadProjectIcon(recentManager: RecentProjectsManagerBase, path: String?): Icon? {
        if (path.isNullOrBlank()) return null
        return try {
            recentManager.getProjectIcon(path, true, ICON_SIZE)
        } catch (_: Exception) {
            null
        }
    }
}
