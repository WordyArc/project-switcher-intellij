package dev.ashenarx.project.switcher.intellij.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RevealTest {

    private val rows = (3..8).map { VisibleRow(index = it, offset = (it - 3) * ROW - 10, size = ROW) }

    @Test
    fun `a fully visible row needs no scrolling`() {
        assertEquals(Reveal.None, revealFor(5, 5, rows, VIEWPORT_START, VIEWPORT_END))
    }

    @Test
    fun `a row above the view is brought to the top`() {
        assertEquals(Reveal.To(1, 0), revealFor(1, 1, rows, VIEWPORT_START, VIEWPORT_END))
    }

    @Test
    fun `a row cut by the top edge scrolls back by the hidden part only`() {
        assertEquals(Reveal.By(-10), revealFor(3, 3, rows, VIEWPORT_START, VIEWPORT_END))
    }

    @Test
    fun `a row below the view is brought to the bottom, not to the top`() {
        assertEquals(Reveal.To(12, -(VIEWPORT_END - ROW)), revealFor(12, 12, rows, VIEWPORT_START, VIEWPORT_END))
    }

    @Test
    fun `a row cut by the bottom edge scrolls forward by the hidden part only`() {
        val cut = rows.first { it.index == 7 }

        assertEquals(
            Reveal.By(cut.offset + cut.size - VIEWPORT_END),
            revealFor(cut.index, cut.index, rows, VIEWPORT_START, VIEWPORT_END),
        )
    }

    @Test
    fun `a section header above the row is revealed together with it`() {
        assertEquals(Reveal.To(2, 0), revealFor(2, 3, rows, VIEWPORT_START, VIEWPORT_END))
    }

    @Test
    fun `a list that has not been laid out yet scrolls straight to the row`() {
        assertEquals(Reveal.To(7, 0), revealFor(7, 7, emptyList(), VIEWPORT_START, VIEWPORT_END))
    }

    @Test
    fun `a page is the fully visible rows minus the one kept for context`() {
        assertEquals(2, pageSizeFor(rows, VIEWPORT_START, VIEWPORT_END))
        assertEquals(1, pageSizeFor(rows.take(1), VIEWPORT_START, VIEWPORT_END), "a page is never empty")
    }

    private companion object {
        const val ROW = 24
        const val VIEWPORT_START = 0
        const val VIEWPORT_END = 100
    }
}
