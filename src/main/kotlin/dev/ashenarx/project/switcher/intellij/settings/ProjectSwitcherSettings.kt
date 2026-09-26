package dev.ashenarx.project.switcher.intellij.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name = "ProjectSwitcherSettings", storages = [Storage("projectSwitcher.xml")], category = SettingsCategory.UI)
class ProjectSwitcherSettings : SimplePersistentStateComponent<ProjectSwitcherSettings.Options>(Options()) {

    class Options : BaseState() {
        var searchField by property(false)
        var showLocation by property(false)
        var showShortcuts by property(false)
    }

    companion object {
        fun getInstance(): ProjectSwitcherSettings = service()
    }
}
