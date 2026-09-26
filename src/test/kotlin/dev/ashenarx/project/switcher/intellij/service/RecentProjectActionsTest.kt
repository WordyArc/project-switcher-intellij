package dev.ashenarx.project.switcher.intellij.service

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.SystemProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
class RecentProjectActionsTest {

    @Test
    fun `project path is relative to user home`() {
        val path = File(SystemProperties.getUserHome(), "projects/example").path.toProjectPath()

        assertEquals("~${File.separator}projects${File.separator}example", presentableProjectPath(path))
    }

    @Test
    fun `the user home never reaches the searchable location`() {
        val home = SystemProperties.getUserHome().toProjectPath()

        assertFalse(
            presentableProjectPath("$home/work/app").contains(File(home).name),
            "typing the user name would otherwise match every project in the home directory",
        )
    }

    @Test
    fun `a path outside the home directory is shown as is`() {
        assertEquals(File("/opt/work").path, presentableProjectPath("/opt/work"))
    }

    @Test
    fun `a directory on this machine is local`() {
        val directory = createTempDirectory("local-project")

        assertTrue(directory.invariantSeparatorsPathString.isLocalProjectPath())
    }
}
