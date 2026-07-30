package dev.ashenarx.project.switcher.intellij.model

data class ProjectList(
    val open: List<ProjectItem.Open>,
    val recent: List<ProjectItem.Recent>,
) {
    val all: List<ProjectItem> = open + recent

    val hasRecent: Boolean get() = recent.isNotEmpty()
    val isEmpty: Boolean get() = all.isEmpty()

    fun filter(matches: (String) -> Boolean): ProjectList {
        return ProjectList(
            open = open.filter { matches(it.searchText) },
            recent = recent.filter { matches(it.searchText) },
        )
    }

    companion object {
        val EMPTY = ProjectList(emptyList(), emptyList())
    }
}

val ProjectItem.searchText: String
    get() = if (path.isEmpty()) displayName else "$displayName $path"

fun moveSelection(items: List<ProjectItem>, selectedId: String?, delta: Int): String? {
    if (items.isEmpty()) return null

    val currentIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val newIndex = (currentIndex + delta).coerceIn(0, items.lastIndex)
    return items[newIndex].id
}

fun defaultSelection(items: List<ProjectItem>): String? =
    (items.firstOrNull { it.isCurrent } ?: items.firstOrNull())?.id
