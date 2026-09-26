package dev.ashenarx.project.switcher.intellij.model

data class ProjectList(
    val open: List<ProjectItem.Open>,
    val recent: List<ProjectItem.Recent>,
) {
    val all: List<ProjectItem> = open + recent
    val duplicateNames: Set<String> = all
        .groupingBy { it.displayName }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    val hasRecent: Boolean get() = recent.isNotEmpty()
    val isEmpty: Boolean get() = all.isEmpty()

    fun without(item: ProjectItem): ProjectList = ProjectList(
        open = open.filterNot { it.id == item.id },
        recent = recent.filterNot { it.id == item.id },
    )

    internal fun rankedBy(match: (String) -> Match?): Ranking {
        val highlights = HashMap<String, Highlights>()
        val ranks = HashMap<String, Rank>()

        fun <T : ProjectItem> List<T>.ranked(): List<T> = mapNotNull { item ->
            val found = match(item.searchText) ?: return@mapNotNull null
            val split = item.splitHighlights(found.ranges)
            highlights[item.id] = split
            ranks[item.id] = Rank(split.isNameOnly, found.degree)
            item
        }.sortedByDescending { ranks.getValue(it.id) }

        val rows = ProjectList(open = open.ranked(), recent = recent.ranked())
        val top = listOfNotNull(rows.open.firstOrNull(), rows.recent.firstOrNull())
            .maxByOrNull { ranks.getValue(it.id) }

        return Ranking(rows, highlights, top)
    }

    companion object {
        val EMPTY = ProjectList(emptyList(), emptyList())
    }
}

internal class Ranking(val rows: ProjectList, val highlights: Map<String, Highlights>, val top: ProjectItem?)

private data class Rank(val nameOnly: Boolean, val degree: Int) : Comparable<Rank> {
    override fun compareTo(other: Rank): Int =
        compareValuesBy(this, other, Rank::nameOnly, Rank::degree)
}

val ProjectItem.searchText: String
    get() = if (location.isEmpty()) displayName else "$displayName $location"
