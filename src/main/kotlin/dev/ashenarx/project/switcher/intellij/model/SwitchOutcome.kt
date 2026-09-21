package dev.ashenarx.project.switcher.intellij.model

sealed interface SwitchOutcome {

    data class Focus(val project: ProjectItem.Open) : SwitchOutcome

    data class Reopen(val project: ProjectItem.Recent, val target: OpenTarget) : SwitchOutcome

    data class CloseCurrent(val project: ProjectItem.Open) : SwitchOutcome

    companion object {
        fun of(item: ProjectItem, target: OpenTarget): SwitchOutcome = when (item) {
            is ProjectItem.Open -> Focus(item)
            is ProjectItem.Recent -> Reopen(item, target)
        }
    }
}
