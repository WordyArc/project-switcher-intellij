package dev.owlmajin.project.switcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

private val BAR_HEIGHT = 28.dp
private val H_PADDING = 8.dp

private val ICON_SIZE = 14.dp
private val ICON_GAP = 8.dp

private val CLEAR_HITBOX = 24.dp          // как в New UI
private val CLEAR_ICON_SIZE = 14.dp

private val DIVIDER_H = 1.dp
private val CARET_W = 1.dp
private val CARET_H = 14.dp

@Composable
internal fun ProjectSearchBar(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = JewelTheme.globalColors

    val iconColor = colors.text.disabled.copy(alpha = 0.90f)
    val closeColor = colors.text.disabled.copy(alpha = 0.80f)

    val dividerColor = colors.text.disabled.copy(alpha = 0.18f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .padding(horizontal = H_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchIcon(
                color = iconColor,
                modifier = Modifier.size(ICON_SIZE)
            )

            Spacer(Modifier.width(ICON_GAP))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = query,
                    maxLines = 1,
                    color = colors.text.normal
                )
                BlinkingCaret(
                    color = colors.text.normal.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .width(CARET_W)
                        .height(CARET_H)
                )
            }

            ClearButton(
                color = closeColor,
                onClick = onClear
            )
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(DIVIDER_H)
                .background(dividerColor)
        )
    }
}

@Composable
private fun ClearButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val hoverBgAwt = JBColor.namedColor("ActionButton.hoverBackground", java.awt.Color(0, 0, 0, 0))
    val hoverBg = Color(hoverBgAwt.rgb).copy(alpha = if (hovered) 1f else 0f)

    Box(
        modifier = modifier
            .size(CLEAR_HITBOX)
            .hoverable(interaction)
            .background(hoverBg, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CloseIcon(color = color, modifier = Modifier.size(CLEAR_ICON_SIZE))
    }
}

@Composable
private fun BlinkingCaret(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "caret")
    val alpha by t.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    Box(modifier = modifier.background(color.copy(alpha = alpha)))
}

@Composable
private fun SearchIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.25.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        val r = size.minDimension * 0.34f
        val cx = size.width * 0.46f
        val cy = size.height * 0.46f

        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = stroke
        )

        val handleStart = Offset(cx + r * 0.70f, cy + r * 0.70f)
        val handleEnd = Offset(size.width * 0.96f, size.height * 0.96f)

        drawLine(
            color = color,
            start = handleStart,
            end = handleEnd,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CloseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.15.dp.toPx()
        val inset = size.minDimension * 0.22f

        val a = Offset(inset, inset)
        val b = Offset(size.width - inset, size.height - inset)
        val c = Offset(size.width - inset, inset)
        val d = Offset(inset, size.height - inset)

        drawLine(color, a, b, strokeWidth, cap = StrokeCap.Round)
        drawLine(color, c, d, strokeWidth, cap = StrokeCap.Round)
    }
}
