package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.APP)
class ProjectOpener(private val coroutineScope: CoroutineScope) {

    fun focus(item: ProjectItem.Open) {
        val project = openProjectOf(item) ?: return

        ProjectUtil.focusProjectWindow(project, true)
    }

    fun close(item: ProjectItem.Open): Boolean {
        val project = openProjectOf(item) ?: return false

        WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(project)
        val closed = WriteIntentReadAction.compute { ProjectManager.getInstance().closeAndDispose(project) }
        if (!closed) return false

        // closeAndDispose cannot distinguish this from an application exit, so do its UI cleanup.
        RecentProjectsManager.getInstance().updateLastProjectPath()
        WelcomeFrame.showIfNoProjectOpened()
        return true
    }

    private fun openProjectOf(item: ProjectItem.Open): Project? =
        ProjectManager.getInstance().openProjects
            .firstOrNull { !it.isDisposed && it.locationHash == item.locationHash }

    // Not ReopenProjectAction: it cannot force reuse of the current frame.
    fun reopen(item: ProjectItem.Recent, target: OpenTarget, contextProject: Project?) {
        val modality = ModalityState.current()

        coroutineScope.launch {
            runCatching {
                val file = Path.of(item.path).normalize()

                if (!item.path.isLocalProjectPath() || withContext(Dispatchers.IO) { Files.notExists(file) }) {
                    // For a stale entry the action is still worth it: its dialog offers to drop the entry.
                    val handled = withContext(Dispatchers.EDT + modality.asContextElement()) {
                        performReopenAction(item, contextProject)
                    }
                    if (!handled) notifyOpenFailure(item.displayName, contextProject, modality)
                    return@runCatching
                }

                val options = OpenProjectTaskFactory.create(
                    contextProject,
                    target == OpenTarget.NewWindow,
                    target == OpenTarget.CurrentWindow,
                )

                val opened = openProject(file, options)
                if (opened == null) notifyOpenFailure(item.displayName, contextProject, modality)
            }.getOrHandleException { error ->
                thisLogger().warn("Cannot open project '${item.displayName}'", error)
                notifyOpenFailure(item.displayName, contextProject, modality)
            }
        }
    }

    private suspend fun openProject(file: Path, options: OpenProjectTask): Project? {
        val recentManager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
        if (recentManager != null) return recentManager.openProject(file, options)

        thisLogger().debug("RecentProjectsManager is not a RecentProjectsManagerBase — opening without its metadata")
        return ProjectManagerEx.getInstanceEx().openProjectAsync(file, options)
    }

    private fun performReopenAction(item: ProjectItem.Recent, contextProject: Project?): Boolean {
        val action = findReopenAction(item.path) ?: return false

        val contextComponent = contextComponent(contextProject) ?: return false
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
        return true
    }

    private suspend fun notifyOpenFailure(
        displayName: String,
        contextProject: Project?,
        modality: ModalityState,
    ) {
        withContext(Dispatchers.EDT + modality.asContextElement()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                    ProjectSwitcherBundle.message("notification.open.failed.title"),
                    ProjectSwitcherBundle.message("notification.open.failed.content", displayName),
                    NotificationType.ERROR,
                )
                .notify(contextProject?.takeUnless { it.isDisposed })
        }
    }

    private fun findReopenAction(path: String): ReopenProjectAction? =
        recentProjectActions().filterIsInstance<ReopenProjectAction>().firstOrNull { it.normalizedPath == path }

    private fun contextComponent(contextProject: Project?): Component? {
        val frame = contextProject?.takeIf { !it.isDisposed }
            ?.let { WindowManager.getInstance().getFrame(it) }

        return frame?.rootPane ?: ProjectUtil.getActiveFrameOrWelcomeScreen()
    }

    companion object {
        internal const val NOTIFICATION_GROUP_ID = "Project Switcher"

        fun getInstance(): ProjectOpener = service()
    }
}
