package dev.ashenarx.project.switcher.intellij.model

enum class OpenTarget {
    /** Defers to the IDE's "Open project in" setting. */
    Ask,

    CurrentWindow,

    NewWindow,
}
