package dev.owlmajin.project.switcher

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

sealed class ProjectItem {
    data class Open(val project: Project) : ProjectItem()
    data class Recent(val name: String, val path: String) : ProjectItem()
}

private fun collectItems(currentProject: Project): List<ProjectItem> {
    val open = ProjectManager.getInstance().openProjects
        .filter { !it.isDisposed }
        .sortedBy { it.name.lowercase() }
        .map { ProjectItem.Open(it) }

    val openPaths = open.mapNotNull { (it as ProjectItem.Open).project.basePath }.toSet()

    val recentActions = RecentProjectsManagerBase.getInstanceEx()
        .getRecentProjectsActions(false) ?: emptyArray()

    val recent = recentActions
        .filterIsInstance<ReopenProjectAction>()
        .mapNotNull { a ->
            val path = a.projectPath ?: return@mapNotNull null
            val name = a.projectName ?: path
            if (openPaths.contains(path)) return@mapNotNull null
            ProjectItem.Recent(name, path)
        }

    return open + recent
}

private fun handleSelect(item: ProjectItem, currentProject: Project) {
    when (item) {
        is ProjectItem.Open -> ProjectUtil.focusProjectWindow(item.project, true)
        is ProjectItem.Recent -> ProjectUtil.openOrImport(item.path, currentProject, true)
    }
}
