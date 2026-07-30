package dev.ashenarx.project.switcher.intellij.model

import javax.swing.Icon


sealed interface ProjectItem {
    val id: String
    val displayName: String
    val path: String
    val branch: String?
    val icon: Icon?

    val isCurrent: Boolean get() = false

    data class Open(
        val locationHash: String,
        override val displayName: String,
        override val path: String,
        override val branch: String?,
        override val icon: Icon?,
        override val isCurrent: Boolean,
    ) : ProjectItem {
        override val id: String get() = "open:$locationHash"
    }

    data class Recent(
        override val displayName: String,
        override val path: String,
        override val branch: String?,
        override val icon: Icon?,
    ) : ProjectItem {
        override val id: String get() = "recent:$path"
    }
}
