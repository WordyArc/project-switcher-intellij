package dev.ashenarx.project.switcher.intellij

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import dev.ashenarx.project.switcher.intellij.service.ProjectCatalog
import dev.ashenarx.project.switcher.intellij.service.ProjectIconLoader
import dev.ashenarx.project.switcher.intellij.service.ProjectOpener
import dev.ashenarx.project.switcher.intellij.settings.ProjectSwitcherConfigurable
import dev.ashenarx.project.switcher.intellij.ui.HINT_LABEL_KEYS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@TestApplication
class PluginRuntimeTest {

    @Test
    fun `the notification group the opener reports failures through is registered`() {
        assertNotNull(
            NotificationGroupManager.getInstance().getNotificationGroup(ProjectOpener.NOTIFICATION_GROUP_ID),
            "notificationGroup id in plugin.xml no longer matches ProjectOpener.NOTIFICATION_GROUP_ID",
        )
    }

    @Test
    fun `the switch action is registered under the id the keymap binds`() {
        val action = ActionManager.getInstance().getAction(ProjectSwitchAction.ID)

        assertNotNull(action, "action id in plugin.xml no longer matches ProjectSwitchAction.ID")
        assertEquals(ProjectSwitchAction::class.java, action.javaClass)
    }

    @Test
    fun `the switch action is available in a regular IDE`() {
        val action = ActionManager.getInstance().getAction(ProjectSwitchAction.ID)
        val event = TestActionEvent.createTestEvent(action)

        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible, "only a remote development host or client may hide it")
    }

    @Test
    fun `the popup reads its toggle shortcut from the active keymap`() {
        assertEquals(
            listOf(KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.ALT_DOWN_MASK)),
            keymapShortcutsOf(ProjectSwitchAction.ID),
            "the default keymap binds the action to alt-F2 in plugin.xml",
        )
    }

    @Test
    fun `the settings page is registered under its id`() {
        val page = Configurable.APPLICATION_CONFIGURABLE.extensionList.singleOrNull { it.id == ProjectSwitcherConfigurable.ID }

        assertNotNull(page, "configurable id in plugin.xml no longer matches ProjectSwitcherConfigurable.ID")
        assertEquals(ProjectSwitcherConfigurable::class.java.name, page!!.instanceClass)
        assertEquals("Project Switcher", page.getDisplayName(), "the name comes from the bundle key in plugin.xml")
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
            "popup.search.placeholder",
            "popup.section.recent",
            "popup.empty",
            "popup.error",
            "popup.loading",
            "notification.group.name",
            "notification.open.failed.title",
            "settings.display.name",
            "settings.group.popup",
            "settings.search.field",
            "settings.search.field.comment",
            "settings.show.location",
            "settings.show.shortcuts",
        ) + HINT_LABEL_KEYS

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
