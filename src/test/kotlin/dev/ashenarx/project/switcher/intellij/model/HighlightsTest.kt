package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HighlightsTest {

    private val item = ProjectItem.Recent(displayName = "switcher", path = "/home/me/switcher", branch = null)

    @Test
    fun `ranges inside the name stay in name coordinates`() {
        val highlights = item.splitHighlights(listOf(0..2))

        assertEquals(Highlights(name = listOf(0..2), path = emptyList()), highlights)
    }

    @Test
    fun `ranges inside the path are rebased onto the path`() {
        val highlights = item.splitHighlights(listOf(9..12))

        assertEquals(Highlights(name = emptyList(), path = listOf(0..3)), highlights)
        assertEquals("/hom", item.path.substring(0..3))
    }

    @Test
    fun `a range spanning the separator is split between the two`() {
        val highlights = item.splitHighlights(listOf(6..10))

        assertEquals(listOf(6..7), highlights.name)
        assertEquals(listOf(0..1), highlights.path)
    }

    @Test
    fun `an item without a path has no path highlights`() {
        val bare = ProjectItem.Recent(displayName = "switcher", path = "", branch = null)

        assertEquals(Highlights(name = listOf(0..7), path = emptyList()), bare.splitHighlights(listOf(0..7)))
    }
}
