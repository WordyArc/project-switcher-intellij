package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.DeferredIcon
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@Service(Service.Level.APP)
class ProjectIconLoader {

    private val permits = Semaphore(MAX_CONCURRENT_LOADS)

    private val warmedUp = AtomicBoolean()

    /** Streams cached-size icons first, then replaces them with HiDPI versions as they load. */
    suspend fun loadIcons(paths: List<String>, emit: suspend (String, Icon) -> Unit) {
        // getProjectIcon is only on the base class, but an IDE may replace the open service with an
        // unrelated implementation. Keep the cast safe instead of using getInstanceEx().
        val recentManager = service<RecentProjectsManager>() as? RecentProjectsManagerBase
        if (recentManager == null) {
            thisLogger().debug("RecentProjectsManager is not a RecentProjectsManagerBase — no project icons")
            return
        }

        val uniquePaths = distinctIconPaths(paths)

        for (size in iconSizePasses(JBUIScale.sysScale())) {
            val probe = IconPassProbe(size, uniquePaths.size, MAX_CONCURRENT_LOADS)

            // Finish the coarse pass before a later, crisp icon can be emitted.
            probe.pass {
                coroutineScope {
                    for (path in uniquePaths) {
                        launch {
                            permits.withPermit {
                                val icon = probe.fetch { projectIcon(recentManager, path, size) }
                                    ?: return@withPermit

                                emit(path, probe.settle { icon.resolved() })
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun warmUp(): Boolean {
        if (!warmedUp.compareAndSet(false, true)) return false

        val elapsed = measureTime {
            val projects = ProjectCatalog.getInstance().collect(currentProject = null)
            loadIcons(projects.all.map { it.path }) { _, _ -> }
        }
        thisLogger().debug { "Warmed the project list and icons in ${elapsed.inWholeMilliseconds} ms" }
        return true
    }

    /**
     * Deferred project icons normally start loading when Swing paints them. Compose rasterization
     * bypasses that trigger, so start the normal Swing lifecycle and wait until the icon settles.
     * The timeout prevents one icon from blocking the next size pass.
     */
    private suspend fun Icon.resolved(): Icon {
        val deferred = this as? DeferredIcon ?: return this

        try {
            // TODO: Replace polling when DeferredIcon exposes a public completion signal.
            withTimeoutOrNull(ICON_TIMEOUT) {
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    deferred.notifyPaint(JPanel(), 0, 0)
                    while (!deferred.isDone) delay(ICON_POLL_INTERVAL)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().debug("Cannot evaluate deferred project icon", e)
        }
        return this
    }

    private fun projectIcon(recentManager: RecentProjectsManagerBase, path: String, size: Int): Icon? {
        if (path.isBlank()) return null
        return runCatching { recentManager.getProjectIcon(path, true, size) }
            .onFailure { thisLogger().debug("Cannot load icon for $path", it) }
            .getOrNull()
    }

    companion object {
        private const val ICON_SIZE = 20

        private const val MAX_RASTER_SCALE = 3

        private const val MAX_CONCURRENT_LOADS = 4

        private val ICON_TIMEOUT = 2.seconds

        private val ICON_POLL_INTERVAL = 10.milliseconds

        /**
         * Starts with the platform's cacheable 20 px size, then requests enough source pixels for
         * HiDPI rasterization. [ReopenProjectAction.projectIcon] cannot provide the larger size.
         */
        internal fun iconSizePasses(scale: Float): List<Int> {
            val crisp = ICON_SIZE * ceil(scale).toInt().coerceIn(1, MAX_RASTER_SCALE)
            return if (crisp == ICON_SIZE) listOf(ICON_SIZE) else listOf(ICON_SIZE, crisp)
        }

        internal fun distinctIconPaths(paths: List<String>): List<String> =
            paths.filter { it.isNotBlank() }.distinct()

        fun getInstance(): ProjectIconLoader = service()
    }
}
