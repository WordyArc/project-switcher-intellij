package dev.ashenarx.project.switcher.intellij.service

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.DeferredIcon
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTimedValue


internal class IconPassProbe(private val size: Int, private val icons: Int, private val loaders: Int) {

    private val fetchNanos = AtomicLong()
    private val settleNanos = AtomicLong()
    private val deferred = AtomicInteger()
    private val unsettled = AtomicInteger()

    suspend fun pass(block: suspend () -> Unit) {
        val (_, elapsed) = measureTimedValue { block() }
        LOG.debug { report(elapsed) }
    }

    suspend fun <T> fetch(block: suspend () -> T): T = record(fetchNanos, block)

    suspend fun settle(block: suspend () -> Icon): Icon {
        val icon = record(settleNanos, block)

        if (icon is DeferredIcon) {
            deferred.incrementAndGet()
            if (!icon.isDone) unsettled.incrementAndGet()
        }
        return icon
    }

    private suspend fun <T> record(total: AtomicLong, block: suspend () -> T): T {
        val (result, elapsed) = measureTimedValue { block() }
        total.addAndGet(elapsed.inWholeNanoseconds)
        return result
    }

    private fun report(elapsed: Duration): String = buildString {
        append("Icon pass ${size}px: $icons icons in ${elapsed.inWholeMilliseconds} ms")
        append(" (fetch ${fetchNanos.get().nanoseconds.inWholeMilliseconds} ms")
        append(" + settle ${settleNanos.get().nanoseconds.inWholeMilliseconds} ms")
        append(" summed over $loaders loaders")
        append(", ${deferred.get()} deferred, ${unsettled.get()} never settled)")
    }

    private companion object {
        val LOG = logger<RecentProjectsService>()
    }
}
