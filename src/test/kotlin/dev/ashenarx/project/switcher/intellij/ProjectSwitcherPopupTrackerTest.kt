package dev.ashenarx.project.switcher.intellij

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class ProjectSwitcherPopupTrackerTest {

    private val tracker = ProjectSwitcherPopupTracker()

    @Test
    fun `with nothing open the shortcut falls through to opening a popup`() {
        assertFalse(tracker.closeIfOpen())
    }

    @Test
    fun `the shortcut closes the popup it opened`() {
        val popup = FakePopup()
        tracker.register(popup.popup)

        assertTrue(tracker.closeIfOpen())
        assertEquals(1, popup.cancelCount)
    }

    @Test
    fun `pressing the shortcut again after closing opens instead of closing`() {
        val popup = FakePopup()
        tracker.register(popup.popup)

        assertTrue(tracker.closeIfOpen())
        assertFalse(tracker.closeIfOpen())
        assertEquals(1, popup.cancelCount, "a closed popup must not be cancelled twice")
    }

    @Test
    fun `a popup that closed on its own leaves nothing to cancel`() {
        val popup = FakePopup()
        tracker.register(popup.popup)

        popup.notifyClosed()

        assertFalse(tracker.closeIfOpen())
        assertEquals(0, popup.cancelCount)
    }

    @Test
    fun `a popup disposed behind the tracker's back is not cancelled`() {
        val popup = FakePopup()
        tracker.register(popup.popup)
        popup.disposed = true

        assertFalse(tracker.closeIfOpen())
        assertEquals(0, popup.cancelCount)
    }

    @Test
    fun `an older popup closing does not unregister the newer one`() {
        val older = FakePopup()
        val newer = FakePopup()

        tracker.register(older.popup)
        tracker.register(newer.popup)

        older.notifyClosed()

        assertTrue(tracker.closeIfOpen(), "the newer popup must still be tracked")
        assertEquals(1, newer.cancelCount)
        assertEquals(0, older.cancelCount)
    }

    private class FakePopup {
        var disposed = false
        var cancelCount = 0

        private val listeners = mutableListOf<JBPopupListener>()

        val popup: JBPopup = Proxy.newProxyInstance(
            JBPopup::class.java.classLoader,
            arrayOf(JBPopup::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "cancel" -> {
                    cancelCount++
                    disposed = true
                    notifyClosed()
                    null
                }

                "isDisposed" -> disposed
                "addListener" -> listeners.add(args[0] as JBPopupListener).let { null }
                "removeListener" -> listeners.remove(args[0] as JBPopupListener).let { null }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakePopup"
                else -> defaultValueFor(method.returnType)
            }
        } as JBPopup

        fun notifyClosed() {
            listeners.toList().forEach { it.onClosed(LightweightWindowEvent(popup)) }
        }

        private fun defaultValueFor(type: Class<*>): Any? = when (type) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }
}
