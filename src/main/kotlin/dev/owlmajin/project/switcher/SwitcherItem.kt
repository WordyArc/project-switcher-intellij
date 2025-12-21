package dev.owlmajin.project.switcher

import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

sealed interface SwitcherItem {
    val key: String

    data class Header(val text: String) : SwitcherItem {
        override val key: String = "header:$text"
    }

    data class OpenProjectItem(
        val project: Project,
        val isCurrent: Boolean,
        val path: String?,
        val branch: String?,
    ) : SwitcherItem {
        override val key: String = "open:${project.locationHash}"
    }

    data class RecentProjectItem(
        val action: ReopenProjectAction,
        val name: String,
        val path: String,
        val subtitle: String?,
        val branch: String?,
    ) : SwitcherItem {
        override val key: String = "recent:$path"
    }
}
