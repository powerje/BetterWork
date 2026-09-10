package dev.betterwork.phone

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import dev.betterwork.platform.BetterApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun editorSwitchesExposeTheirLabelsAndToggleFromTheLabelArea() {
        device.wakeUp()
        ActivityScenario.launch(MainActivity::class.java).use {
            device.wait(Until.findObject(By.text("Japanese Walk")), 5000).click()
            val edit = device.wait(Until.findObject(By.text("Edit")), 5000)
            assertNotNull("A bundled routine can be edited", edit)
            edit.click()
            device.wait(Until.findObject(By.text("Session rules")), 5000).click()
            assertLabeledSwitch("Repeat routine")
            assertLabeledSwitch("Confirm before next step")
        }
    }

    @Test
    fun soundSwitchExposesItsLabelAndPersistsChanges() {
        val app = ApplicationProvider.getApplicationContext<BetterApp>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var original = false
        instrumentation.runOnMainSync { original = app.repository.preferences().sound }
        device.wakeUp()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                device.wait(Until.findObject(By.desc("Settings")), 5000).click()
                device.wait(Until.findObject(By.text("Default cues")), 5000).click()
                assertLabeledSwitch("Sound")
                instrumentation.runOnMainSync {
                    assertEquals(!original, app.repository.preferences().sound)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                app.repository.preferences(app.repository.preferences().copy(sound = original))
            }
        }
    }

    private fun assertLabeledSwitch(label: String) {
        if (!device.wait(Until.hasObject(By.text(label)), 2000)) {
            UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(label)
        }
        val selector = By.checkable(true).hasDescendant(By.text(label))
        val control = device.wait(Until.findObject(selector), 5000)
        assertNotNull("$label must belong to a checkable accessibility group", control)
        val checked = control.isChecked
        val bounds = control.visibleBounds
        // The text side of the row is interactive, not just the switch thumb.
        device.click(bounds.right - 24, bounds.centerY())
        assertTrue(
            "$label toggles from its label area",
            device.wait(Until.hasObject(selector.checked(!checked)), 5000),
        )
    }
}
