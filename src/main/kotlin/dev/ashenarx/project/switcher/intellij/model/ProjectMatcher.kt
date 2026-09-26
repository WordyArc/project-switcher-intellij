package dev.ashenarx.project.switcher.intellij.model

import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.NameUtilCore
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher.MatchResult

// The platform matcher, not Jewel's port: only it fixes the keyboard layout, tolerates typos and matches Pinyin.
internal class ProjectMatcher(query: String) : SpeedSearchMatcher {

    private val delegate: MinusculeMatcher? =
        query.toPatternOrNull()?.let { NameUtil.buildMatcher(it).typoTolerant().build() }

    override fun matches(text: String?): MatchResult {
        val matcher = delegate ?: return MatchResult.NoMatch
        val fragments = text?.let(matcher::match) ?: return MatchResult.NoMatch

        return if (fragments.isEmpty()) {
            MatchResult.NoMatch
        } else {
            MatchResult.Match(fragments.map { it.startOffset until it.endOffset })
        }
    }

    fun rangesOrNull(text: String): List<IntRange>? = (matches(text) as? MatchResult.Match)?.ranges

    fun degreeOrNull(text: String): Int? {
        val matcher = delegate ?: return null
        val fragments = matcher.match(text) ?: return null

        return matcher.matchingDegree(text, false, fragments)
    }
}

private fun String.toPatternOrNull(): String? {
    if (isBlank()) return null

    val pattern = NameUtilCore.nameToWordList(this).joinToString("*")
    return if (pattern.startsWith("*")) pattern else "*$pattern"
}
