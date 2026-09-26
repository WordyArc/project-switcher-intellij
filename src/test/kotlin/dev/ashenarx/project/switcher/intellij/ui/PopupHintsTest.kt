package dev.ashenarx.project.switcher.intellij.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class PopupHintsTest {

    @Test
    fun `a recent project offers every way to open it and its removal`() {
        assertEquals(
            listOf("popup.hint.open", "popup.hint.current.window", "popup.hint.new.window", "popup.hint.remove"),
            hintsFor(recent("beta")).map { it.labelKey },
        )
    }

    @Test
    fun `an open project offers switching to it and closing it`() {
        assertEquals(listOf("popup.hint.switch", "popup.hint.close"), hintsFor(open("alpha")).map { it.labelKey })
    }

    @Test
    fun `the current project offers only closing, since switching to it does nothing`() {
        assertEquals(listOf("popup.hint.close"), hintsFor(open("alpha", isCurrent = true)).map { it.labelKey })
    }

    @Test
    fun `nothing selected shows no hints`() {
        assertEquals(emptyList<Hint>(), hintsFor(null))
    }

    @Test
    fun `the close hint shows the shortcut the popup reacts to`() {
        val onMac = hintsFor(recent("beta"), mac = true).last().stroke
        val elsewhere = hintsFor(recent("beta"), mac = false).last().stroke

        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.META_DOWN_MASK), onMac)
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), elsewhere)
    }

    @Test
    fun `every hint has a label in the bundle list`() {
        val used = listOf(recent("b"), open("a"), open("c", isCurrent = true))
            .flatMap { hintsFor(it) }
            .map { it.labelKey }
            .toSet()

        assertEquals(used, used.intersect(HINT_LABEL_KEYS.toSet()), "PluginRuntimeTest checks only the keys in HINT_LABEL_KEYS")
    }
}
