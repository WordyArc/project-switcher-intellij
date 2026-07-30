package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.jetbrains.jewel.bridge.retrieveColorOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import javax.swing.Icon as SwingIcon

private val INDICATOR_WIDTH = 4.dp
private val INDICATOR_STROKE = 2.dp
private val INDICATOR_VPAD = 7.dp
private val INDICATOR_INSET_START = 2.dp

@Composable
internal fun ProjectRow(
    item: ProjectItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = JewelTheme.simpleListItemStyle

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val selectedBg = retrieveColorOrNull("List.selectionBackground")
        ?: JewelTheme.globalColors.outlines.focused
    val hoverBg = retrieveColorOrNull("List.hoverBackground") ?: selectedBg.copy(alpha = 0.4f)

    val background = when {
        isSelected -> selectedBg
        isHovered -> hoverBg
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(JewelTheme.globalMetrics.rowHeight)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        CurrentProjectIndicator(
            visible = item.isCurrent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = INDICATOR_INSET_START, top = INDICATOR_VPAD, bottom = INDICATOR_VPAD)
                .width(INDICATOR_WIDTH)
                .fillMaxHeight()
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(style.metrics.outerPadding)
                .background(background, RoundedCornerShape(style.metrics.selectionBackgroundCornerSize))
                .padding(style.metrics.innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProjectIcon(item)
            Spacer(Modifier.width(Dimens.IconGap))

            Text(item.displayName, maxLines = 1)
            Spacer(Modifier.weight(1f))

            item.branch?.let { branch ->
                val textColors = JewelTheme.globalColors.text
                Text(
                    text = branch,
                    color = if (isSelected) textColors.disabledSelected else textColors.disabled,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Rasterized rather than drawn through a `SwingPanel`, which flickers as the lazy list recycles and
 * forces every row background to be opaque. Jewel's `IntelliJIconKey.fromPlatformIcon` is not an
 * option either: it only accepts icons backed by a resource path, while project icons are generated
 * (from `.idea/icon.png` or the project's initials).
 */
@Composable
private fun ProjectIcon(item: ProjectItem) {
    val painter = remember(item.icon) { item.icon?.toPainterOrNull() }

    if (painter == null) {
        Spacer(Modifier.size(Dimens.IconSize))
    } else {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSize),
        )
    }
}

private fun SwingIcon.toPainterOrNull(): Painter? =
    runCatching {
        val image = ImageUtil.toBufferedImage(IconUtil.toImage(this, ScaleContext.create()))
        BitmapPainter(image.toComposeImageBitmap())
    }.getOrNull()

@Composable
private fun CurrentProjectIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return

    val color = JewelTheme.globalColors.text.normal.copy(alpha = 0.85f)
    Canvas(modifier = modifier) {
        val x = size.width / 2f
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = INDICATOR_STROKE.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
