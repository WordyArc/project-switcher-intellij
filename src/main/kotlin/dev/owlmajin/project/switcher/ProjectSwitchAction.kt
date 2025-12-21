package dev.owlmajin.project.switcher

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ProjectSwitchAction : DumbAwareAction("Switch Project") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val currentProject = e.project
        val place = e.place.ifBlank { ActionPlaces.UNKNOWN }

        val openProjects = ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .sortedBy { it.name.lowercase() }

        val openPaths = openProjects.asSequence()
            .mapNotNull { it.basePath ?: it.projectFilePath }
            .map { FileUtil.toSystemIndependentName(it) }
            .toSet()

        val recentActions: List<ReopenProjectAction> =
            RecentProjectsManagerBase.getInstanceEx()
                .getRecentProjectsActions(false)
                .mapNotNull { it as? ReopenProjectAction }

        val recentMeta = recentActions.map { a ->
            val path = FileUtil.toSystemIndependentName(a.projectPath)
            val name: String =
                a.projectName?.takeIf { it.isNotBlank() }
                    ?: a.projectDisplayName?.takeIf { it.isNotBlank() }
                    ?: File(path).name.ifBlank { path }

            Triple(name, path, a)
        }.filter { (_, path, _) -> path.isNotBlank() && path !in openPaths }

        val duplicateNames = recentMeta.groupBy { it.first }.filterValues { it.size > 1 }.keys

        val items: List<SwitcherItem> = buildList {
            if (openProjects.isNotEmpty()) {
                add(SwitcherItem.Header("Open"))
                openProjects.forEach { p ->
                    val pPath = p.basePath?.let { FileUtil.toSystemIndependentName(it) }
                    add(
                        SwitcherItem.OpenProjectItem(
                            project = p,
                            isCurrent = (currentProject != null && currentProject == p),
                            path = pPath
                        )
                    )
                }
            }

            if (recentMeta.isNotEmpty()) {
                add(SwitcherItem.Header("Recent"))
                recentMeta.forEach { (name, path, action) ->
                    add(
                        SwitcherItem.RecentProjectItem(
                            action = action,
                            name = name,
                            path = path,
                            subtitle = if (name in duplicateNames) path else null
                        )
                    )
                }
            }
        }

        if (items.none { it is SwitcherItem.OpenProjectItem || it is SwitcherItem.RecentProjectItem }) return

        val popupRef = AtomicReference<JBPopup?>(null)

        val panel = JewelComposePanel {
            SwingBridgeTheme {
                ProjectSwitcherPopup(
                    items = items,
                    currentProjectName = currentProject?.name,
                    onClose = { popupRef.get()?.cancel() },
                    onSelectOpen = { p ->
                        popupRef.get()?.cancel()
                        ProjectUtil.focusProjectWindow(p, true)
                    },
                    onSelectRecent = { reopenAction, modifiersEx ->
                        popupRef.get()?.cancel()

                        val src: Component? =
                            (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? Component)
                                ?: (popupRef.get()?.content as? Component)

                        if (src != null) {
                            executeReopen(reopenAction, place, src, modifiersEx)
                        }
                    }
                )
            }
        }.apply {
            preferredSize = Dimension(560, 380)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelKeyEnabled(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setMovable(false)
            .setResizable(false)
            .createPopup()

        popupRef.set(popup)
        popup.showInBestPositionFor(e.dataContext)
    }

    private fun executeReopen(
        action: ReopenProjectAction,
        place: String,
        contextComponent: Component,
        modifiersEx: Int
    ) {
        val inputEvent = KeyEvent(
            contextComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            modifiersEx,
            KeyEvent.VK_ENTER,
            '\n'
        )

        ActionManager.getInstance().tryToExecute(
            action,
            inputEvent,
            contextComponent,
            place,
            true
        )
    }
}
