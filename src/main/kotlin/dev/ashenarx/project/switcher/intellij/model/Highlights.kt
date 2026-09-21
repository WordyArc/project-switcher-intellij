package dev.ashenarx.project.switcher.intellij.model

internal data class Highlights(val name: List<IntRange>, val path: List<IntRange>) {

    companion object {
        val NONE = Highlights(emptyList(), emptyList())
    }
}

internal fun ProjectItem.splitHighlights(ranges: List<IntRange>): Highlights {
    val pathStart = displayName.length + 1

    return Highlights(
        name = ranges.clampTo(displayName.indices),
        path = ranges.clampTo(pathStart until pathStart + path.length).map { it.first - pathStart..it.last - pathStart },
    )
}

private fun List<IntRange>.clampTo(bounds: IntRange): List<IntRange> = mapNotNull { range ->
    val first = maxOf(range.first, bounds.first)
    val last = minOf(range.last, bounds.last)
    if (first <= last) first..last else null
}
