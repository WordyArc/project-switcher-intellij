package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Immutable

@Immutable
internal data class PopupAppearance(
    val searchField: Boolean = false,
    val showLocation: Boolean = false,
    val showShortcuts: Boolean = false,
) {
    val hasFooter: Boolean get() = showLocation || showShortcuts
}
