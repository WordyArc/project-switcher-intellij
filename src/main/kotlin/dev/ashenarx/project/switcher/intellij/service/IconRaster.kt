package dev.ashenarx.project.switcher.intellij.service

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import javax.swing.Icon as SwingIcon

// On an offscreen HiDPI canvas with a transform, ProjectFileIcon shrinks into one corner.
internal fun SwingIcon.rasterize(): BufferedImage {
    @Suppress("UndesirableClassUsage")
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
