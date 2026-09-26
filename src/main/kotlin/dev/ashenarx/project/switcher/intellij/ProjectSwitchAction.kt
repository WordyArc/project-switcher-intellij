package dev.ashenarx.project.switcher.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.productMode.IdeProductMode

class ProjectSwitchAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = IdeProductMode.isMonolith
    }

    override fun actionPerformed(e: AnActionEvent) {
        service<ProjectSwitcherPopupService>().toggle(e.project)
    }

    companion object {
        const val ID = "ProjectSwitcher.SwitchProject"
    }
}
