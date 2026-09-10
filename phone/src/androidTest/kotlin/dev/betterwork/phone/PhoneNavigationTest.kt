package dev.betterwork.phone

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.betterwork.data.Appearance
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.platform.TimerService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<BetterApp>()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun browsingAndChangingAppearancePreserveTheRunningSnapshot() {
        device.wakeUp()
        val original = app.repository.appPreferences.value
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                instrumentation.runOnMainSync {
                    app.engine?.stop()
                    app.dismiss()
                    app.start(Presets.japaneseWalk, SessionOptions(limitMs = 30_000))
                }
                assertTrue(device.wait(Until.hasObject(By.text("Pause")), 5000))
                device.wait(Until.findObject(By.desc("Settings")), 5000).click()
                device.wait(Until.findObject(By.text("Appearance")), 5000).click()
                device.wait(Until.findObject(By.text("Dark")), 5000).click()
                activity.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("Appearance")), 5000))
                instrumentation.runOnMainSync {
                    assertEquals(Appearance.DARK, app.repository.appPreferences.value.appearance)
                    val state = requireNotNull(app.sessions.value)
                    assertEquals(Phase.RUNNING, state.phase)
                    assertEquals(Presets.japaneseWalk, state.routine)
                    assertTrue(state.activeMs > 0)
                    assertEquals(1L, state.cueSerial)
                }
                device.pressBack()
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.text("Routines")), 5000))
                assertTrue(device.hasObject(By.text("Tabata")))
                assertTrue(device.hasObject(By.text("Pause")))
            }
        } finally {
            instrumentation.runOnMainSync {
                app.engine?.stop()
                app.stopService(Intent(app, TimerService::class.java))
                app.dismiss()
                app.repository.appPreferences(original)
            }
        }
    }
}
