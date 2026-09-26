package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectMatcherTest {

    @Test
    fun `matches a name typed on the wrong keyboard layout`() {
        // "зкщоусе" is "project" typed with a Russian layout active.
        assertNotNull(ProjectMatcher("зкщоусе").match("project-switcher ~/project"))
    }

    @Test
    fun `leaves a half-switched query unmatched`() {
        // The platform's fixLayout retries only when every letter of the query is non-ASCII.
        assertNull(ProjectMatcher("pкщоусе").match("project"))
    }

    @Test
    fun `matches camel humps and reports the matched ranges`() {
        val match = checkNotNull(ProjectMatcher("ps").match("ProjectSwitcher")) { "camel humps should match" }

        assertEquals(listOf(0 until 1, 7 until 8), match.ranges)
    }

    @Test
    fun `ranges cover exactly the matched characters`() {
        val ranges = ProjectMatcher("min").match("Terminal")?.ranges

        assertEquals("min", ranges?.joinToString("") { "Terminal".substring(it) })
    }

    @Test
    fun `ranks a word-start match above one buried mid-word`() {
        val matcher = ProjectMatcher("proj")

        val atStart = checkNotNull(matcher.match("project")) { "\"project\" should match" }.degree
        val midWord = checkNotNull(matcher.match("approject")) { "\"approject\" should match" }.degree

        assertTrue(atStart > midWord, "expected $atStart > $midWord")
    }

    @Test
    fun `survives a typo`() {
        assertNotNull(ProjectMatcher("porject").match("project"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertNull(ProjectMatcher("   ").match("project"))
        assertNull(ProjectMatcher("").match("project"))
    }
}
