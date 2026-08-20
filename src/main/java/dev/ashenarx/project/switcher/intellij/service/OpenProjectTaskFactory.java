package dev.ashenarx.project.switcher.intellij.service;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.OpenProjectTaskBuilder;
import com.intellij.openapi.project.Project;
import kotlin.Unit;

final class OpenProjectTaskFactory {
    private OpenProjectTaskFactory() {
    }

    static OpenProjectTask create(Project projectToClose, boolean forceOpenInNewFrame, boolean forceReuseFrame) {
        OpenProjectTaskBuilder builder = new OpenProjectTaskBuilder();
        builder.setProjectToClose(projectToClose);
        builder.setForceOpenInNewFrame(forceOpenInNewFrame);
        builder.setForceReuseFrame(forceReuseFrame);
        builder.setRunConfigurators(true);

        // Keep this call in Java: Kotlin inlines this method in early 262 builds and exposes constructor ABI.
        return builder.build(ignored -> Unit.INSTANCE);
    }
}
