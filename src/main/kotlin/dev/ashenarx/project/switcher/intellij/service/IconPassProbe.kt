package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.measureTime

internal class IconPassProbe(private val size: Int, private val icons: Int) {

    private val ready = AtomicInteger()
    private val waited = AtomicInteger()
    private val unsettled = AtomicInteger()

    suspend fun pass(block: suspend () -> Unit) {
        val elapsed = measureTime { block() }
        LOG.debug { report(elapsed) }
    }

    fun ready() {
        ready.incrementAndGet()
    }

    fun settledAfterWaiting() {
        waited.incrementAndGet()
    }

    fun neverSettled() {
        unsettled.incrementAndGet()
    }

    private fun report(elapsed: Duration): String = buildString {
        append("Icon pass ${size}px: $icons icons in ${elapsed.inWholeMilliseconds} ms")
        append(" (${ready.get()} ready at once, ${waited.get()} after waiting, ${unsettled.get()} never settled")
        if (PowerSaveMode.isEnabled()) append(", power save mode")
        append(")")
    }

    private companion object {
        val LOG = logger<ProjectIconLoader>()
    }
}
