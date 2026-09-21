package dev.ashenarx.project.switcher.intellij.service

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class ProjectIconLoaderTest {

    @Test
    fun `warm up does its work once per IDE run`() = timeoutRunBlocking {
        val loader = ProjectIconLoader.getInstance()

        assertTrue(loader.warmUp(), "the first call has to do the warming")
        assertFalse(loader.warmUp(), "a second call must not repeat the icon passes")
    }
}
