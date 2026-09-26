package dev.ashenarx.project.switcher.intellij.model

import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.NameUtilCore

internal data class Match(val degree: Int, val ranges: List<IntRange>)

internal class ProjectMatcher(query: String) {

    private val delegate: MinusculeMatcher? =
        query.toPatternOrNull()?.let { NameUtil.buildMatcher(it).typoTolerant().build() }

    fun match(text: String): Match? {
        val matcher = delegate ?: return null
        val fragments = matcher.match(text)?.takeIf { it.isNotEmpty() } ?: return null

        return Match(
            degree = matcher.matchingDegree(text, false, fragments),
            ranges = fragments.map { it.startOffset until it.endOffset },
        )
    }
}

private fun String.toPatternOrNull(): String? {
    if (isBlank()) return null

    val pattern = NameUtilCore.nameToWordList(this).joinToString("*")
    return if (pattern.startsWith("*")) pattern else "*$pattern"
}
