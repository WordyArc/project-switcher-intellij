package dev.owlmajin.project.switcher.data

import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.project.Project
import javax.swing.Icon

sealed interface ProjectData {
    val displayName: String
    val path: String?
    val branch: String?
    val icon: Icon?

    data class Open(
        val project: Project,
        val isCurrent: Boolean,
        override val branch: String?,
        override val icon: Icon?
    ) : ProjectData {
        override val displayName: String get() = project.name
        override val path: String? get() = project.basePath ?: project.projectFilePath
    }

    data class Recent(
        val action: ReopenProjectAction,
        override val branch: String?,
        override val icon: Icon?
    ) : ProjectData {
        override val displayName: String get() = action.projectNameToDisplay.orEmpty()
        override val path: String get() = action.projectPath
    }
}

data class ProjectsData(
    val openProjects: List<ProjectData.Open>,
    val recentProjects: List<ProjectData.Recent>
) {
    val hasOpenProjects: Boolean get() = openProjects.isNotEmpty()
    val hasRecentProjects: Boolean get() = recentProjects.isNotEmpty()
    val isEmpty: Boolean get() = !hasOpenProjects && !hasRecentProjects

    companion object {
        val EMPTY = ProjectsData(emptyList(), emptyList())
    }
}
