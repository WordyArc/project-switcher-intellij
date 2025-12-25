package dev.owlmajin.project.switcher.ui

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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingConstants

private val SEARCH_ROW_HEIGHT = 32.dp
private val ICON_SIZE = 16.dp

private val CLEAR_HITBOX = 24.dp
private val CLEAR_PADDING = 4.dp
private val CLEAR_CORNER = 6.dp

@Composable
internal fun ProjectSearchBar(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = JewelTheme.globalColors

    // Divider как в speed-search: очень тонкий и малоконтрастный
    val dividerColor = colors.text.disabled.copy(alpha = 0.22f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEARCH_ROW_HEIGHT)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlatformIcon(
                icon = IconLoader.getDisabledIcon(AllIcons.Actions.Search),
                modifier = Modifier.size(ICON_SIZE)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = query,
                maxLines = 1,
                color = colors.text.normal,
                modifier = Modifier.weight(1f)
            )

            ClearButton(onClick = onClear)
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )
    }
}

@Composable
private fun ClearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    // Берём platform defaults для hover-подсветки action button’ов
    val hoverBg = JBColor.namedColor(
        "ActionButton.hoverBackground",
        JBColor(0x14000000, 0x26FFFFFF) // fallback: лёгкий hover в light/dark
    ).toCompose()

    val bg = if (isHovered) hoverBg else Color.Transparent
    val shape = RoundedCornerShape(CLEAR_CORNER)

    // Иконка: в покое “приглушённая”, на hover — обычная (чуть “живее” как в IDE)
    val closeIcon: Icon = if (isHovered) {
        AllIcons.Actions.Close
    } else {
        IconLoader.getDisabledIcon(AllIcons.Actions.Close)
    }

    Box(
        modifier = modifier
            .size(CLEAR_HITBOX)
            .background(bg, shape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(CLEAR_PADDING),
        contentAlignment = Alignment.Center
    ) {
        PlatformIcon(
            icon = closeIcon,
            modifier = Modifier.size(ICON_SIZE)
        )
    }
}

@Composable
private fun PlatformIcon(
    icon: Icon,
    modifier: Modifier = Modifier
) {
    SwingPanel(
        modifier = modifier,
        factory = {
            JLabel(icon).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isOpaque = false
                border = null
                text = null
            }
        },
        update = { label ->
            label.icon = icon
        }
    )
}

private fun java.awt.Color.toCompose(): Color = Color(this.rgb)
