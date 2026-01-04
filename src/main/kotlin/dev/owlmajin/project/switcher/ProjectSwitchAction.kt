package dev.owlmajin.project.switcher

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.*
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

class ProjectSwitchAction : DumbAwareAction("Switch Project") {

    // Важно: update должен быть максимально дешёвым и не блокироваться на фоне.
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (closePopupIfOpen()) return

        val currentProject = e.project
        val place = e.place

        val projectsState = mutableStateOf(ProjectsData.EMPTY)

        val panel = createPopupPanel(projectsState, place)
        val popup = createPopup(panel)

        currentPopup = popup
        popup.addListener(PopupCloseListener())
        popup.showCenteredInCurrentWindow(currentProject ?: ProjectManager.getInstance().defaultProject)

        // грузим данные после показа popup - главное, не блокировать EDT
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
        place: String
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

    private fun loadProjectsAsync(
        currentProject: Project?,
        popup: JBPopup,
        projectsState: MutableState<ProjectsData>
    ) {
        val app = ApplicationManager.getApplication()

        val dumbProbeProject: Project? =
            currentProject ?: ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }

        val isDumbNow = dumbProbeProject?.let { DumbService.isDumb(it) } == true

        fun publish(data: ProjectsData) {
            app.invokeLater(
                {
                    if (!popup.isDisposed) {
                        projectsState.value = data
                        // если реально нечего показывать — можно закрыть автоматически
                        if (data.isEmpty) currentPopup?.cancel()
                    }
                },
                ModalityState.any()
            )
        }

        // 1) Быстрый путь: без иконок/веток (почти не зависит от индексации).
        if (isDumbNow) {
            app.executeOnPooledThread {
                val fast = ProjectDataService.collectProjectsData(
                    currentProject = currentProject,
                    includeBranch = false,
                    includeIcon = false
                )
                publish(fast)

                // 2) Когда станет smart — догружаем декорации (ветки+иконки)
                dumbProbeProject?.let { p ->
                    DumbService.getInstance(p).runWhenSmart {
                        app.executeOnPooledThread {
                            val full = ProjectDataService.collectProjectsData(
                                currentProject = currentProject,
                                includeBranch = true,
                                includeIcon = true
                            )
                            publish(full)
                        }
                    }
                }
            }
        } else {
            // Обычный путь: сразу грузим всё, но всё равно в фоне.
            app.executeOnPooledThread {
                val full = ProjectDataService.collectProjectsData(
                    currentProject = currentProject,
                    includeBranch = true,
                    includeIcon = true
                )
                publish(full)
            }
        }
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
        private const val POPUP_WIDTH = 380
        private const val POPUP_HEIGHT = 420

        @Volatile
        private var currentPopup: JBPopup? = null
    }
}
