package dev.ashenarx.project.switcher.intellij.service

import dev.ashenarx.project.switcher.intellij.service.RecentProjectsService.Companion.distinctIconPaths
import dev.ashenarx.project.switcher.intellij.service.RecentProjectsService.Companion.iconSizePasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconSizePassesTest {

    @Test
    fun `icon paths are non-blank and unique in encounter order`() {
        val paths = distinctIconPaths(listOf("/first", "", "/second", "/first", "   ", "/second"))

        assertEquals(listOf("/first", "/second"), paths)
    }

    @Test
    fun `the first pass asks for the size the platform itself caches`() {
        for (scale in listOf(1f, 1.25f, 2f, 3f, 4f)) {
            assertEquals(20, iconSizePasses(scale).first(), "scale $scale")
        }
    }

    @Test
    fun `passes are ordered coarse to crisp`() {
        val passes = iconSizePasses(2f)

        assertEquals(listOf(20, 40), passes)
        assertTrue(passes == passes.sorted(), "expected ascending, got $passes")
    }

    @Test
    fun `a non-HiDPI screen gets a single pass`() {
        assertEquals(listOf(20), iconSizePasses(1f))
    }

    @Test
    fun `a fractional scale rounds up to the next whole pass`() {
        assertEquals(listOf(20, 40), iconSizePasses(1.25f))
        assertEquals(listOf(20, 60), iconSizePasses(2.5f))
    }

    @Test
    fun `an extreme scale is clamped`() {
        assertEquals(listOf(20, 60), iconSizePasses(4f))
        assertEquals(listOf(20, 60), iconSizePasses(100f))
    }

    @Test
    fun `a degenerate scale falls back to a single pass`() {
        assertEquals(listOf(20), iconSizePasses(0f))
        assertEquals(listOf(20), iconSizePasses(-1f))
    }
}
