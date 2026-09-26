package dev.ashenarx.project.switcher.intellij

import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class ArchitectureTest {

    private val classes: Map<String, Set<String>> by lazy { pluginClasses() }

    @Test
    fun `the plugin classes are found at all`() {
        assertTrue(classes.keys.any { it.startsWith(MODEL) }, "no model classes found, the rules below would pass vacuously")
        assertTrue(classes.keys.any { it.startsWith(SERVICE) }, "no service classes found")
        assertTrue(classes.keys.any { it.startsWith(UI) }, "no ui classes found")
    }

    @Test
    fun `the model depends on neither the UI toolkit nor the services`() {
        assertEquals(
            emptyMap<String, List<String>>(),
            violations(MODEL, forbidden = listOf(SERVICE, UI, SETTINGS, "androidx/compose/", "org/jetbrains/jewel/", "javax/swing/", "java/awt/")),
            "model/ holds plain types; UI and platform services depend on it, not the other way round",
        )
    }

    @Test
    fun `services do not reach into the UI`() {
        assertEquals(
            emptyMap<String, List<String>>(),
            violations(SERVICE, forbidden = listOf(UI, SETTINGS, "androidx/compose/", "org/jetbrains/jewel/")),
            "service/ talks to the platform and returns model types and images, never Compose state",
        )
    }

    @Test
    fun `settings are read by the root package only`() {
        assertEquals(
            emptyMap<String, List<String>>(),
            violations(UI, forbidden = listOf(SETTINGS)) + violations(SETTINGS, forbidden = listOf(MODEL, SERVICE, UI)),
            "the popup gets a PopupAppearance from the root, so it can be shown in any look without the platform",
        )
    }

    @Test
    fun `the UI reaches services only through PlatformProjectActions`() {
        val bridge = "${UI}PlatformProjectActions"

        assertEquals(
            emptyMap<String, List<String>>(),
            violations(UI, forbidden = listOf(SERVICE)).filterKeys { !it.startsWith(bridge) },
            "everything else in ui/ works against the ProjectActions port so that it can be tested without the platform",
        )
    }

    private fun violations(packagePrefix: String, forbidden: List<String>): Map<String, List<String>> =
        classes
            .filterKeys { it.startsWith(packagePrefix) }
            .mapValues { (_, references) ->
                references
                    .filter { reference -> reference !in COMPILER_GENERATED && forbidden.any(reference::startsWith) }
                    .sorted()
            }
            .filterValues { it.isNotEmpty() }

    private fun pluginClasses(): Map<String, Set<String>> {
        val roots = listOf(ProjectItem::class.java, Class.forName("$ROOT_PACKAGE.service.OpenProjectTaskFactory"))
            .map(::rootOf)
            .distinct()

        return roots
            .flatMap(::classFilesUnder)
            .filter { (name, _) -> name.startsWith(ROOT) }
            .associate { (name, bytes) -> name to referencesOf(bytes) }
    }

    private fun rootOf(type: Class<*>): Path {
        val resource = type.name.replace('.', '/') + ".class"
        val url = checkNotNull(type.classLoader.getResource(resource)) { "cannot locate $resource" }

        if (url.protocol == "jar") return Path.of(URI(url.path.substringBefore("!/")))

        return generateSequence(Path.of(url.toURI())) { it.parent }.elementAt(resource.count { it == '/' } + 1)
    }

    private fun classFilesUnder(root: Path): List<Pair<String, ByteArray>> {
        if (root.isDirectory()) {
            return Files.walk(root).use { paths ->
                paths.asSequence()
                    .filter { it.extension == "class" }
                    .map { it.relativeTo(root).invariantSeparatorsPathString.removeSuffix(".class") to it.readBytes() }
                    .toList()
            }
        }

        return FileSystems.newFileSystem(root).use { jar ->
            classFilesUnder(jar.getPath("/")).map { (name, bytes) -> name.removePrefix("/") to bytes }
        }
    }

    private fun referencesOf(bytes: ByteArray): Set<String> {
        val references = mutableSetOf<String>()
        val recorder = object : Remapper() {
            override fun map(internalName: String): String {
                references += internalName
                return internalName
            }
        }

        ClassReader(bytes).accept(ClassRemapper(object : ClassVisitor(Opcodes.ASM9) {}, recorder), 0)
        return references
    }

    private companion object {
        const val ROOT_PACKAGE = "dev.ashenarx.project.switcher.intellij"
        const val ROOT = "dev/ashenarx/project/switcher/intellij/"
        const val MODEL = "${ROOT}model/"
        const val SERVICE = "${ROOT}service/"
        const val UI = "${ROOT}ui/"
        const val SETTINGS = "${ROOT}settings/"

        val COMPILER_GENERATED = setOf("androidx/compose/runtime/internal/StabilityInferred")
    }
}
