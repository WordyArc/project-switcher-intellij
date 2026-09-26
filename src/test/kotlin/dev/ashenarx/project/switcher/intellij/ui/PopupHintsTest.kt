package dev.ashenarx.project.switcher.intellij.ui

import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PopupHintsTest {

    @Test
    fun `a recent project offers every way to open it and its removal`() {
        assertEquals(
            listOf("popup.hint.open", "popup.hint.current.window", "popup.hint.new.window", "popup.hint.remove"),
            hints(recent("beta")).map { it.labelKey },
        )
    }

    @Test
    fun `an open project offers switching to it and closing it`() {
        assertEquals(listOf("popup.hint.switch", "popup.hint.close"), hints(open("alpha")).map { it.labelKey })
    }

    @Test
    fun `the current project offers only closing, since switching to it does nothing`() {
        assertEquals(listOf("popup.hint.close"), hints(open("alpha", isCurrent = true)).map { it.labelKey })
    }

    @Test
    fun `nothing selected shows no hints`() {
        assertEquals(emptyList<Hint>(), hints(null))
    }

    @Test
    fun `the close hint shows the key the popup closes with`() {
        val shortcut = PopupKeys(closeOnDelete = false).close
        val delete = PopupKeys(closeOnDelete = true).close

        assertEquals(closeShortcut(), hintsFor(recent("beta"), shortcut).last().stroke)
        assertEquals(deleteShortcut(), hintsFor(recent("beta"), delete).last().stroke, "the shortcut no longer closes once delete is chosen")
    }

    @Test
    fun `every hint has a label in the bundle list`() {
        val used = listOf(recent("b"), open("a"), open("c", isCurrent = true))
            .flatMap { hints(it) }
            .map { it.labelKey }
            .toSet()

        assertEquals(used, used.intersect(HINT_LABEL_KEYS.toSet()), "PluginRuntimeTest checks only the keys in HINT_LABEL_KEYS")
    }

    private fun hints(item: ProjectItem?): List<Hint> = hintsFor(item, closeShortcut())
}
