package dev.ashenarx.project.switcher.intellij.data

data class ProjectsData(
    val openProjects: List<ProjectData.Open>,
    val recentProjects: List<ProjectData.Recent>
) {
    val hasOpenProjects: Boolean get() = openProjects.isNotEmpty()
    val hasRecentProjects: Boolean get() = recentProjects.isNotEmpty()
    val isEmpty: Boolean get() = !hasOpenProjects && !hasRecentProjects

    val allProjects: List<ProjectData> get() = openProjects + recentProjects

    fun filter(query: String): ProjectsData {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return this

        return ProjectsData(
            openProjects = openProjects.filter { it.matchesQuery(trimmed) },
            recentProjects = recentProjects.filter { it.matchesQuery(trimmed) }
        )
    }

    companion object {
        val EMPTY = ProjectsData(emptyList(), emptyList())
    }
}

private fun ProjectData.matchesQuery(query: String): Boolean {
    return displayName.contains(query, ignoreCase = true) ||
            path?.contains(query, ignoreCase = true) == true
}
