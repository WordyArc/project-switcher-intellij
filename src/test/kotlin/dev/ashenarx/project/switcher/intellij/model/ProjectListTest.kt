package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProjectListTest {

    @Test
    fun `filter keeps items matching by name or path`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/home/me/alpha")),
            recent = listOf(recent("beta", "/work/beta"), recent("gamma", "/home/me/gamma")),
        )

        val byName = list.filter { it.contains("beta") }
        assertEquals(emptyList<ProjectItem.Open>(), byName.open)
        assertEquals(listOf("recent:/work/beta"), byName.recent.map { it.id })

        val byPath = list.filter { it.contains("/home/me") }
        assertEquals(listOf("open:hash-alpha", "recent:/home/me/gamma"), byPath.all.map { it.id })
    }

    @Test
    fun `filter that matches nothing yields an empty list`() {
        val list = ProjectList(open = listOf(open("alpha", "/a")), recent = listOf(recent("beta", "/b")))

        assertEquals(true, list.filter { false }.isEmpty)
    }

    @Test
    fun `moveSelection walks the combined list across the section boundary`() {
        val items = listOf(open("alpha", "/a"), recent("beta", "/b"), recent("gamma", "/c"))

        assertEquals("recent:/b", moveSelection(items, "open:hash-alpha", delta = +1))
        assertEquals("open:hash-alpha", moveSelection(items, "recent:/b", delta = -1))
    }

    @Test
    fun `moveSelection clamps at both ends`() {
        val items = listOf(open("alpha", "/a"), recent("beta", "/b"))

        assertEquals("open:hash-alpha", moveSelection(items, "open:hash-alpha", delta = -1))
        assertEquals("recent:/b", moveSelection(items, "recent:/b", delta = +1))
    }

    @Test
    fun `moveSelection falls back to the first item when the selection is gone`() {
        val items = listOf(open("alpha", "/a"), recent("beta", "/b"))

        assertEquals("open:hash-alpha", moveSelection(items, "recent:/vanished", delta = 0))
        assertNull(moveSelection(emptyList(), "open:hash-alpha", delta = +1))
    }

    @Test
    fun `defaultSelection prefers the current project over the first row`() {
        val items = listOf(open("alpha", "/a"), open("beta", "/b", isCurrent = true))

        assertEquals("open:hash-beta", defaultSelection(items))
        assertEquals("open:hash-alpha", defaultSelection(listOf(open("alpha", "/a"))))
        assertNull(defaultSelection(emptyList()))
    }

    @Test
    fun `searchText covers both name and path`() {
        assertEquals("alpha /home/alpha", open("alpha", "/home/alpha").searchText)
        assertEquals("alpha", open("alpha", "").searchText)
    }

    private fun open(name: String, path: String, isCurrent: Boolean = false) = ProjectItem.Open(
        locationHash = "hash-$name",
        displayName = name,
        path = path,
        branch = null,
        icon = null,
        isCurrent = isCurrent,
    )

    private fun recent(name: String, path: String) = ProjectItem.Recent(
        displayName = name,
        path = path,
        branch = null,
        icon = null,
    )
}
