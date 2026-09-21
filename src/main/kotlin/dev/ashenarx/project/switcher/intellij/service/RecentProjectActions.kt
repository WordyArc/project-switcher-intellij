package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.util.io.FileUtil

internal fun String.toProjectPath(): String = FileUtil.toSystemIndependentName(this)

internal val ReopenProjectAction.normalizedPath: String
    get() = projectPath.toProjectPath()

internal fun recentProjectActions(): Sequence<ReopenProjectAction> =
    RecentProjectListActionProvider.getInstance()
        .getActions()
        .asSequence()
        .filterIsInstance<ReopenProjectAction>()
