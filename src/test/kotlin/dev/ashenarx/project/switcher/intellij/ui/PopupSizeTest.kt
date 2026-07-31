package dev.ashenarx.project.switcher.intellij.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Dimension

class PopupSizeTest {

    @Test
    fun `size follows ordinary IDE window`() {
        assertEquals(Dimension(380, 432), popupSizeFor(Dimension(1280, 720)))
        assertEquals(Dimension(380, 540), popupSizeFor(Dimension(1440, 900)))
        assertEquals(Dimension(480, 648), popupSizeFor(Dimension(1920, 1080)))
    }

    @Test
    fun `size is capped on a large IDE window`() {
        assertEquals(Dimension(640, 760), popupSizeFor(Dimension(2560, 1440)))
    }

    @Test
    fun `size keeps margins in a small IDE window`() {
        assertEquals(Dimension(336, 296), popupSizeFor(Dimension(400, 360)))
    }
}
