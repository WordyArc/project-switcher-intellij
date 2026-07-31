package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManager
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
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
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
        val project = openProjectOf(item) ?: return

        ProjectUtil.focusProjectWindow(project, true)
    }

    /** Mirrors the platform action so frame bounds and `canClose` handlers are preserved. */
    fun close(item: ProjectItem.Open) {
        val project = openProjectOf(item) ?: return

        WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(project)
        WriteIntentReadAction.run { ProjectManager.getInstance().closeAndDispose(project) }

        // closeAndDispose cannot distinguish this from an application exit, so do its UI cleanup.
        RecentProjectsManager.getInstance().updateLastProjectPath()
        WelcomeFrame.showIfNoProjectOpened()
    }

    private fun openProjectOf(item: ProjectItem.Open): Project? =
        ProjectManager.getInstance().openProjects
            .firstOrNull { !it.isDisposed && it.locationHash == item.locationHash }

    /**
     * ReopenProjectAction cannot force reuse of the current frame. Opening through the manager gives
     * [OpenTarget.CurrentWindow] access to [OpenProjectTask.forceReuseFrame].
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
