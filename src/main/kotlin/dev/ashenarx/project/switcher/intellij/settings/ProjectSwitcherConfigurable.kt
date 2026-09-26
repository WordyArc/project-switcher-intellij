package dev.ashenarx.project.switcher.intellij.settings

import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

private val CLOSE_SHORTCUT: KeyStroke =
    KeyStroke.getKeyStroke(KeyEvent.VK_W, if (SystemInfoRt.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK)

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
                buttonsGroup(ProjectSwitcherBundle.message("settings.close.with")) {
                    row {
                        radioButton(KeymapUtil.getKeystrokeText(CLOSE_SHORTCUT), false)
                    }
                    row {
                        radioButton(ProjectSwitcherBundle.message("settings.close.with.delete"), true)
                            .comment(ProjectSwitcherBundle.message("settings.close.with.delete.comment"))
                    }
                }.bind(options::closeOnDelete)
            }
        }
    }

    companion object {
        const val ID = "dev.ashenarx.project-switcher.settings"
    }
}
