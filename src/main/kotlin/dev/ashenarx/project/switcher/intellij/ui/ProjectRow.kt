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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.openapi.util.io.FileUtil
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
    icon: SwingIcon?,
    isSelected: Boolean,
    showPath: Boolean,
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
            val secondary = with(JewelTheme.globalColors.text) {
                if (isSelected) disabledSelected else disabled
            }

            ProjectIcon(icon)
            Spacer(Modifier.width(Dimens.IconGap))

            NameAndPath(
                item = item,
                showPath = showPath,
                secondary = secondary,
                modifier = Modifier.weight(1f),
            )

            item.branch?.let { branch ->
                Spacer(Modifier.width(Dimens.MetadataGap))
                Text(
                    text = branch,
                    color = secondary,
                    maxLines = 1,
                    // A branch is identified by its tail, so drop the `feature/` style prefix first.
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.widthIn(max = Dimens.MaxBranchWidth),
                )
            }
        }
    }
}

@Composable
private fun NameAndPath(
    item: ProjectItem,
    showPath: Boolean,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = item.displayName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (showPath && item.path.isNotEmpty()) {
            Spacer(Modifier.width(Dimens.MetadataGap))
            Text(
                text = presentableProjectPath(item.path),
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

internal fun presentableProjectPath(path: String): String =
    FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path), false)

/**
 * SwingPanel flickers in recycled rows, while Jewel icon keys cannot represent generated project
 * icons. Keep an empty slot until the asynchronously rasterized icon arrives.
 */
@Composable
private fun ProjectIcon(icon: SwingIcon?) {
    val painter = remember(icon) { icon?.toPainterOrNull() }

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
    runCatching { BitmapPainter(rasterize().toComposeImageBitmap()) }.getOrNull()

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
