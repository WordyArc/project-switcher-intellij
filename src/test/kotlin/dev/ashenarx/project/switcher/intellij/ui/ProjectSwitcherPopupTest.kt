package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.input.key.Key
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class ProjectSwitcherPopupTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outcomes = mutableListOf<SwitchOutcome>()

    @BeforeEach
    fun allowSkiaCleaner() {
        ThreadLeakTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Reference Cleaner")
    }

    @AfterEach
    fun cancelScope() {
        scope.cancel("test finished")
    }

    @Test
    fun `typed text narrows the list`() {
        val model = loadedModel(ProjectList(open = listOf(open("alpha")), recent = listOf(recent("beta"), recent("gamma"))))

        runInEdtAndWait {
            popup(model).use { scene ->
                scene.frames()

                scene.type("gam")
                scene.frames()

                assertNull(scene.boundsOf("beta"), "a row that does not match must leave the list")
                assertNotNull(scene.boundsOf("gamma"))
            }
        }
    }

    @Test
    fun `enter opens what was typed even when the keys arrive before the next frame`() {
        val gamma = recent("gamma")
        val model = loadedModel(ProjectList(open = listOf(open("alpha")), recent = listOf(recent("beta"), gamma)))

        runInEdtAndWait {
            popup(model).use { scene ->
                scene.frames()

                scene.type("gamma")
                scene.press(Key.Enter)
            }
        }

        assertEquals(
            listOf(SwitchOutcome.Reopen(gamma, OpenTarget.Ask)),
            outcomes,
            "Enter must act on the text already typed, not on the query of an earlier frame",
        )
    }

    @Test
    fun `moving the selection to a visible row does not scroll the list`() {
        val model = loadedModel(ProjectList(open = emptyList(), recent = manyRows()))

        runInEdtAndWait {
            popup(model).use { scene ->
                scene.frames()
                val before = checkNotNull(scene.boundsOf("project00")) { "the first row must be on screen" }

                scene.press(Key.DirectionDown)
                scene.frames(30)

                assertEquals(
                    before,
                    scene.boundsOf("project00"),
                    "the second row was already visible, so the list must stay where it is",
                )
            }
        }
    }

    @Test
    fun `a row selected below the view is scrolled up to the footer, not to the top`() {
        val model = loadedModel(ProjectList(open = emptyList(), recent = manyRows()))

        runInEdtAndWait {
            popup(model).use { scene ->
                scene.frames()

                scene.press(Key.MoveEnd)
                scene.frames(30)

                val row = checkNotNull(scene.boundsOf("project39")) { "End must bring the last row into view" }
                val footer = checkNotNull(scene.boundsOf("~/projects/project39")) { "the footer names the selection" }
                assertTrue(row.bottom <= footer.top, "the last row must be fully visible above the footer")
                assertNotNull(scene.boundsOf("project30"), "the rows above the selection must stay in view")
            }
        }
    }

    @Test
    fun `a click with shift opens a recent project in the current window`() {
        val beta = recent("beta")
        val model = loadedModel(ProjectList(open = listOf(open("alpha")), recent = listOf(beta)))

        runInEdtAndWait {
            popup(model).use { scene ->
                scene.frames()

                scene.click("beta", shift = true)
                scene.frames()
            }
        }

        assertEquals(listOf(SwitchOutcome.Reopen(beta, OpenTarget.CurrentWindow)), outcomes)
    }

    private fun manyRows() = (0 until 40).map { recent("project%02d".format(it)) }

    private fun loadedModel(projects: ProjectList): ProjectSwitcherModel = timeoutRunBlocking {
        val model = ProjectSwitcherModel(
            coroutineScope = scope,
            actions = FakeProjectActions(projects),
            uiContext = Dispatchers.Unconfined,
        )
        model.load()
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
        model
    }

    private fun popup(model: ProjectSwitcherModel) = PopupScene {
        ProjectSwitcherPopup(model = model, toggleShortcuts = emptyList(), onClose = {}, onResult = { outcomes += it })
    }
}
