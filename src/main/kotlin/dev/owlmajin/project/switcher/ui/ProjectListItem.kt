package dev.owlmajin.project.switcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.owlmajin.project.switcher.data.ProjectData
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette
import javax.swing.Icon
import java.awt.RenderingHints
import java.awt.image.BufferedImage

private const val ICON_SIZE_DP = 20
private val GUTTER_WIDTH = 18.dp
private val DOT_SIZE = 6.dp

@Composable
fun ProjectListItem(
    projectData: ProjectData,
    isSelected: Boolean,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hover должен влиять только на "карточку", не на gutter с точкой
    val cardHoverSource = remember { MutableInteractionSource() }
    val isHovered = cardHoverSource.collectIsHoveredAsState().value

    Row(
        modifier = modifier
            .fillMaxWidth()
            // кликаем по всей строке (включая gutter), но фон/ховер только на карточке
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- GUTTER (точка вне карточки) ----
        Box(
            modifier = Modifier
                .width(GUTTER_WIDTH)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            CurrentProjectDot(visible = isCurrent)
        }

        // В Jewel SimpleListItem "active" удобно использовать как "hovered/active row":
        // - not selected + active -> hover background
        // - not selected + !active -> transparent/regular background
        // - selected + active -> selectedActive background
        SimpleListItem(
            selected = isSelected,
            active = isSelected || isHovered,
            modifier = Modifier
                .weight(1f)
                .hoverable(cardHoverSource)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProjectIcon(icon = projectData.icon, size = ICON_SIZE_DP.dp)

                Spacer(Modifier.width(8.dp))

                Text(
                    projectData.displayName,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                projectData.branch?.let { branch ->
                    Spacer(Modifier.width(12.dp))
                    BranchLabel(branch = branch, isSelected = isSelected)
                }
            }
        }
    }
}

@Composable
private fun CurrentProjectDot(visible: Boolean) {
    if (!visible) {
        Spacer(Modifier.size(DOT_SIZE))
        return
    }

    val dotColor = JewelTheme.colorPalette.grayOrNull(8)
        ?: Color(0xFF9AA0A6) // безопасный fallback

    Box(
        modifier = Modifier
            .size(DOT_SIZE)
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
private fun ProjectIcon(icon: Icon?, size: Dp, modifier: Modifier = Modifier) {
    if (icon == null) {
        Spacer(modifier.size(size))
        return
    }

    val density = LocalDensity.current
    val bitmap = remember(icon, size, density) {
        val targetPx = with(density) { size.roundToPx().coerceAtLeast(1) }

        val srcW = icon.iconWidth.coerceAtLeast(1)
        val srcH = icon.iconHeight.coerceAtLeast(1)

        val image = BufferedImage(targetPx, targetPx, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        try {
            // clear to transparent explicitly
            g2.composite = java.awt.AlphaComposite.Src
            g2.color = java.awt.Color(0, 0, 0, 0)
            g2.fillRect(0, 0, targetPx, targetPx)

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val sx = targetPx.toDouble() / srcW.toDouble()
            val sy = targetPx.toDouble() / srcH.toDouble()
            g2.scale(sx, sy)

            icon.paintIcon(null, g2, 0, 0)
        } finally {
            g2.dispose()
        }

        image.toComposeImageBitmap()
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

@Composable
private fun BranchLabel(branch: String, isSelected: Boolean) {
    val base = JewelTheme.colorPalette.grayOrNull(8) ?: Color(0xFF9AA0A6)
    val color = if (isSelected) base.copy(alpha = 0.95f) else base.copy(alpha = 0.75f)

    Text(
        branch,
        color = color,
        maxLines = 1
    )
}
