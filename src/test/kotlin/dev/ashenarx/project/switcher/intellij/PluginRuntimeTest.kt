package dev.ashenarx.project.switcher.intellij

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import dev.ashenarx.project.switcher.intellij.service.ProjectCatalog
import dev.ashenarx.project.switcher.intellij.service.ProjectIconLoader
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class PluginRuntimeTest {

    @Test
    fun `the notification group the opener reports failures through is registered`() {
        assertNotNull(
            NotificationGroupManager.getInstance().getNotificationGroup("Project Switcher"),
            "notificationGroup id in plugin.xml no longer matches ProjectOpener.NOTIFICATION_GROUP_ID",
        )
    }

    @Test
    fun `the switch action is registered under the id the keymap binds`() {
        val action = ActionManager.getInstance().getAction("ProjectSwitcher.SwitchProject")

        assertNotNull(action, "action id in plugin.xml changed")
        assertEquals(ProjectSwitchAction::class.java, action.javaClass)
    }

    @Test
    fun `application services resolve and are singletons`() {
        assertSame(ProjectCatalog.getInstance(), ProjectCatalog.getInstance())
        assertSame(ProjectIconLoader.getInstance(), ProjectIconLoader.getInstance())
        assertSame(ProjectOpener.getInstance(), ProjectOpener.getInstance())
        assertNotNull(service<ProjectSwitcherPopupService>())
    }

    @Test
    fun `every bundle key the code asks for exists`() {
        val keys = listOf(
            "popup.title",
            "popup.section.recent",
            "popup.empty",
            "popup.error",
            "popup.loading",
            "notification.group.name",
            "notification.open.failed.title",
        )

        for (key in keys) {
            val message = ProjectSwitcherBundle.message(key)
            assertEquals(false, message.startsWith("!"), "missing bundle key: $key")
        }

        assertEquals(
            "Project 'demo' could not be opened.",
            ProjectSwitcherBundle.message("notification.open.failed.content", "demo"),
        )
    }
}
