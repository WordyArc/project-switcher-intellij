package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedSearchTest {

    private val query = TextFieldState()

    @Test
    fun `letters, digits and the usual path symbols are typed`() {
        "Ab1-_./".forEach { assertTrue(type(it), "'$it' should be typed") }

        assertEquals("Ab1-_./", query.text.toString())
    }

    @Test
    fun `a letter from another keyboard layout is typed as is`() {
        type('з')

        assertEquals("з", query.text.toString(), "layout correction happens in the matcher, not while typing")
    }

    @Test
    fun `characters outside the searchable set are left alone`() {
        assertFalse(type('='))
        assertFalse(type('\t'))

        assertEquals("", query.text.toString())
    }

    @Test
    fun `a space is typed only after something else`() {
        assertFalse(type(' ', Key.Spacebar), "a leading space would match everything and show nothing")

        type('a')
        assertTrue(type(' ', Key.Spacebar))
        assertEquals("a ", query.text.toString())
    }

    @Test
    fun `backspace erases the last character and alt-backspace the whole query`() {
        "abc".forEach { type(it) }

        assertTrue(query.typeSpeedSearchKey(event(Key.Backspace)))
        assertEquals("ab", query.text.toString())

        assertTrue(query.typeSpeedSearchKey(event(Key.Backspace, alt = true)))
        assertEquals("", query.text.toString())
    }

    @Test
    fun `backspace on an empty query is not handled`() {
        assertFalse(query.typeSpeedSearchKey(event(Key.Backspace)))
    }

    @Test
    fun `a shortcut chord is never typed`() {
        assertFalse(query.typeSpeedSearchKey(event(Key.A, 'a', ctrl = true), mac = false))
        assertFalse(query.typeSpeedSearchKey(event(Key.A, 'a', meta = true), mac = true))
        assertFalse(query.typeSpeedSearchKey(event(Key.A, 'a', alt = true), mac = false), "Alt is a shortcut modifier off macOS")

        assertEquals("", query.text.toString())
    }

    @Test
    fun `option types the character it produces on macOS`() {
        assertTrue(query.typeSpeedSearchKey(event(Key.O, 'ø', alt = true), mac = true))

        assertEquals("ø", query.text.toString())
    }

    private fun type(char: Char, key: Key = Key.A): Boolean = query.typeSpeedSearchKey(event(key, char), mac = false)

    private fun event(
        key: Key,
        char: Char? = null,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): KeyEvent = KeyEvent(
        key = key,
        type = KeyEventType.KeyDown,
        codePoint = char?.code ?: 0,
        isCtrlPressed = ctrl,
        isMetaPressed = meta,
        isAltPressed = alt,
    )
}
