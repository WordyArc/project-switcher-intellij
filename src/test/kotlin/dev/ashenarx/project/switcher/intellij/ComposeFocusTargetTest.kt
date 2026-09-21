package dev.ashenarx.project.switcher.intellij

import androidx.compose.ui.awt.ComposePanel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
class ComposeFocusTargetTest {

    @Test
    fun `the popup focuses the Compose panel inside the Jewel wrapper, not the wrapper itself`() = runInEdtAndWait {
        val panel = JewelComposePanel { }

        val target = panel.composeFocusTarget()

        assertInstanceOf(ComposePanel::class.java, target, "a JPanel wrapper keeps the focus away from Compose")
    }

    @Test
    fun `a component without Compose content is its own focus target`() {
        val plain = JPanel()

        assertSame(plain, plain.composeFocusTarget())
    }
}
