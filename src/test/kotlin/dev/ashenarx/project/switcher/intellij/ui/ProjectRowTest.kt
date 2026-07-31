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
}
