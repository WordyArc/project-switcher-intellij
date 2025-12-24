package dev.owlmajin.project.switcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import dev.owlmajin.project.switcher.data.ProjectData
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionIndicator(isSelected = isSelected, isCurrent = isCurrent)

        ProjectIcon(projectData.icon)
        Spacer(Modifier.width(4.dp))

        Text(projectData.displayName)

        projectData.branch?.let { branch ->
            Spacer(Modifier.width(8.dp))
            BranchLabel(branch = branch)
        }
    }
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
private fun BranchLabel(branch: String) {
    Text("[$branch]")
}

