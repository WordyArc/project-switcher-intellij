@file:Suppress("UnstableApiUsage")

package dev.ashenarx.project.switcher.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import dev.ashenarx.project.switcher.intellij.ui.POPUP_HEIGHT
import dev.ashenarx.project.switcher.intellij.ui.POPUP_WIDTH
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherModel
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherPopup
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherPopupTracker
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.Dimension
import javax.swing.JComponent

class ProjectSwitchAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val tracker = service<ProjectSwitcherPopupTracker>()
        if (tracker.closeIfOpen()) return

        val currentProject = e.project
        val model = ProjectSwitcherModel(currentProject)

        var popup: JBPopup? = null

        // Opening a project has to happen *after* the popup is gone, otherwise the new frame fights
        // the closing one for focus. setFinalRunnable is the platform's hook for exactly that.
        var onClosed: (() -> Unit)? = null

        val panel = JewelComposePanel {
            SwingBridgeTheme {
                ProjectSwitcherPopup(
                    model = model,
                    onClose = { popup?.cancel() },
                    onSelectOpen = { item ->
                        onClosed = { ProjectOpener.getInstance().focus(item) }
                        popup?.cancel()
                    },
                    onSelectRecent = { item, target ->
                        onClosed = {
                            ProjectOpener.getInstance().reopen(item, target, currentProject)
                        }
                        popup?.cancel()
                    },
                )
            }
        }.apply {
            preferredSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
        }

        popup = createPopup(panel).also {
            it.setFinalRunnable { onClosed?.invoke() }
            tracker.register(it)
            it.showCenteredInCurrentWindow(currentProject ?: ProjectManager.getInstance().defaultProject)
        }

        model.load()
    }

    private fun createPopup(panel: JComponent): JBPopup =
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelKeyEnabled(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setMovable(false)
            .setResizable(false)
            .createPopup()
}
