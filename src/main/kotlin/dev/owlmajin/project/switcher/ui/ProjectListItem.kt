package dev.owlmajin.project.switcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.owlmajin.project.switcher.data.ProjectData
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import javax.swing.Icon
import javax.swing.JLabel

private const val ICON_SIZE_DP = 20

@Composable
fun ProjectListItem(
    projectData: ProjectData,
    isSelected: Boolean,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val active = isSelected || isHovered

    val titleColor = if (isSelected) JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.normal
    val metaColor = if (isSelected) JewelTheme.globalColors.text.disabledSelected else JewelTheme.globalColors.text.disabled

    SimpleListItem(
        selected = isSelected,
        active = active,
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurrentDot(isCurrent = isCurrent)
            Spacer(Modifier.width(8.dp))

            ProjectIcon(projectData.icon)
            Spacer(Modifier.width(8.dp))

            Text(
                text = projectData.displayName,
                color = titleColor,
                modifier = Modifier.weight(1f)
            )

            projectData.branch?.let { branch ->
                Spacer(Modifier.width(10.dp))
                BranchLabel(branch = branch, color = metaColor)
            }
        }
    }
}


@Composable
private fun CurrentDot(isCurrent: Boolean) {
    // маленький маркер "текущего" проекта; можно убрать, если не нужен
    val dotColor = if (isCurrent) JewelTheme.globalColors.text.info else Color.Transparent
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
private fun SelectionIndicator(isSelected: Boolean, isCurrent: Boolean) {
    val prefix = when {
        isSelected -> "> "
        isCurrent -> "* "
        else -> "  "
    }
    Text(prefix)
}

@Composable
private fun ProjectIcon(icon: Icon?) {
    SwingPanel(
        modifier = Modifier.size(ICON_SIZE_DP.dp),
        factory = {
            JLabel(icon).apply {
                isOpaque = false
                border = null
            }
        },
        update = { label ->
            label.icon = icon
        }
    )
}

@Composable
private fun BranchLabel(branch: String, color: Color) {
    Text(
        text = branch,
        color = color,
        fontSize = 11.sp
    )
}


