package dev.ashenarx.project.switcher.intellij.model

import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.NameUtilCore
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher.MatchResult

/**
 * Bridges Jewel's speed search onto the platform's [MinusculeMatcher].
 *
 * Jewel's own `SpeedSearchMatcher.patternMatcher` is a Compose-side port of `MinusculeMatcherImpl`
 * and stops at camel humps. Delegating to the platform buys the wrapper layers that
 * `NameUtil.MatcherBuilder.build` adds for free: `FixingLayoutTypoTolerantMatcher` both retries the
 * query through the keyboard layout the user is actually typing in and forgives an adjacent-key
 * slip, and `PinyinMatcher` on top of it covers Chinese.
 */
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

    /**
     * The platform's relevance score for [text], or `null` when it does not match at all. Higher is
     * better; the scale is arbitrary and only comparable between calls on the same matcher.
     */
    fun degreeOrNull(text: String): Int? {
        val matcher = delegate ?: return null
        val fragments = matcher.match(text) ?: return null

        return matcher.matchingDegree(text, false, fragments)
    }
}

/**
 * The pattern shape `SpeedSearchComparator` feeds the platform: words joined by `*` so one query can
 * skip across humps, plus a leading `*` so it need not match from the start of the name.
 */
private fun String.toPatternOrNull(): String? {
    if (isBlank()) return null

    val pattern = NameUtilCore.nameToWordList(this).joinToString("*")
    return if (pattern.startsWith("*")) pattern else "*$pattern"
}
