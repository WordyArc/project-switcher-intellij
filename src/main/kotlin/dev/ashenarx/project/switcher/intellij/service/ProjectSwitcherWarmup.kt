package dev.ashenarx.project.switcher.intellij.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class ProjectSwitcherWarmup : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Test mode may run this from a scope that project opening waits on, and the icon passes
        // are long enough to turn that into a timeout.
        if (ApplicationManager.getApplication().isUnitTestMode) return

        ProjectIconLoader.getInstance().warmUp()
    }
}
