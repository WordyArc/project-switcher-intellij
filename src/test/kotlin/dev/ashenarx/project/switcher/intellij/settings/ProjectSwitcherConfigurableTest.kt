package dev.ashenarx.project.switcher.intellij.settings

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JComponent

@TestApplication
class ProjectSwitcherConfigurableTest {

    private val settings get() = ProjectSwitcherSettings.getInstance()

    @AfterEach
    fun restoreDefaults() {
        settings.loadState(ProjectSwitcherSettings.Options())
    }

    @Test
    fun `each checkbox applies to its own option`() = runInEdtAndWait {
        val configurable = ProjectSwitcherConfigurable()
        try {
            val panel = checkNotNull(configurable.createComponent())
            configurable.reset()

            panel.checkBox("settings.search.field").doClick()
            panel.checkBox("settings.show.shortcuts").doClick()
            assertTrue(configurable.isModified, "a toggled checkbox must enable Apply")

            configurable.apply()

            assertEquals(
                listOf(true, false, true),
                with(settings.state) { listOf(searchField, showLocation, showShortcuts) },
            )
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }

    @Test
    fun `the page shows what is stored`() = runInEdtAndWait {
        settings.state.showLocation = true
        val configurable = ProjectSwitcherConfigurable()
        try {
            val panel = checkNotNull(configurable.createComponent())
            configurable.reset()

            assertTrue(panel.checkBox("settings.show.location").isSelected)
            assertFalse(panel.checkBox("settings.search.field").isSelected)
        } finally {
            configurable.disposeUIResources()
        }
    }

    private fun JComponent.checkBox(key: String): JCheckBox {
        val text = ProjectSwitcherBundle.message(key)
        return UIUtil.findComponentsOfType(this, JCheckBox::class.java).single { it.text == text }
    }
}
