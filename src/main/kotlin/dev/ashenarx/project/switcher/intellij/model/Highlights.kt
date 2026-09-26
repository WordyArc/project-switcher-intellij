package dev.ashenarx.project.switcher.intellij.model

internal data class Highlights(val name: List<IntRange>, val location: List<IntRange>) {

    val isNameOnly: Boolean get() = location.isEmpty()

    companion object {
        val NONE = Highlights(emptyList(), emptyList())
    }
}

internal fun ProjectItem.splitHighlights(ranges: List<IntRange>): Highlights {
    val locationStart = displayName.length + 1

    return Highlights(
        name = ranges.clampTo(displayName.indices),
        location = ranges
            .clampTo(locationStart until locationStart + location.length)
            .map { it.first - locationStart..it.last - locationStart },
    )
}

private fun List<IntRange>.clampTo(bounds: IntRange): List<IntRange> = mapNotNull { range ->
    val first = maxOf(range.first, bounds.first)
    val last = minOf(range.last, bounds.last)
    if (first <= last) first..last else null
}
