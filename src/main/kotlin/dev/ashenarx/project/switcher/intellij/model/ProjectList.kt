package dev.ashenarx.project.switcher.intellij.model

data class ProjectList(
    val open: List<ProjectItem.Open>,
    val recent: List<ProjectItem.Recent>,
) {
    val all: List<ProjectItem> = open + recent

    val hasRecent: Boolean get() = recent.isNotEmpty()
    val isEmpty: Boolean get() = all.isEmpty()

    /** Ranks each section independently, preserving input order for equal scores. */
    fun rankedBy(score: (String) -> Int?): ProjectList {
        return ProjectList(
            open = open.ranked(score),
            recent = recent.ranked(score),
        )
    }

    /** Compares the already-ranked section heads; ties favor an open project. */
    fun topMatch(score: (String) -> Int?): ProjectItem? =
        listOfNotNull(open.firstOrNull(), recent.firstOrNull())
            .maxByOrNull { score(it.searchText) ?: Int.MIN_VALUE }

    companion object {
        val EMPTY = ProjectList(emptyList(), emptyList())
    }
}

private fun <T : ProjectItem> List<T>.ranked(score: (String) -> Int?): List<T> =
    mapNotNull { item -> score(item.searchText)?.let { item to it } }
        .sortedByDescending { (_, degree) -> degree }
        .map { (item, _) -> item }

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

fun selectionAfterRemoving(items: List<ProjectItem>, removedId: String?): String? {
    val index = items.indexOfFirst { it.id == removedId }
    if (index < 0) return null

    return (items.getOrNull(index + 1) ?: items.getOrNull(index - 1))?.id
}
