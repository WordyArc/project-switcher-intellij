package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import java.awt.event.InputEvent
import javax.swing.JPanel
import java.awt.event.KeyEvent as AwtKeyEvent

internal class PopupScene(
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    content: @Composable () -> Unit,
) : AutoCloseable {

    private val scene = ImageComposeScene(width, height, Density(1f), Dispatchers.Unconfined) {
        SwingBridgeTheme(content)
    }

    private val source = JPanel()

    private var nanos = 0L

    fun frames(count: Int = 2) {
        repeat(count) {
            nanos += FRAME_NANOS
            scene.render(nanos)
        }
    }

    fun press(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): Boolean {
        val modifiers = modifiersOf(ctrl = ctrl, shift = shift, alt = alt, meta = meta)
        val handled = send(AwtKeyEvent.KEY_PRESSED, key.nativeKeyCode, AwtKeyEvent.CHAR_UNDEFINED, modifiers)
        send(AwtKeyEvent.KEY_RELEASED, key.nativeKeyCode, AwtKeyEvent.CHAR_UNDEFINED, modifiers)
        return handled
    }

    fun type(text: String) {
        for (char in text) {
            val keyCode = AwtKeyEvent.getExtendedKeyCodeForChar(char.code)
            send(AwtKeyEvent.KEY_PRESSED, keyCode, char, modifiers = 0)
            send(AwtKeyEvent.KEY_TYPED, AwtKeyEvent.VK_UNDEFINED, char, modifiers = 0)
            send(AwtKeyEvent.KEY_RELEASED, keyCode, char, modifiers = 0)
        }
    }

    fun click(text: String, ctrl: Boolean = false, shift: Boolean = false) {
        val center = checkNotNull(boundsOf(text)) { "nothing on screen shows \"$text\"" }.center
        val modifiers = PointerKeyboardModifiers(isCtrlPressed = ctrl, isShiftPressed = shift)

        scene.sendPointerEvent(PointerEventType.Move, center, keyboardModifiers = modifiers)
        scene.sendPointerEvent(
            PointerEventType.Press,
            center,
            buttons = PointerButtons(isPrimaryPressed = true),
            keyboardModifiers = modifiers,
            button = PointerButton.Primary,
        )
        scene.sendPointerEvent(
            PointerEventType.Release,
            center,
            buttons = PointerButtons(),
            keyboardModifiers = modifiers,
            button = PointerButton.Primary,
        )
    }

    fun boundsOf(text: String): Rect? =
        scene.semanticsOwners.firstNotNullOfOrNull { it.unmergedRootSemanticsNode.find(text) }?.boundsInRoot

    override fun close() {
        scene.close()
    }

    private fun send(id: Int, keyCode: Int, keyChar: Char, modifiers: Int): Boolean {
        val awt = AwtKeyEvent(source, id, System.currentTimeMillis(), modifiers, keyCode, keyChar)
        val type = when (id) {
            AwtKeyEvent.KEY_PRESSED -> KeyEventType.KeyDown
            AwtKeyEvent.KEY_RELEASED -> KeyEventType.KeyUp
            else -> KeyEventType.Unknown
        }

        return scene.sendKeyEvent(
            KeyEvent(
                key = Key(keyCode),
                type = type,
                codePoint = if (keyChar == AwtKeyEvent.CHAR_UNDEFINED) 0 else keyChar.code,
                isCtrlPressed = awt.isControlDown,
                isMetaPressed = awt.isMetaDown,
                isAltPressed = awt.isAltDown,
                isShiftPressed = awt.isShiftDown,
                nativeEvent = awt,
            ),
        )
    }

    private fun modifiersOf(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Int =
        (if (ctrl) InputEvent.CTRL_DOWN_MASK else 0) or
            (if (shift) InputEvent.SHIFT_DOWN_MASK else 0) or
            (if (alt) InputEvent.ALT_DOWN_MASK else 0) or
            (if (meta) InputEvent.META_DOWN_MASK else 0)

    private fun SemanticsNode.find(text: String): SemanticsNode? {
        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty()
        if (texts.any { it.text == text }) return this

        return children.firstNotNullOfOrNull { it.find(text) }
    }

    private companion object {
        const val DEFAULT_WIDTH = 420
        const val DEFAULT_HEIGHT = 420
        const val FRAME_NANOS = 16_000_000L
    }
}
