package dev.ashenarx.project.switcher.intellij.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle

internal class ProjectSwitcherConfigurable : BoundConfigurable(ProjectSwitcherBundle.message("settings.display.name")) {

    override fun createPanel(): DialogPanel {
        val options = ProjectSwitcherSettings.getInstance().state

        return panel {
            group(ProjectSwitcherBundle.message("settings.group.popup")) {
                row {
                    checkBox(ProjectSwitcherBundle.message("settings.search.field"))
                        .bindSelected(options::searchField)
                        .comment(ProjectSwitcherBundle.message("settings.search.field.comment"))
                }
                row {
                    checkBox(ProjectSwitcherBundle.message("settings.show.location"))
                        .bindSelected(options::showLocation)
                }
                row {
                    checkBox(ProjectSwitcherBundle.message("settings.show.shortcuts"))
                        .bindSelected(options::showShortcuts)
                }
            }
        }
    }

    companion object {
        const val ID = "dev.ashenarx.project-switcher.settings"
    }
}
