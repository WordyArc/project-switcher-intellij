package dev.ashenarx.project.switcher.intellij.ui

import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal data class Hint(val stroke: KeyStroke, val labelKey: String)

internal fun hintsFor(item: ProjectItem?, close: KeyStroke): List<Hint> = when (item) {
    null -> emptyList()

    is ProjectItem.Open -> listOfNotNull(
        Hint(ENTER, "popup.hint.switch").takeUnless { item.isCurrent },
        Hint(close, "popup.hint.close"),
    )

    is ProjectItem.Recent -> listOf(
        Hint(ENTER, "popup.hint.open"),
        Hint(SHIFT_ENTER, "popup.hint.current.window"),
        Hint(CTRL_ENTER, "popup.hint.new.window"),
        Hint(close, "popup.hint.remove"),
    )
}

internal val HINT_LABEL_KEYS = listOf(
    "popup.hint.switch",
    "popup.hint.close",
    "popup.hint.open",
    "popup.hint.current.window",
    "popup.hint.new.window",
    "popup.hint.remove",
)

private val ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
private val SHIFT_ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
private val CTRL_ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK)
