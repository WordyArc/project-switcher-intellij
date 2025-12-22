package dev.owlmajin.project.switcher

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
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

    companion object {
        @Volatile
        private var currentPopup: JBPopup? = null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getCurrentBranchByPath(path: String): String? {
        return try {
            val gitDir = File(path, ".git")
            if (!gitDir.exists()) return null

            val headFile = File(gitDir, "HEAD")
            if (!headFile.exists()) return null

            val headContent = headFile.readText().trim()
            when {
                headContent.startsWith("ref: refs/heads/") ->
                    headContent.substring("ref: refs/heads/".length)
                headContent.length >= 7 ->
                    headContent.substring(0, 7)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        // Если popup уже открыт, закрываем его (toggle behavior)
        val existingPopup = currentPopup
        if (existingPopup != null && !existingPopup.isDisposed) {
            existingPopup.cancel()
            return
        }

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

        val recentManager = RecentProjectsManagerBase.getInstanceEx()

        val items: List<SwitcherItem> = buildList {
            if (openProjects.isNotEmpty()) {
                openProjects.forEach { p ->
                    val pPath = p.basePath?.let { FileUtil.toSystemIndependentName(it) }
                    val branch = pPath?.let { getCurrentBranchByPath(it) }
                    val icon = pPath?.let { recentManager.getProjectIcon(it, true, 20) }
                    add(
                        SwitcherItem.OpenProjectItem(
                            project = p,
                            isCurrent = (currentProject != null && currentProject == p),
                            branch = branch,
                            icon = icon
                        )
                    )
                }
            }

            if (recentMeta.isNotEmpty()) {
                add(SwitcherItem.Header("Recent"))
                recentMeta.forEach { (_, path, action) ->
                    val branch = getCurrentBranchByPath(path)
                    add(SwitcherItem.RecentProjectItem(action = action, branch = branch))
                }
            }
        }

        if (items.none { it is SwitcherItem.OpenProjectItem || it is SwitcherItem.RecentProjectItem }) return

        val panel = JewelComposePanel {
            SwingBridgeTheme {
                ProjectSwitcherPopup(
                    items = items,
                    currentProjectName = currentProject?.name,
                    onClose = { currentPopup?.cancel() },
                    onSelectOpen = { p ->
                        currentPopup?.cancel()
                        ProjectUtil.focusProjectWindow(p, true)
                    },
                    onSelectRecent = { reopenAction, modifiersEx ->
                        currentPopup?.cancel()

                        val src: Component? =
                            (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? Component)
                                ?: (currentPopup?.content as? Component)

                        if (src != null) {
                            executeReopen(reopenAction, place, src, modifiersEx)
                        }
                    }
                )
            }
        }.apply {
            preferredSize = Dimension(450, 300)
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

        // Устанавливаем currentPopup ПЕРЕД показом
        currentPopup = popup

        popup.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
            override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                if (currentPopup == popup) {
                    currentPopup = null
                }
            }
        })

        popup.showCenteredInCurrentWindow(e.project ?: ProjectManager.getInstance().defaultProject)
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
