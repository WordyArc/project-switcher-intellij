package dev.owlmajin.project.switcher.data

import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.project.Project
import javax.swing.Icon

sealed interface ProjectData {
    val id: String

    val displayName: String
    val path: String?
    val branch: String?
    val icon: Icon?

    val isCurrent: Boolean get() = false

    data class Open(
        val project: Project,
        override val isCurrent: Boolean,
        override val branch: String?,
        override val icon: Icon?
    ) : ProjectData {
        override val id: String = "open:${project.locationHash}"

        override val displayName: String get() = project.name
        override val path: String? get() = project.basePath ?: project.projectFilePath
    }

    data class Recent(
        val action: ReopenProjectAction,
        override val branch: String?,
        override val icon: Icon?
    ) : ProjectData {
        override val id: String = "recent:${action.projectPath}"

        override val displayName: String get() = action.projectNameToDisplay.orEmpty()
        override val path: String get() = action.projectPath
    }
}
