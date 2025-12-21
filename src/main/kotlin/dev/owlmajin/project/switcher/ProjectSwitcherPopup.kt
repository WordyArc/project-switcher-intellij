package dev.owlmajin.project.switcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.ui.component.Text
import java.awt.event.InputEvent

@Composable
fun ProjectSwitcherPopup(
    items: List<SwitcherItem>,
    currentProjectName: String?,
    onClose: () -> Unit,
    onSelectOpen: (Project) -> Unit,
    onSelectRecent: (ReopenProjectAction, modifiersEx: Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }

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
                    else -> false
                }
            }
    ) {
        Text("Switch Project")
        Spacer(Modifier.size(8.dp))

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
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

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOpen(item.project) }
                                .padding(vertical = 6.dp, horizontal = 6.dp)
                        ) {
                            Text(prefix + item.project.name)
                            item.path?.let { Text(it) }
                        }
                    }

                    is SwitcherItem.RecentProjectItem -> {
                        val isSelected = selectableIdx.getOrNull(selectedPos) == index
                        val prefix = if (isSelected) "› " else "  "

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRecent(item.action, 0) }
                                .padding(vertical = 6.dp, horizontal = 6.dp)
                        ) {
                            Text(prefix + item.name)
                            Text(item.subtitle ?: item.path)
                        }
                    }
                }
            }
        }
    }
}

private fun SwitcherItem.matches(q: String): Boolean = when (this) {
    is SwitcherItem.Header -> true
    is SwitcherItem.OpenProjectItem ->
        project.name.contains(q, ignoreCase = true) || (path?.contains(q, true) == true)

    is SwitcherItem.RecentProjectItem ->
        name.contains(q, ignoreCase = true) || path.contains(q, ignoreCase = true)
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
