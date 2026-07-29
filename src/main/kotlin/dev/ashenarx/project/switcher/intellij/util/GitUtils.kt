package dev.ashenarx.project.switcher.intellij.util

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap


object GitUtils {
    private const val GIT_DIR_NAME = ".git"
    private const val HEAD_FILE_NAME = "HEAD"
    private const val REF_PREFIX = "ref: refs/heads/"
    private const val SHORT_SHA_LENGTH = 7

    private const val NO_BRANCH_MARKER = "\u0000"

    private val branchCache = ConcurrentHashMap<String, String>()


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


    fun clearCache() {
        branchCache.clear()
    }


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
