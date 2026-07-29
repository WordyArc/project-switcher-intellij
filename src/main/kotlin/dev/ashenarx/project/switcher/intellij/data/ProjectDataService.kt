package dev.ashenarx.project.switcher.intellij.data

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import dev.ashenarx.project.switcher.intellij.util.GitUtils
import javax.swing.Icon

private const val ICON_SIZE = 20

object ProjectDataService {

    /**
     * @param includeBranch if false -> branch is always null (fast path, no FS reads)
     * @param includeIcon   if false -> icon is always null (fast path, avoids any platform icon resolution work)
     */
    fun collectProjectsData(
        currentProject: Project?,
        includeBranch: Boolean = true,
        includeIcon: Boolean = true
    ): ProjectsData {
        val openProjects = collectOpenProjects(currentProject, includeBranch, includeIcon)
        val recentProjects = collectRecentProjects(openProjects, includeBranch, includeIcon)

        return ProjectsData(
            openProjects = openProjects,
            recentProjects = recentProjects
        )
    }

    private fun collectOpenProjects(
        currentProject: Project?,
        includeBranch: Boolean,
        includeIcon: Boolean
    ): List<ProjectData.Open> {
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
                    branch = if (includeBranch) GitUtils.getCurrentBranch(path) else null,
                    icon = if (includeIcon) loadProjectIcon(recentManager, path) else null
                )
            }
            .toList()
    }

    private fun collectRecentProjects(
        openProjects: List<ProjectData.Open>,
        includeBranch: Boolean,
        includeIcon: Boolean
    ): List<ProjectData.Recent> {
        val openPaths = openProjects
            .asSequence()
            .mapNotNull { it.path }
            .map { FileUtil.toSystemIndependentName(it) }
            .toSet()

        val recentManager = RecentProjectsManagerBase.getInstanceEx()

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
                    branch = if (includeBranch) GitUtils.getCurrentBranch(action.projectPath) else null,
                    icon = if (includeIcon) loadProjectIcon(recentManager, action.projectPath) else null
                )
            }
            .toList()
    }

    private fun loadProjectIcon(recentManager: RecentProjectsManagerBase, path: String?): Icon? {
        if (path.isNullOrBlank()) return null
        return try {
            recentManager.getProjectIcon(path, true, ICON_SIZE)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
}
