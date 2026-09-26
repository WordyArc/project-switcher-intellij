package dev.ashenarx.project.switcher.intellij.model

data class Choice(val id: String, val query: String)

fun resolveSelection(rows: List<ProjectItem>, query: String, choice: Choice?, top: ProjectItem?): String? =
    choice?.takeIf { it.query == query && rows.any { row -> row.id == it.id } }?.id
        ?: top?.id
        ?: defaultSelection(rows)

fun defaultSelection(items: List<ProjectItem>): String? =
    (items.firstOrNull { !it.isCurrent } ?: items.firstOrNull())?.id

fun moveSelection(items: List<ProjectItem>, selectedId: String?, delta: Int): String? {
    if (items.isEmpty()) return null

    val currentIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val newIndex = (currentIndex + delta).mod(items.size)
    return items[newIndex].id
}

fun pageSelection(items: List<ProjectItem>, selectedId: String?, delta: Int): String? {
    if (items.isEmpty()) return null

    val currentIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    return items[(currentIndex + delta).coerceIn(items.indices)].id
}

fun selectionAfterRemoving(items: List<ProjectItem>, removedId: String?): String? {
    val index = items.indexOfFirst { it.id == removedId }
    if (index < 0) return null

    return (items.getOrNull(index + 1) ?: items.getOrNull(index - 1))?.id
}
