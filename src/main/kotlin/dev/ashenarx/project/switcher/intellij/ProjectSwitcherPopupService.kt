@file:Suppress("UnstableApiUsage")

package dev.ashenarx.project.switcher.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.util.coroutines.childScope
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import dev.ashenarx.project.switcher.intellij.service.ProjectCatalog
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import dev.ashenarx.project.switcher.intellij.ui.DEFAULT_POPUP_SIZE
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherModel
import dev.ashenarx.project.switcher.intellij.ui.ProjectSwitcherPopup
import dev.ashenarx.project.switcher.intellij.ui.popupSizeFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.Dimension
import javax.swing.JComponent

@Service(Service.Level.APP)
internal class ProjectSwitcherPopupService(private val coroutineScope: CoroutineScope) {

    private val tracker = ProjectSwitcherPopupTracker()

    fun toggle(currentProject: Project?) {
        if (tracker.closeIfOpen()) return

        show(currentProject)
    }

    private fun show(currentProject: Project?) {
        val popupScope = coroutineScope.childScope("Project Switcher popup")
        val model = ProjectSwitcherModel(currentProject, popupScope)

        var popup: JBPopup? = null

        // Acting before the popup closes causes the old and new frames to fight for focus.
        var outcome: SwitchOutcome? = null

        val panel = JewelComposePanel {
            SwingBridgeTheme {
                ProjectSwitcherPopup(
                    model = model,
                    onClose = { popup?.cancel() },
                    onResult = { result ->
                        outcome = result
                        popup?.cancel()
                    },
                )
            }
        }.apply {
            preferredSize = preferredPopupSize(currentProject)
        }

        popup = createPopup(panel).also {
            Disposer.register(it) { popupScope.cancel("Project Switcher popup disposed") }
            it.setFinalRunnable { outcome?.let { result -> perform(result, currentProject) } }
            tracker.register(it)
            ProjectCatalog.getInstance().onRecentProjectsChanged(it) { model.refresh() }
            it.showCenteredInCurrentWindow(currentProject ?: ProjectManager.getInstance().defaultProject)
        }

        model.load()
    }

    private fun perform(outcome: SwitchOutcome, contextProject: Project?) {
        val opener = ProjectOpener.getInstance()

        when (outcome) {
            is SwitchOutcome.Focus -> opener.focus(outcome.project)
            is SwitchOutcome.Reopen -> opener.reopen(outcome.project, outcome.target, contextProject)
            is SwitchOutcome.CloseCurrent -> opener.close(outcome.project)
        }
    }

    private fun preferredPopupSize(currentProject: Project?): Dimension {
        val windowManager = WindowManager.getInstance()
        val window = windowManager.mostRecentFocusedWindow ?: windowManager.getFrame(currentProject)

        return window?.size?.let(::popupSizeFor) ?: DEFAULT_POPUP_SIZE
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
