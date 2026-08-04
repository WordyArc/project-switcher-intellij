package dev.ashenarx.project.switcher.intellij.ui

import com.intellij.openapi.project.Project
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import javax.swing.Icon

internal class FakeProjectActions(private var projects: ProjectList) : ProjectActions {

    val closed = mutableListOf<ProjectItem.Open>()
    val forgotten = mutableListOf<String>()

    override suspend fun collect(currentProject: Project?): ProjectList = projects

    /** Stands in for the platform changing its mind between two collects. */
    fun publish(projects: ProjectList) {
        this.projects = projects
    }

    override suspend fun loadIcons(paths: List<String>, emit: suspend (String, Icon) -> Unit) = Unit

    /** A closed project leaves the open section for the recent one, as it does in the IDE. */
    override fun close(project: ProjectItem.Open) {
        closed += project
        projects = ProjectList(
            open = projects.open - project,
            recent = projects.recent + ProjectItem.Recent(project.displayName, project.path, project.branch),
        )
    }

    override fun forget(path: String) {
        forgotten += path
        projects = ProjectList(
            open = projects.open,
            recent = projects.recent.filterNot { it.path == path },
        )
    }
}
