package dev.ashenarx.project.switcher.intellij.model

data class ProjectList(
    val open: List<ProjectItem.Open>,
    val recent: List<ProjectItem.Recent>,
) {
    val all: List<ProjectItem> = open + recent

    val hasRecent: Boolean get() = recent.isNotEmpty()
    val isEmpty: Boolean get() = all.isEmpty()

    /**
     * Keeps the items [score] accepts, best first. Ranking stays inside each section so the
     * open/recent split survives, and the sort is stable, which leaves items the matcher rates
     * equally in the recency order they arrived in.
     */
    fun rankedBy(score: (String) -> Int?): ProjectList {
        return ProjectList(
            open = open.ranked(score),
            recent = recent.ranked(score),
        )
    }

    /**
     * The best-scoring item across both sections, for pre-selecting what a query most likely meant.
     * [rankedBy] already put the winner of each section at its head, so only those two can compete;
     * a tie goes to the open project, whose row is the cheaper thing to activate by accident.
     */
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

/**
 * Where the selection should land once [removedId] is gone. Without this the rebuilt list falls back
 * to [defaultSelection] and the selection jumps to the top, which makes deleting several entries in
 * a row unusable.
 */
fun selectionAfterRemoving(items: List<ProjectItem>, removedId: String?): String? {
    val index = items.indexOfFirst { it.id == removedId }
    if (index < 0) return null

    return (items.getOrNull(index + 1) ?: items.getOrNull(index - 1))?.id
}
