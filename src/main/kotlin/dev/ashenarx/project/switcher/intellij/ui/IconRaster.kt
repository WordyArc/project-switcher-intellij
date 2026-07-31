package dev.ashenarx.project.switcher.intellij.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import javax.swing.Icon as SwingIcon

/**
 * ProjectFileIcon derives two dimensions from the graphics device and transform. Those disagree on
 * an offscreen HiDPI canvas, shrinking the icon into one corner, so rasterize without a transform.
 */
internal fun SwingIcon.rasterize(): BufferedImage {
    @Suppress("UndesirableClassUsage") // A raw canvas is intended: no hidden HiDPI scaling wanted.
    val image = BufferedImage(max(1, iconWidth), max(1, iconHeight), BufferedImage.TYPE_INT_ARGB)

    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        paintIcon(null, g, 0, 0)
    } finally {
        g.dispose()
    }

    return image
}
