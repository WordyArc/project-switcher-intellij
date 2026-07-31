package dev.ashenarx.project.switcher.intellij.ui

import com.intellij.ide.RecentProjectIconHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.Icon

class IconRasterTest {

    private class FullBleedIcon(private val size: Int) : Icon {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            g.color = Color.RED
            g.fillRect(x, y, size, size)
        }

        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
    }

    @Test
    fun `raster takes the icon's logical size`() {
        val image = FullBleedIcon(20).rasterize()

        assertEquals(20, image.width)
        assertEquals(20, image.height)
    }

    @Test
    fun `content fills the raster instead of hugging the top-left corner`() {
        val image = FullBleedIcon(20).rasterize()

        assertOpaque(image, 0, 0)
        assertOpaque(image, image.width - 1, image.height - 1)
    }

    @Test
    fun `a zero-sized icon still yields a usable bitmap`() {
        val image = FullBleedIcon(0).rasterize()

        assertTrue(image.width >= 1 && image.height >= 1, "got ${image.width}x${image.height}")
    }

    // A real ProjectFileIcon sizes itself from the graphics context, unlike FullBleedIcon.
    @Test
    fun `a real project icon covers the raster it is measured for`() {
        val icon = RecentProjectIconHelper.createIcon(data = opaquePng(64), svg = false, size = 20)
        val image = icon.rasterize()

        assertEquals(icon.iconWidth, image.width)
        assertEquals(icon.iconHeight, image.height)

        assertOpaque(image, 0, 0)
        assertOpaque(image, image.width - 1, image.height - 1)
        assertOpaque(image, image.width / 2, image.height / 2)
    }

    @Test
    fun `a larger requested size reaches the raster`() {
        val png = opaquePng(128)

        for (requested in listOf(20, 40, 60)) {
            val image = RecentProjectIconHelper.createIcon(data = png, svg = false, size = requested).rasterize()

            assertEquals(requested, image.width, "requested $requested")
            assertEquals(requested, image.height, "requested $requested")
            assertOpaque(image, image.width - 1, image.height - 1)
        }
    }

    private fun opaquePng(size: Int): ByteArray {
        @Suppress("UndesirableClassUsage")
        val source = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = source.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, size, size)
        g.dispose()

        return ByteArrayOutputStream().also { ImageIO.write(source, "png", it) }.toByteArray()
    }

    private fun assertOpaque(image: BufferedImage, x: Int, y: Int) {
        val alpha = image.getRGB(x, y) ushr 24
        assertTrue(alpha > 0) { "pixel ($x, $y) is transparent — content does not reach there" }
    }
}
