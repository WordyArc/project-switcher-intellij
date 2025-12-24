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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import dev.owlmajin.project.switcher.data.ProjectData
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

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

    val selectedBg = style.colors.backgroundSelectedActive
    val panelBg = JewelTheme.globalColors.panelBackground

    val itemBackground: Color = when {
        isSelected -> selectedBg
        isHovered -> selectedBg.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    val shape = RoundedCornerShape(style.metrics.selectionBackgroundCornerSize)

    // ВАЖНО: Swing не любит полупрозрачные backgrounds как у Compose.
    // Поэтому для Swing-иконки всегда даём ОПАКОВЫЙ фон:
    // - если строка прозрачная -> panelBg
    // - если ховер/селект -> композитим поверх panelBg и получаем alpha=1
    val iconBackground: Color = if (itemBackground.alpha == 0f) {
        panelBg
    } else {
        compositeOverOpaque(itemBackground, panelBg)
    }

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
        // Абсолютный индикатор: не занимает место, просто рисуется поверх
        CurrentProjectIndicator(
            visible = isCurrent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = INDICATOR_INSET_START, top = INDICATOR_VPAD, bottom = INDICATOR_VPAD)
                .width(INDICATOR_WIDTH)
                .fillMaxHeight()
        )

        // Карточка (фон/скругления как в SimpleListItemStyle)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(style.metrics.outerPadding)
                .background(itemBackground, shape)
                .padding(style.metrics.innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProjectIcon(icon = projectData.icon, background = iconBackground)
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

/**
 * Самый простой Swing-рендер, но без "чёрного фона":
 * делаем свой opaque JPanel-контейнер и задаём background ему (и label'у как свойство).
 */
@Composable
private fun ProjectIcon(icon: Icon?, background: Color) {
    val awtBg = java.awt.Color(background.toArgb(), true)

    SwingPanel(
        modifier = Modifier.size(ICON_SIZE_DP.dp),
        factory = {
            IconHost().apply {
                setBg(awtBg)
                label.icon = icon
            }
        },
        update = { host ->
            host.setBg(awtBg)
            host.label.icon = icon
        }
    )
}

private class IconHost : JPanel(BorderLayout()) {
    val label = JLabel().apply {
        isOpaque = false
        border = null
    }

    init {
        isOpaque = true
        border = null
        add(label, BorderLayout.CENTER)
    }

    fun setBg(c: java.awt.Color) {
        background = c
        label.background = c
        repaint()
    }
}

private fun compositeOverOpaque(fg: Color, bg: Color): Color {
    val a = fg.alpha.coerceIn(0f, 1f)
    val inv = 1f - a
    val r = fg.red * a + bg.red * inv
    val g = fg.green * a + bg.green * inv
    val b = fg.blue * a + bg.blue * inv
    return Color(r, g, b, 1f)
}
