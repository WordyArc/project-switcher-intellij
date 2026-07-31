@file:Suppress("UnstableApiUsage")

package dev.ashenarx.project.switcher.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import dev.ashenarx.project.switcher.intellij.service.RecentProjectsService
import dev.ashenarx.project.switcher.intellij.ui.POPUP_HEIGHT
import dev.ashenarx.project.switcher.intellij.ui.POPUP_WIDTH
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherModel
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherPopup
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherPopupTracker
import kotlinx.coroutines.cancel
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
        val popupScope = RecentProjectsService.getInstance().coroutineScope.childScope("Project Switcher popup")
        val model = ProjectSwitcherModel(currentProject, popupScope)

        var popup: JBPopup? = null

        // Opening before the popup closes causes the old and new frames to fight for focus.
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
                    onCloseCurrent = { item ->
                        onClosed = { ProjectOpener.getInstance().close(item) }
                        popup?.cancel()
                    },
                )
            }
        }.apply {
            preferredSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
        }

        popup = createPopup(panel).also {
            Disposer.register(it) { popupScope.cancel("Project Switcher popup disposed") }
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
            // Compose speed search clears its query on the first Escape and closes on the next one.
            .setCancelKeyEnabled(false)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setMovable(false)
            .setResizable(false)
            .createPopup()
}
