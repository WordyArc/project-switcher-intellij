package dev.ashenarx.project.switcher.intellij.ui

import com.intellij.openapi.project.Project
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.service.ProjectCatalog
import dev.ashenarx.project.switcher.intellij.service.ProjectIconLoader
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import javax.swing.Icon

internal interface ProjectActions {
    suspend fun collect(currentProject: Project?): ProjectList

    suspend fun loadIcons(paths: List<String>, emit: suspend (String, Icon) -> Unit)

    fun close(project: ProjectItem.Open)

    fun forget(path: String)
}

internal object PlatformProjectActions : ProjectActions {
    override suspend fun collect(currentProject: Project?): ProjectList =
        ProjectCatalog.getInstance().collect(currentProject)

    override suspend fun loadIcons(paths: List<String>, emit: suspend (String, Icon) -> Unit) {
        ProjectIconLoader.getInstance().loadIcons(paths, emit)
    }

    override fun close(project: ProjectItem.Open) {
        ProjectOpener.getInstance().close(project)
    }

    override fun forget(path: String) {
        ProjectCatalog.getInstance().forget(path)
    }
}
