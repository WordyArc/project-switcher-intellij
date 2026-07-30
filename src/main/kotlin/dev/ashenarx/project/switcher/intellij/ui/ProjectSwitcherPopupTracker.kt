package dev.ashenarx.project.switcher.intellij.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import java.util.concurrent.atomic.AtomicReference

/**
 * An application service rather than a `companion object` field: the state is still global, but the
 * platform owns and disposes it. The compare-and-set on close means a popup that was already
 * superseded cannot clear a newer one's registration.
 */
@Service(Service.Level.APP)
internal class ProjectSwitcherPopupTracker {

    private val current = AtomicReference<JBPopup?>(null)

    fun closeIfOpen(): Boolean {
        val popup = current.getAndSet(null) ?: return false
        if (popup.isDisposed) return false

        popup.cancel()
        return true
    }

    fun register(popup: JBPopup) {
        current.set(popup)

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                current.compareAndSet(popup, null)
            }
        })
    }
}
