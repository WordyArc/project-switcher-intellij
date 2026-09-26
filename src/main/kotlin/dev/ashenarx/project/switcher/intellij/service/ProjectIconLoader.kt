package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.DeferredIcon
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@Service(Service.Level.APP)
class ProjectIconLoader {

    private val rendered = ConcurrentHashMap<RenderedIcon, BufferedImage>()

    private val warmedUp = AtomicBoolean()

    fun cachedIcons(keys: Collection<String>): Map<String, BufferedImage> {
        val size = iconSizePasses(JBUIScale.sysScale()).last()
        val dark = isDarkTheme()

        return keys.mapNotNull { key -> rendered[RenderedIcon(key, size, dark)]?.let { key to it } }.toMap()
    }

    suspend fun loadIcons(items: List<ProjectItem>, emit: suspend (String, BufferedImage) -> Unit) {
        val sources = iconSources(distinctIconItems(items))
        if (sources.isEmpty()) return

        val passes = iconSizePasses(JBUIScale.sysScale())
        val crisp = passes.last()
        val dark = isDarkTheme()
        val shown = sources.keys.filterTo(mutableSetOf()) { rendered.containsKey(RenderedIcon(it, crisp, dark)) }

        for (size in passes) {
            val isCrisp = size == crisp
            val wanted = if (isCrisp) sources else sources.filterKeys { it !in shown }
            if (wanted.isEmpty()) continue

            val probe = IconPassProbe(size, wanted.size)
            probe.pass {
                val icons = wanted.mapNotNull { (key, source) -> source(size)?.let { key to it } }.toMap()

                settle(icons, probe) { key, icon, resolved ->
                    if (!resolved && key in shown) return@settle

                    val image = icon.rasterize()
                    if (resolved && isCrisp) remember(RenderedIcon(key, size, dark), image)
                    shown += key
                    emit(key, image)
                }
            }
        }
    }

    suspend fun warmUp(): Boolean {
        if (!warmedUp.compareAndSet(false, true)) return false

        val elapsed = measureTime {
            val projects = ProjectCatalog.getInstance().collect(currentProject = null)
            loadIcons(projects.all) { _, _ -> }
        }
        thisLogger().debug { "Warmed the project list and icons in ${elapsed.inWholeMilliseconds} ms" }
        return true
    }

    // A DeferredIcon starts loading only when Swing paints it, which Compose rasterization never does.
    private suspend fun settle(
        icons: Map<String, Icon>,
        probe: IconPassProbe,
        onSettled: suspend (key: String, icon: Icon, resolved: Boolean) -> Unit,
    ) = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val pending = linkedMapOf<String, DeferredIcon>()

        for ((key, icon) in icons) {
            val deferred = icon as? DeferredIcon
            if (deferred == null || deferred.isDone) {
                probe.ready()
                guarded(key) { onSettled(key, icon, true) }
            } else {
                pending[key] = deferred
            }
        }

        if (pending.isNotEmpty() && !PowerSaveMode.isEnabled()) {
            val component = JPanel()
            pending.values.forEach { it.notifyPaint(component, 0, 0) }

            // TODO: Replace polling when DeferredIcon exposes a public completion signal.
            withTimeoutOrNull(ICON_TIMEOUT) {
                while (pending.isNotEmpty()) {
                    delay(ICON_POLL_INTERVAL)

                    for ((key, icon) in pending.filterValues { it.isDone }) {
                        pending.remove(key)
                        probe.settledAfterWaiting()
                        guarded(key) { onSettled(key, icon, true) }
                    }
                }
            }
        }

        for ((key, icon) in pending) {
            probe.neverSettled()
            guarded(key) { onSettled(key, icon, false) }
        }
    }

    private suspend fun guarded(key: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().debug("Cannot render project icon for $key", e)
        }
    }

    private fun remember(key: RenderedIcon, image: BufferedImage) {
        if (rendered.size >= MAX_RENDERED_ICONS) rendered.clear()
        rendered[key] = image
    }

    private fun iconSources(items: List<ProjectItem>): Map<String, (size: Int) -> Icon?> {
        val recentManager = service<RecentProjectsManager>() as? RecentProjectsManagerBase
        if (recentManager == null) {
            thisLogger().debug("RecentProjectsManager is not a RecentProjectsManagerBase — no project icons")
            return emptyMap()
        }

        return items.associate { item -> item.path to { size: Int -> projectIcon(recentManager, item.path, size) } }
    }

    private fun projectIcon(recentManager: RecentProjectsManagerBase, path: String, size: Int): Icon? =
        try {
            recentManager.getProjectIcon(path, true, size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().debug("Cannot load icon for $path", e)
            null
        }

    private fun isDarkTheme(): Boolean = !JBColor.isBright()

    private data class RenderedIcon(val key: String, val size: Int, val dark: Boolean)

    companion object {
        private const val ICON_SIZE = 20

        private const val MAX_RASTER_SCALE = 3

        private const val MAX_RENDERED_ICONS = 512

        private val ICON_TIMEOUT = 2.seconds

        private val ICON_POLL_INTERVAL = 16.milliseconds

        // ReopenProjectAction.projectIcon is stuck at 20 px, too few source pixels for HiDPI.
        internal fun iconSizePasses(scale: Float): List<Int> {
            val crisp = ICON_SIZE * ceil(scale).toInt().coerceIn(1, MAX_RASTER_SCALE)
            return if (crisp == ICON_SIZE) listOf(ICON_SIZE) else listOf(ICON_SIZE, crisp)
        }

        internal fun distinctIconItems(items: List<ProjectItem>): List<ProjectItem> =
            items.filter { it.path.isNotBlank() }.distinctBy { it.path }

        fun getInstance(): ProjectIconLoader = service()
    }
}
