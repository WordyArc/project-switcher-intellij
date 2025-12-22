package dev.owlmajin.project.switcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import javax.swing.Icon

@Composable
fun ProjectSwitcherPopup(
    items: List<SwitcherItem>,
    currentProjectName: String?,
    onClose: () -> Unit,
    onSelectOpen: (Project) -> Unit,
    onSelectRecent: (ReopenProjectAction, modifiersEx: Int) -> Unit,
) {
    val queryState = remember { TextFieldState("") }
    val query by remember { derivedStateOf { queryState.text.toString() } }

    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items
        else items.filter { it.matches(q) }.pruneLonelyHeaders()
    }

    val selectableIdx = remember(filtered) {
        filtered.mapIndexedNotNull { i, it ->
            when (it) {
                is SwitcherItem.OpenProjectItem,
                is SwitcherItem.RecentProjectItem -> i
                else -> null
            }
        }
    }

    var selectedPos by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectableIdx.size) {
        selectedPos = selectedPos.coerceIn(0, (selectableIdx.lastIndex).coerceAtLeast(0))
    }

    fun selectedItem(): SwitcherItem? =
        selectableIdx.getOrNull(selectedPos)?.let { filtered.getOrNull(it) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Обработка Alt+F2 для toggle (закрытие popup)
                if (ev.key == Key.F2 && ev.isAltPressed) {
                    onClose()
                    return@onPreviewKeyEvent true
                }

                when (ev.key) {
                    Key.Escape -> { onClose(); true }
                    Key.DirectionDown -> {
                        if (selectableIdx.isNotEmpty()) selectedPos = (selectedPos + 1).coerceAtMost(selectableIdx.lastIndex)
                        true
                    }
                    Key.DirectionUp -> {
                        if (selectableIdx.isNotEmpty()) selectedPos = (selectedPos - 1).coerceAtLeast(0)
                        true
                    }
                    Key.Enter -> {
                        val item = selectedItem()
                        val modifiersEx =
                            (if (ev.isShiftPressed) InputEvent.SHIFT_DOWN_MASK else 0) or
                                    (if (ev.isCtrlPressed) InputEvent.CTRL_DOWN_MASK else 0) or
                                    (if (ev.isMetaPressed) InputEvent.META_DOWN_MASK else 0) or
                                    (if (ev.isAltPressed) InputEvent.ALT_DOWN_MASK else 0)

                        when (item) {
                            is SwitcherItem.OpenProjectItem -> onSelectOpen(item.project)
                            is SwitcherItem.RecentProjectItem -> onSelectRecent(item.action, modifiersEx)
                            else -> {}
                        }
                        true
                    }
                    Key.Backspace -> {
                        if (query.isNotEmpty()) {
                            queryState.edit {
                                replace(length - 1, length, "")
                            }
                        }
                        true
                    }
                    else -> {
                        // Автоматически добавляем печатные символы в строку поиска
                        val char = ev.utf16CodePoint.toChar()
                        if (char.isDefined() && !char.isISOControl()) {
                            queryState.edit {
                                replace(length, length, char.toString())
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
            }
    ) {
        Text("Switch Project")
        Spacer(Modifier.size(8.dp))

        TextField(
            state = queryState,
            placeholder = { Text("Search projects...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.size(10.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(filtered, key = { _, it -> it.key }) { index, item ->
                when (item) {
                    is SwitcherItem.Header -> {
                        Spacer(Modifier.height(8.dp))
                        Text(item.text)
                        Spacer(Modifier.height(4.dp))
                    }

                    is SwitcherItem.OpenProjectItem -> {
                        val isSelected = selectableIdx.getOrNull(selectedPos) == index
                        val prefix =
                            when {
                                isSelected -> "› "
                                item.project.name == currentProjectName -> "• "
                                else -> "  "
                            }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOpen(item.project) }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prefix)
                            item.icon?.let { icon ->
                                val bitmap = remember(icon) { icon.toImageBitmap() }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(item.project.name)
                            item.branch?.let {
                                Spacer(Modifier.width(8.dp))
                                Text("[$it]")
                            }
                        }
                    }

                    is SwitcherItem.RecentProjectItem -> {
                        val isSelected = selectableIdx.getOrNull(selectedPos) == index
                        val prefix = if (isSelected) "› " else "  "
                        val name = item.action.projectNameToDisplay

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRecent(item.action, 0) }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prefix)
                            item.action.projectIcon?.let { icon ->
                                val bitmap = remember(icon) { icon.toImageBitmap() }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(name)
                            item.branch?.let {
                                Spacer(Modifier.width(8.dp))
                                Text("[$it]")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun SwitcherItem.matches(q: String): Boolean = when (this) {
    is SwitcherItem.Header -> true
    is SwitcherItem.OpenProjectItem -> {
        val pPath = project.basePath ?: project.projectFilePath
        project.name.contains(q, ignoreCase = true) || (pPath?.contains(q, true) == true)
    }

    is SwitcherItem.RecentProjectItem -> {
        val name = action.projectNameToDisplay ?: ""
        val path = action.projectPath
        name.contains(q, ignoreCase = true) || path.contains(q, ignoreCase = true)
    }
}

private fun List<SwitcherItem>.pruneLonelyHeaders(): List<SwitcherItem> {
    val out = ArrayList<SwitcherItem>(size)
    for (i in indices) {
        val it = this[i]
        if (it !is SwitcherItem.Header) {
            out += it
            continue
        }
        // header оставляем, только если после него есть хотя бы один entry до следующего header/конца
        val hasEntryAfter = (i + 1 until size).any { j ->
            val x = this[j]
            when (x) {
                is SwitcherItem.Header -> false
                is SwitcherItem.OpenProjectItem, is SwitcherItem.RecentProjectItem -> true
            }
        }
        if (hasEntryAfter) out += it
    }
    return out
}

private fun Icon.toImageBitmap(): ImageBitmap {
    val bufferedImage = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = bufferedImage.createGraphics()
    paintIcon(null, graphics, 0, 0)
    graphics.dispose()
    return bufferedImage.toComposeImageBitmap()
}
