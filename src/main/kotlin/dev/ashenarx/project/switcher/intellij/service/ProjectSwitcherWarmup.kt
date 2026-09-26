package dev.ashenarx.project.switcher.intellij.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class ProjectSwitcherWarmup : ProjectActivity {

    override suspend fun execute(project: Project) {
        // In tests project opening waits on this, and the icon passes turn that into a timeout.
        if (ApplicationManager.getApplication().isUnitTestMode) return

        ProjectIconLoader.getInstance().warmUp()
    }
}
