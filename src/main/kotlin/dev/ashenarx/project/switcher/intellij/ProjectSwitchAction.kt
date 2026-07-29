package dev.ashenarx.project.switcher.intellij

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.intellij.ide.DataManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.wm.WindowManager
import dev.ashenarx.project.switcher.intellij.data.ProjectData
import dev.ashenarx.project.switcher.intellij.data.ProjectDataService
import dev.ashenarx.project.switcher.intellij.data.ProjectsData
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherPopup
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.Component
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent

class ProjectSwitchAction : DumbAwareAction("Switch Project") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (closePopupIfOpen()) return

        val currentProject = e.project
        val place = e.place.ifBlank { ActionPlaces.UNKNOWN }

        val projectsState = mutableStateOf(ProjectsData.EMPTY)

        val panel = createPopupPanel(projectsState, place, currentProject)
        val popup = createPopup(panel)

        currentPopup = popup
        popup.addListener(PopupCloseListener())
        popup.showCenteredInCurrentWindow(currentProject ?: ProjectManager.getInstance().defaultProject)

        loadProjectsAsync(
            currentProject = currentProject,
            popup = popup,
            projectsState = projectsState
        )
    }

    private fun closePopupIfOpen(): Boolean {
        val popup = currentPopup ?: return false
        if (popup.isDisposed) return false
        popup.cancel()
        return true
    }

    private fun createPopupPanel(
        projectsState: MutableState<ProjectsData>,
        place: String,
        currentProject: Project?
    ): JComponent {
        return JewelComposePanel {
            SwingBridgeTheme {
                ProjectSwitcherPopup(
                    projectsData = projectsState.value,
                    onClose = { currentPopup?.cancel() },
                    onSelectOpen = { openProject ->
                        currentPopup?.cancel()
                        ProjectUtil.focusProjectWindow(openProject.project, true)
                    },
                    onSelectRecent = { recentProject, modifiersEx ->
                        val popup = currentPopup
                        popup?.cancel()

                        ApplicationManager.getApplication().invokeLater(
                            {
                                if (popup != null && popup.isDisposed) {
                                    executeReopenAction(
                                        recentProject = recentProject,
                                        place = place,
                                        modifiersEx = modifiersEx,
                                        currentProject = currentProject
                                    )
                                }
                            },
                            ModalityState.any()
                        )
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

    private fun loadProjectsAsync(
        currentProject: Project?,
        popup: JBPopup,
        projectsState: MutableState<ProjectsData>
    ) {
        val app = ApplicationManager.getApplication()

        val probeProject: Project? =
            currentProject ?: ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }

        val isDumbNow = probeProject?.let { DumbService.isDumb(it) } == true

        fun publish(data: ProjectsData) {
            app.invokeLater(
                {
                    if (!popup.isDisposed) {
                        projectsState.value = data
                        if (data.isEmpty) currentPopup?.cancel()
                    }
                },
                ModalityState.any()
            )
        }

        app.executeOnPooledThread {
            if (isDumbNow) {
                val fast = ProjectDataService.collectProjectsData(
                    currentProject = currentProject,
                    includeBranch = false,
                    includeIcon = false
                )
                publish(fast)
            }

            val full = ProjectDataService.collectProjectsData(
                currentProject = currentProject,
                includeBranch = true,
                includeIcon = true
            )
            publish(full)
        }
    }

    private fun executeReopenAction(
        recentProject: ProjectData.Recent,
        place: String,
        modifiersEx: Int,
        currentProject: Project?
    ) {
        val contextComponent = getIdeContextComponent(currentProject) ?: return
        val dataContext = DataManager.getInstance().getDataContext(contextComponent)

        val inputEvent: InputEvent? =
            if (modifiersEx != 0) {
                KeyEvent(
                    contextComponent,
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    modifiersEx,
                    KeyEvent.VK_ENTER,
                    '\n'
                )
            } else null

        ActionUtil.invokeAction(
            recentProject.action,
            dataContext,
            place.ifBlank { ActionPlaces.UNKNOWN },
            inputEvent,
            null
        )
    }

    private fun getIdeContextComponent(currentProject: Project?): Component? {
        val frame = currentProject?.let { WindowManager.getInstance().getFrame(it) }
        if (frame != null) return frame.rootPane

        return ProjectUtil.getActiveFrameOrWelcomeScreen()
    }

    private inner class PopupCloseListener : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
            currentPopup = null
        }
    }

    companion object {
        private const val POPUP_WIDTH = 380
        private const val POPUP_HEIGHT = 420

        @Volatile
        private var currentPopup: JBPopup? = null
    }
}
