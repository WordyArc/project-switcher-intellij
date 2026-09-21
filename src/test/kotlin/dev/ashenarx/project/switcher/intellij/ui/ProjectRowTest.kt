package dev.ashenarx.project.switcher.intellij.ui

import com.intellij.util.SystemProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ProjectRowTest {

    @Test
    fun `project path is relative to user home`() {
        val path = File(SystemProperties.getUserHome(), "projects/example").path

        assertEquals("~${File.separator}projects${File.separator}example", presentableProjectPath(path))
    }

    @Test
    fun `highlights keep their place when the path is shown as is`() {
        val ranges = listOf(1..4, 6..6)

        assertEquals(ranges, presentablePathHighlights("/opt/work", "/opt/work", ranges))
    }

    @Test
    fun `highlights shift left when the home prefix collapses to a tilde`() {
        val path = "/Users/me/projects/example"
        val presentable = "~/projects/example"

        assertEquals(listOf(2..9), presentablePathHighlights(path, presentable, listOf(10..17)))
        assertEquals("projects", presentable.substring(2..9))
    }

    @Test
    fun `a highlight inside the collapsed prefix is dropped or trimmed to what remains`() {
        val path = "/Users/me/projects/example"
        val presentable = "~/projects/example"

        assertEquals(emptyList<IntRange>(), presentablePathHighlights(path, presentable, listOf(1..5)))
        assertEquals(listOf(1..1), presentablePathHighlights(path, presentable, listOf(5..9)))
    }
}
