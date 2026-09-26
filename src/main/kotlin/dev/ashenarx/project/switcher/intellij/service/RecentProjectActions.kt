package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun String.toProjectPath(): String = FileUtil.toSystemIndependentName(this)

internal fun presentableProjectPath(path: String): String =
    FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path), false)

internal fun String.isLocalProjectPath(): Boolean =
    try {
        Path.of(this).getEelDescriptor() == LocalEelDescriptor
    } catch (_: InvalidPathException) {
        false
    }

internal val ReopenProjectAction.normalizedPath: String
    get() = projectPath.toProjectPath()

internal fun recentProjectActions(): List<AnAction> =
    RecentProjectListActionProvider.getInstance().getActions()
