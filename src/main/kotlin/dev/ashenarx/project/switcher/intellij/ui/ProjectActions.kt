package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.intellij.openapi.project.Project
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.service.ProjectCatalog
import dev.ashenarx.project.switcher.intellij.service.ProjectIconLoader
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener

internal interface ProjectActions {
    fun snapshot(): ProjectList?

    suspend fun collect(): ProjectList

    fun cachedIcons(keys: Collection<String>): Map<String, ImageBitmap>

    suspend fun loadIcons(items: List<ProjectItem>, emit: suspend (String, ImageBitmap) -> Unit)

    suspend fun missing(paths: Collection<String>): Set<String>

    fun close(project: ProjectItem.Open)

    fun forget(path: String)
}

internal class PlatformProjectActions(private val currentProject: Project?) : ProjectActions {

    override fun snapshot(): ProjectList? = ProjectCatalog.getInstance().snapshot(currentProject)

    override suspend fun collect(): ProjectList = ProjectCatalog.getInstance().collect(currentProject)

    override fun cachedIcons(keys: Collection<String>): Map<String, ImageBitmap> =
        ProjectIconLoader.getInstance().cachedIcons(keys).mapValues { (_, image) -> image.toComposeImageBitmap() }

    override suspend fun loadIcons(items: List<ProjectItem>, emit: suspend (String, ImageBitmap) -> Unit) {
        ProjectIconLoader.getInstance().loadIcons(items) { key, image -> emit(key, image.toComposeImageBitmap()) }
    }

    override suspend fun missing(paths: Collection<String>): Set<String> = ProjectCatalog.getInstance().missing(paths)

    override fun close(project: ProjectItem.Open) {
        ProjectOpener.getInstance().close(project)
    }

    override fun forget(path: String) {
        ProjectCatalog.getInstance().forget(path)
    }
}
