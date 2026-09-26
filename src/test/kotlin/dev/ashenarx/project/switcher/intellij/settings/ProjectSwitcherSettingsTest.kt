package dev.ashenarx.project.switcher.intellij.settings

import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ProjectSwitcherSettingsTest {

    @Test
    fun `the defaults keep the classic popup`() {
        val options = ProjectSwitcherSettings.Options()

        assertFalse(options.searchField, "the classic popup has a title and a speed search, not a field")
        assertFalse(options.showLocation, "the classic popup has no footer")
        assertFalse(options.showShortcuts, "the classic popup has no footer")
    }

    @Test
    fun `the options survive a save and a load`() {
        val options = ProjectSwitcherSettings.Options().apply {
            searchField = true
            showShortcuts = true
        }

        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(options), ProjectSwitcherSettings.Options::class.java)

        assertEquals(options, restored)
    }
}
