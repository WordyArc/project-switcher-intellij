package dev.ashenarx.project.switcher.intellij.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import javax.swing.Icon as SwingIcon

/**
 * Paints [this] into a bitmap of its own logical size, through a graphics carrying no scale
 * transform.
 *
 * The absent transform is the whole point. A `ProjectFileIcon` — what `.idea/icon.png` loads as —
 * negotiates its resolution against the graphics it is handed: it sizes the bitmap by
 * `JBUI.pixScale(g.deviceConfiguration)` but reports its logical size by dividing through
 * `JBUIScale.sysScale(g)`, read off the graphics transform. Offscreen those disagree, since a
 * device configuration is always unscaled there, so any transform makes the icon declare itself
 * smaller than it drew and paint into the top-left of an oversized canvas. `IconUtil.toImage`
 * trips on this too, by way of the HiDPI-backed image it paints into.
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
