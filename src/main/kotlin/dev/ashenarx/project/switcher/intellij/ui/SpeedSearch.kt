package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberComponentRectPositionProvider
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.speedSearchStyle

private const val SEARCHABLE_SYMBOLS = "*_-+\"'/.#$>: ,;?!@%^&"

private val ICON_GAP = 4.dp

internal fun TextFieldState.typeSpeedSearchKey(event: KeyEvent, mac: Boolean = SystemInfoRt.isMac): Boolean {
    if (event.key == Key.Backspace) return erase(wholeQuery = event.isAltPressed)

    val char = event.searchableChar(mac) ?: return false
    if (char == ' ' && text.isBlank()) return false

    edit { append(char) }
    return true
}

private fun TextFieldState.erase(wholeQuery: Boolean): Boolean {
    if (text.isEmpty()) return false

    edit { delete(if (wholeQuery) 0 else length - 1, length) }
    return true
}

private fun KeyEvent.searchableChar(mac: Boolean): Char? {
    val shortcutModifier = if (mac) isMetaPressed || isCtrlPressed else isCtrlPressed || isAltPressed
    if (shortcutModifier) return null

    val char = if (key == Key.Spacebar) ' ' else utf16CodePoint.toChar()
    return char.takeIf { it.isLetterOrDigit() || it in SEARCHABLE_SYMBOLS }
}

@Composable
internal fun SpeedSearchPopup(query: String, hasMatches: Boolean) {
    val style = JewelTheme.speedSearchStyle

    Popup(
        popupPositionProvider = rememberComponentRectPositionProvider(Alignment.TopStart, Alignment.TopEnd),
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
    ) {
        Row(
            modifier = Modifier
                .background(style.colors.background)
                .border(1.dp, style.colors.border)
                .padding(style.metrics.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(key = style.icons.magnifyingGlass, contentDescription = null)
            Spacer(Modifier.width(ICON_GAP))
            Text(query, color = if (hasMatches) style.colors.foreground else style.colors.error, maxLines = 1)
        }
    }
}
