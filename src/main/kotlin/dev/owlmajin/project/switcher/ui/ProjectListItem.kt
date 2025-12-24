package dev.owlmajin.project.switcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import dev.owlmajin.project.switcher.data.ProjectData
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingConstants

private const val ICON_SIZE_DP = 20

private val INDICATOR_WIDTH = 4.dp
private val INDICATOR_STROKE = 2.dp
private val INDICATOR_VPAD = 7.dp
private val INDICATOR_INSET_START = 2.dp

@Composable
fun ProjectListItem(
    projectData: ProjectData,
    isSelected: Boolean,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = JewelTheme.simpleListItemStyle

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Берём OPAQUE цвета из UI defaults (как делает платформа для списков),
    // чтобы SwingPanel не проваливался в чёрный на alpha.
    val panelBgAwt = JBColor.PanelBackground
    val selectedBgAwt = JBColor.namedColor("List.selectionBackground", panelBgAwt)
    val hoverBgAwt = JBColor.namedColor("List.hoverBackground", selectedBgAwt)

    val selectedBg = selectedBgAwt.toCompose()
    val hoverBg = hoverBgAwt.toCompose()

    val itemBackground: Color = when {
        isSelected -> selectedBg
        isHovered -> hoverBg
        else -> Color.Transparent
    }

    val iconBgAwt = when {
        isSelected -> selectedBgAwt
        isHovered -> hoverBgAwt
        else -> panelBgAwt
    }

    val shape = RoundedCornerShape(style.metrics.selectionBackgroundCornerSize)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(JewelTheme.globalMetrics.rowHeight)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        CurrentProjectIndicator(
            visible = isCurrent,
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
                .background(itemBackground, shape)
                .padding(style.metrics.innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProjectIcon(icon = projectData.icon, background = iconBgAwt)
            Spacer(Modifier.width(6.dp))

            Text(projectData.displayName, maxLines = 1)
            Spacer(Modifier.weight(1f))

            projectData.branch?.let { branch ->
                Text(branch, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CurrentProjectIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val c = JewelTheme.globalColors.text.normal.copy(alpha = 0.85f)

    Canvas(modifier = modifier) {
        val x = size.width / 2f
        drawLine(
            color = c,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = INDICATOR_STROKE.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ProjectIcon(icon: Icon?, background: java.awt.Color) {
    SwingPanel(
        modifier = Modifier.size(ICON_SIZE_DP.dp),
        factory = {
            JLabel(icon).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isOpaque = true              // важно: иначе фон не будет рисоваться
                this.background = background // OPAQUE фон => нет чёрного “под” иконкой
                border = null
                text = null
            }
        },
        update = { label ->
            label.icon = icon
            label.background = background
        }
    )
}

private fun java.awt.Color.toCompose(): Color = Color(this.rgb)
