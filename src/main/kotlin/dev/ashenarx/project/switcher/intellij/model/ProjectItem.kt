package dev.ashenarx.project.switcher.intellij.model


/** Deliberately carries no icon; the popup keeps those in a map keyed by [path]. */
sealed interface ProjectItem {
    val id: String
    val displayName: String
    val path: String
    val branch: String?

    val isCurrent: Boolean get() = false

    data class Open(
        val locationHash: String,
        override val displayName: String,
        override val path: String,
        override val branch: String?,
        override val isCurrent: Boolean,
    ) : ProjectItem {
        override val id: String get() = "open:$locationHash"
    }

    data class Recent(
        override val displayName: String,
        override val path: String,
        override val branch: String?,
    ) : ProjectItem {
        override val id: String get() = "recent:$path"
    }
}
