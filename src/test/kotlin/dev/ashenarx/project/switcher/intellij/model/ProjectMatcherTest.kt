package dev.ashenarx.project.switcher.intellij.model

import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher.MatchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectMatcherTest {

    @Test
    fun `matches a name typed on the wrong keyboard layout`() {
        // "зкщоусе" is "project" typed with a Russian layout active.
        assertNotNull(ProjectMatcher("зкщоусе").degreeOrNull("project-switcher /home/me/project"))
    }

    @Test
    fun `leaves a half-switched query unmatched`() {
        // The platform's fixLayout only retries when every letter of the query is non-ASCII, so a
        // query half-typed in each layout stays a miss.
        assertNull(ProjectMatcher("pкщоусе").degreeOrNull("project"))
    }

    @Test
    fun `matches camel humps and reports the matched ranges`() {
        val result = ProjectMatcher("ps").matches("ProjectSwitcher")

        assertTrue(result is MatchResult.Match, "expected a match, got $result")
        assertEquals(listOf(0 until 1, 7 until 8), (result as MatchResult.Match).ranges)
    }

    @Test
    fun `ranges cover exactly the matched characters`() {
        val ranges = ProjectMatcher("min").rangesOrNull("Terminal")

        assertEquals("min", ranges?.joinToString("") { "Terminal".substring(it) })
    }

    @Test
    fun `ranks a word-start match above one buried mid-word`() {
        val matcher = ProjectMatcher("proj")

        val atStart = checkNotNull(matcher.degreeOrNull("project")) { "\"project\" should match" }
        val midWord = checkNotNull(matcher.degreeOrNull("approject")) { "\"approject\" should match" }

        assertTrue(atStart > midWord, "expected $atStart > $midWord")
    }

    @Test
    fun `survives a typo`() {
        assertNotNull(ProjectMatcher("porject").degreeOrNull("project"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertEquals(MatchResult.NoMatch, ProjectMatcher("   ").matches("project"))
        assertNull(ProjectMatcher("").degreeOrNull("project"))
    }

    @Test
    fun `a null text matches nothing`() {
        assertEquals(MatchResult.NoMatch, ProjectMatcher("proj").matches(null))
    }
}
