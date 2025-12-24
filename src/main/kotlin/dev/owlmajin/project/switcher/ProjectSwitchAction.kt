package dev.owlmajin.project.switcher

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import dev.owlmajin.project.switcher.data.ProjectData
import dev.owlmajin.project.switcher.data.ProjectDataService
import dev.owlmajin.project.switcher.data.ProjectsData
import dev.owlmajin.project.switcher.ui.ProjectSwitcherPopup
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JComponent

/**
 * Action to show project switcher popup.
 *
 * Displays a list of open and recent projects, allows navigation and switching between them.
 */
class ProjectSwitchAction : DumbAwareAction("Switch Project") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        if (closePopupIfOpen()) return

        val currentProject = e.project
        val projectsData = ProjectDataService.collectProjectsData(currentProject)

        if (projectsData.isEmpty) return

        val panel = createPopupPanel(projectsData, e.place)
        val popup = createPopup(panel)

        currentPopup = popup
        popup.addListener(PopupCloseListener())
        popup.showCenteredInCurrentWindow(currentProject ?: ProjectManager.getInstance().defaultProject)
    }

    private fun closePopupIfOpen(): Boolean {
        val popup = currentPopup ?: return false
        if (popup.isDisposed) return false

        popup.cancel()
        return true
    }

    private fun createPopupPanel(projectsData: ProjectsData, place: String): JComponent {
        return JewelComposePanel {
            SwingBridgeTheme {
                ProjectSwitcherPopup(
                    projectsData = projectsData,
                    onClose = { currentPopup?.cancel() },
                    onSelectOpen = { openProject ->
                        currentPopup?.cancel()
                        ProjectUtil.focusProjectWindow(openProject.project, true)
                    },
                    onSelectRecent = { recentProject, modifiersEx ->
                        currentPopup?.cancel()
                        executeReopenAction(recentProject, place, modifiersEx)
                    }
                )
            }
        }.apply {
            preferredSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
        }
    }

    private fun createPopup(panel: JComponent): JBPopup {
        return JBPopupFactory.getInstance()
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

    private fun executeReopenAction(
        recentProject: ProjectData.Recent,
        place: String,
        modifiersEx: Int
    ) {
        val contextComponent = getContextComponent() ?: return

        val inputEvent = KeyEvent(
            contextComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            modifiersEx,
            KeyEvent.VK_ENTER,
            '\n'
        )

        ActionManager.getInstance().tryToExecute(
            recentProject.action,
            inputEvent,
            contextComponent,
            place.ifBlank { ActionPlaces.UNKNOWN },
            true
        )
    }

    private fun getContextComponent(): Component? {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            ?: currentPopup?.content
    }

    private inner class PopupCloseListener : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
            currentPopup = null
        }
    }

    companion object {
        private const val POPUP_WIDTH = 450
        private const val POPUP_HEIGHT = 300

        @Volatile
        private var currentPopup: JBPopup? = null
    }
}

