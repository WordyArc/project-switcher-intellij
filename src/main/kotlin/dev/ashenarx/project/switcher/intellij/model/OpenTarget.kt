package dev.ashenarx.project.switcher.intellij.model

/** Which frame a recent project should be opened in. */
enum class OpenTarget {
    /** Leaves the frame to the "Open project in" setting, which may ask the user. */
    Ask,

    /** Replace the project in the current frame, without asking. */
    CurrentWindow,

    /** Always open a new frame. */
    NewWindow,
}
