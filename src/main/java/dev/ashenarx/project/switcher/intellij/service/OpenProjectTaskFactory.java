package dev.ashenarx.project.switcher.intellij.service;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.OpenProjectTaskBuilder;
import com.intellij.openapi.project.Project;
import kotlin.Unit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class OpenProjectTaskFactory {
    private static final MethodType RUN_CONFIGURATORS_SETTER_TYPE =
        MethodType.methodType(void.class, OpenProjectTaskBuilder.class, boolean.class);
    private static final MethodHandle RUN_CONFIGURATORS_SETTER = findRunConfiguratorsSetter();

    private OpenProjectTaskFactory() {
    }

    static OpenProjectTask create(Project projectToClose, boolean forceOpenInNewFrame, boolean forceReuseFrame) {
        OpenProjectTaskBuilder builder = new OpenProjectTaskBuilder();
        builder.setProjectToClose(projectToClose);
        builder.setForceOpenInNewFrame(forceOpenInNewFrame);
        builder.setForceReuseFrame(forceReuseFrame);
        setRunConfigurators(builder);

        // Keep this call in Java: Kotlin inlines this method in early 262 builds and exposes constructor ABI.
        return builder.build(ignored -> Unit.INSTANCE);
    }

    private static void setRunConfigurators(OpenProjectTaskBuilder builder) {
        try {
            RUN_CONFIGURATORS_SETTER.invokeExact(builder, true);
        } catch (Throwable e) {
            throw new IllegalStateException("OpenProjectTaskBuilder.runConfigurators cannot be set", e);
        }
    }

    private static MethodHandle findRunConfiguratorsSetter() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (Class<?> parameter : new Class<?>[]{boolean.class, Boolean.class}) {
            try {
                return lookup
                    .findVirtual(OpenProjectTaskBuilder.class, "setRunConfigurators", MethodType.methodType(void.class, parameter))
                    .asType(RUN_CONFIGURATORS_SETTER_TYPE);
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
            }
        }
        throw new IllegalStateException("OpenProjectTaskBuilder has no runConfigurators setter");
    }
}
