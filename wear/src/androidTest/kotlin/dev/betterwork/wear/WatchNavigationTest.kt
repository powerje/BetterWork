package dev.betterwork.wear

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.betterwork.domain.Phase
import dev.betterwork.domain.Presets
import dev.betterwork.platform.BetterApp
import dev.betterwork.platform.TimerService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<BetterApp>()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun rotaryNavigationAndLimitShortcutPreserveSavedRoutine() {
        device.wakeUp()
        instrumentation.runOnMainSync { app.stopService(Intent(app, TimerService::class.java)) }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            app.engine?.stop()
            app.dismiss()
        }
        val originalPreferences = app.repository.preferences()
        val originalLibrary = app.repository.library.value
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                requireNotNull(device.wait(Until.findObject(By.text("Japanese Walk")), 5000))
                    .click()
                rotaryTo("Adjust this session", -1f).click()
                rotaryTo("Active time limit", -1f).click()
                rotaryTo("60 minutes", -1f).click()
                device.pressBack()
                device.pressBack()
                rotaryTo("Start", 1f).click()
                assertTrue(device.wait(Until.hasObject(By.text("Pause")), 5000))
                assertEquals(3_600_000L, app.sessions.value?.options?.limitMs)
                assertEquals(Presets.japaneseWalk, app.sessions.value?.routine)
                assertEquals(originalLibrary, app.repository.library.value)
                assertEquals(originalPreferences, app.repository.preferences())
                activity.recreate()
                activity.onActivity {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                rotaryTo("Controls", -1f).click()
                rotaryTo("Stop session", -1f).click()
                rotaryTo("Stop", -1f).click()
                assertTrue(device.wait(Until.hasObject(By.text("Done")), 5000))
                assertEquals(Phase.STOPPED, app.sessions.value?.phase)
            }
        } finally {
            instrumentation.runOnMainSync { app.stopService(Intent(app, TimerService::class.java)) }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                app.engine?.stop()
                app.dismiss()
                app.repository.preferences(originalPreferences)
            }
        }
    }

    private fun visibleOnRoundScreen(bounds: android.graphics.Rect): Boolean {
        val centerX = device.displayWidth / 2.0
        val centerY = device.displayHeight / 2.0
        val radius = minOf(centerX, centerY) - 8
        return listOf(
                bounds.left to bounds.top,
                bounds.right to bounds.top,
                bounds.left to bounds.bottom,
                bounds.right to bounds.bottom,
            )
            .all { (x, y) -> kotlin.math.hypot(x - centerX, y - centerY) <= radius }
    }

    private fun rotaryTo(label: String, direction: Float): UiObject2 {
        repeat(60) {
            device.waitForIdle(1000)
            val control = device.findObject(By.text(label))
            if (control != null) {
                val bounds = control.visibleBounds
                if (visibleOnRoundScreen(bounds)) {
                    return control
                }
            }
            val now = SystemClock.uptimeMillis()
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val scrollDirection =
                control?.visibleBounds?.centerY()?.let {
                    if (it < device.displayHeight / 2) 1f else -1f
                } ?: direction
            val rotaryDevice =
                InputDevice.getDeviceIds()
                    .asSequence()
                    .mapNotNull(InputDevice::getDevice)
                    .firstOrNull { it.supportsSource(InputDevice.SOURCE_ROTARY_ENCODER) }
            val coordinates =
                MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_SCROLL, scrollDirection * 0.1f)
                }
            val event =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_SCROLL,
                    1,
                    arrayOf(properties),
                    arrayOf(coordinates),
                    0,
                    0,
                    1f,
                    1f,
                    rotaryDevice?.id ?: 0,
                    0,
                    InputDevice.SOURCE_ROTARY_ENCODER,
                    0,
                )
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            SystemClock.sleep(500)
        }
        val dump = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(dump)
        dump.toString().chunked(3000).forEach { android.util.Log.i("ROTARY_DUMP", it) }
        throw AssertionError("Rotary scrolling did not make $label usable on the round display")
    }
}
