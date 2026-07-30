package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProjectListTest {

    @Test
    fun `rankedBy keeps items matching by name or path`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/home/me/alpha")),
            recent = listOf(recent("beta", "/work/beta"), recent("gamma", "/home/me/gamma")),
        )

        val byName = list.rankedBy { text -> 0.takeIf { text.contains("beta") } }
        assertEquals(emptyList<ProjectItem.Open>(), byName.open)
        assertEquals(listOf("recent:/work/beta"), byName.recent.map { it.id })

        val byPath = list.rankedBy { text -> 0.takeIf { text.contains("/home/me") } }
        assertEquals(listOf("open:hash-alpha", "recent:/home/me/gamma"), byPath.all.map { it.id })
    }

    @Test
    fun `rankedBy that matches nothing yields an empty list`() {
        val list = ProjectList(open = listOf(open("alpha", "/a")), recent = listOf(recent("beta", "/b")))

        assertEquals(true, list.rankedBy { null }.isEmpty)
    }

    @Test
    fun `rankedBy sorts by score inside a section without merging the two`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a"), open("beta", "/b")),
            recent = listOf(recent("gamma", "/c"), recent("delta", "/d")),
        )

        // Scores the trailing items highest, so both sections must reverse independently.
        val ranked = list.rankedBy { text -> if (text.startsWith("beta") || text.startsWith("delta")) 10 else 1 }

        assertEquals(listOf("open:hash-beta", "open:hash-alpha"), ranked.open.map { it.id })
        assertEquals(listOf("recent:/d", "recent:/c"), ranked.recent.map { it.id })
    }

    @Test
    fun `rankedBy leaves equally scored items in their original order`() {
        val list = ProjectList(
            open = emptyList(),
            recent = listOf(recent("gamma", "/c"), recent("delta", "/d"), recent("epsilon", "/e")),
        )

        assertEquals(
            listOf("recent:/c", "recent:/d", "recent:/e"),
            list.rankedBy { 7 }.recent.map { it.id },
        )
    }

    @Test
    fun `topMatch reaches across the section boundary`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a")),
            recent = listOf(recent("beta", "/b")),
        )

        // The head of the recent section outscores the open one, so it must win despite ranking below.
        assertEquals("recent:/b", list.topMatch { text -> if (text.startsWith("beta")) 900 else 100 }?.id)
        assertEquals("open:hash-alpha", list.topMatch { text -> if (text.startsWith("beta")) 100 else 900 }?.id)
    }

    @Test
    fun `topMatch breaks a tie in favour of the open project`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a")),
            recent = listOf(recent("beta", "/b")),
        )

        assertEquals("open:hash-alpha", list.topMatch { 500 }?.id)
    }

    @Test
    fun `topMatch of an empty list is null`() {
        assertNull(ProjectList.EMPTY.topMatch { 1 })
    }

    @Test
    fun `topMatch ignores everything below each section head`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a"), open("zeta", "/z")),
            recent = listOf(recent("beta", "/b")),
        )

        // "zeta" scores highest but is not a head, so it cannot be the pre-selection.
        assertEquals("recent:/b", list.topMatch { text -> if (text.startsWith("zeta")) 999 else if (text.startsWith("beta")) 500 else 100 }?.id)
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
