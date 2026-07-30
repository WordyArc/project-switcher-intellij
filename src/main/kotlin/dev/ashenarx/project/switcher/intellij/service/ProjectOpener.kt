package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.WindowManager
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path

/** Must be called on the EDT. */
@Service(Service.Level.APP)
class ProjectOpener(private val coroutineScope: CoroutineScope) {

    fun focus(item: ProjectItem.Open) {
        val project = ProjectManager.getInstance().openProjects
            .firstOrNull { !it.isDisposed && it.locationHash == item.locationHash }
            ?: return

        ProjectUtil.focusProjectWindow(project, true)
    }

    /**
     * The project is opened here rather than by replaying [ReopenProjectAction], because that action
     * can only express two of the three outcomes: it reduces the event to "new frame or no
     * preference", and no preference still leaves the platform free to ask "New Window / This
     * Window". [OpenTarget.CurrentWindow] has to suppress that question, and
     * [OpenProjectTask.forceReuseFrame] is the only switch for it.
     *
     * The cost is the action's `ProjectDetector` open-logging and project-group bookkeeping.
     * Everything that decides *how* the project opens, remote (Eel) initialization included, lives
     * below [RecentProjectsManagerBase.openProject] and is unaffected.
     */
    fun reopen(item: ProjectItem.Recent, target: OpenTarget, contextProject: Project?) {
        val file = Path.of(item.path).normalize()

        // The action is still worth replaying for a stale entry: its dialog offers to drop it.
        if (Files.notExists(file)) {
            performReopenAction(item, contextProject)
            return
        }

        val options = OpenProjectTask {
            projectToClose = contextProject
            forceOpenInNewFrame = target == OpenTarget.NewWindow
            forceReuseFrame = target == OpenTarget.CurrentWindow
            runConfigurators = true
        }

        coroutineScope.launch {
            RecentProjectsManagerBase.getInstanceEx().openProject(file, options)
        }
    }

    private fun performReopenAction(item: ProjectItem.Recent, contextProject: Project?) {
        val action = findReopenAction(item.path) ?: return

        val contextComponent = contextComponent(contextProject) ?: return
        val dataContext = DataManager.getInstance().getDataContext(contextComponent)
        val event = AnActionEvent.createEvent(
            action,
            dataContext,
            null,
            ActionPlaces.POPUP,
            ActionUiKind.POPUP,
            null,
        )

        ActionUtil.performAction(action, event)
    }

    private fun findReopenAction(path: String): ReopenProjectAction? =
        RecentProjectListActionProvider.getInstance()
            .getActions()
            .asSequence()
            .filterIsInstance<ReopenProjectAction>()
            .firstOrNull { FileUtil.toSystemIndependentName(it.projectPath) == path }

    private fun contextComponent(contextProject: Project?): Component? {
        val frame = contextProject?.takeIf { !it.isDisposed }
            ?.let { WindowManager.getInstance().getFrame(it) }

        return frame?.rootPane ?: ProjectUtil.getActiveFrameOrWelcomeScreen()
    }

    companion object {
        fun getInstance(): ProjectOpener = service()
    }
}
