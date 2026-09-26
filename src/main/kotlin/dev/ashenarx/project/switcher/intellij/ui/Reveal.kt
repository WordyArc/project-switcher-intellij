package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState

internal data class VisibleRow(val index: Int, val offset: Int, val size: Int)

internal sealed interface Reveal {
    data object None : Reveal

    data class By(val pixels: Int) : Reveal

    data class To(val index: Int, val offset: Int) : Reveal
}

internal fun revealFor(first: Int, last: Int, visible: List<VisibleRow>, viewportStart: Int, viewportEnd: Int): Reveal {
    val top = visible.firstOrNull() ?: return Reveal.To(first, 0)
    val bottom = visible.last()
    val firstRow = visible.firstOrNull { it.index == first }
    val lastRow = visible.firstOrNull { it.index == last }

    return when {
        first < top.index -> Reveal.To(first, 0)
        firstRow != null && firstRow.offset < viewportStart -> Reveal.By(firstRow.offset - viewportStart)
        last > bottom.index -> Reveal.To(last, -(viewportEnd - viewportStart - bottom.size))
        lastRow != null && lastRow.offset + lastRow.size > viewportEnd -> Reveal.By(lastRow.offset + lastRow.size - viewportEnd)
        else -> Reveal.None
    }
}

internal fun pageSizeFor(visible: List<VisibleRow>, viewportStart: Int, viewportEnd: Int): Int =
    (visible.count { it.offset >= viewportStart && it.offset + it.size <= viewportEnd } - 1).coerceAtLeast(1)

internal suspend fun LazyListState.reveal(first: Int, last: Int) {
    val info = layoutInfo
    when (val reveal = revealFor(first, last, info.visibleRows(), info.viewportStartOffset, info.viewportEndOffset)) {
        Reveal.None -> Unit
        is Reveal.By -> scrollBy(reveal.pixels.toFloat())
        is Reveal.To -> scrollToItem(reveal.index, reveal.offset)
    }
}

internal fun LazyListState.pageSize(): Int {
    val info = layoutInfo
    return pageSizeFor(info.visibleRows(), info.viewportStartOffset, info.viewportEndOffset)
}

private fun LazyListLayoutInfo.visibleRows(): List<VisibleRow> =
    visibleItemsInfo.map { VisibleRow(it.index, it.offset, it.size) }
