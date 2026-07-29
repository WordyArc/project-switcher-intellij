package dev.ashenarx.project.switcher.intellij.util

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Utilities for working with Git repositories.
 *
 * Provides cached access to Git branch information to avoid
 * repeated file system reads during UI rendering.
 */
object GitUtils {
    private const val GIT_DIR_NAME = ".git"
    private const val HEAD_FILE_NAME = "HEAD"
    private const val REF_PREFIX = "ref: refs/heads/"
    private const val SHORT_SHA_LENGTH = 7

    // Marker for "no branch found" to distinguish from "not yet cached"
    private const val NO_BRANCH_MARKER = "\u0000"

    private val branchCache = ConcurrentHashMap<String, String>()

    /**
     * Retrieves the current Git branch name for a project by its path.
     * Results are cached to avoid repeated file system access.
     *
     * @param projectPath The file system path to the project.
     * @return The branch name, short SHA if detached HEAD, or null if not a Git repo.
     */
    fun getCurrentBranch(projectPath: String?): String? {
        if (projectPath.isNullOrBlank()) return null

        val normalizedPath = FileUtil.toSystemIndependentName(projectPath)

        val cached = branchCache[normalizedPath]
        if (cached != null) {
            return if (cached == NO_BRANCH_MARKER) null else cached
        }

        val branch = readBranchFromGit(normalizedPath)
        branchCache[normalizedPath] = branch ?: NO_BRANCH_MARKER
        return branch
    }

    /**
     * Clears the branch cache. Useful when projects may have switched branches.
     */
    fun clearCache() {
        branchCache.clear()
    }

    /**
     * Invalidates cache for a specific project path.
     */
    fun invalidate(projectPath: String) {
        branchCache.remove(FileUtil.toSystemIndependentName(projectPath))
    }

    private fun readBranchFromGit(projectPath: String): String? {
        return try {
            val gitDir = File(projectPath, GIT_DIR_NAME)
            if (!gitDir.exists()) return null

            val headFile = File(gitDir, HEAD_FILE_NAME)
            if (!headFile.exists()) return null

            val headContent = headFile.readText().trim()
            parseBranchFromHead(headContent)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseBranchFromHead(headContent: String): String? {
        return when {
            headContent.startsWith(REF_PREFIX) ->
                headContent.substring(REF_PREFIX.length)
            headContent.length >= SHORT_SHA_LENGTH ->
                headContent.substring(0, SHORT_SHA_LENGTH)
            else -> null
        }
    }
}
