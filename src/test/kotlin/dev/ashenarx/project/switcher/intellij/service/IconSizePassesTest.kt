package dev.ashenarx.project.switcher.intellij.service

import dev.ashenarx.project.switcher.intellij.service.RecentProjectsService.Companion.iconSizePasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A regression here costs latency, not correctness, so nothing at runtime would report it. */
class IconSizePassesTest {

    /**
     * The platform's icon cache is keyed by `(path, size)`, and every platform caller asks for 20.
     * Leading with any other size means the first thing the user sees can never be a cache hit.
     */
    @Test
    fun `the first pass asks for the size the platform itself caches`() {
        for (scale in listOf(1f, 1.25f, 2f, 3f, 4f)) {
            assertEquals(20, iconSizePasses(scale).first(), "scale $scale")
        }
    }

    /**
     * A later pass overwrites an earlier one, so the crisp icon has to come last. Reversed, the
     * HiDPI raster would be replaced by the blurry one the moment it arrived.
     */
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
