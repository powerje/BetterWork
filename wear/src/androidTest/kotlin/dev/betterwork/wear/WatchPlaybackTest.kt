package dev.betterwork.wear

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.platform.TimerService
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<BetterApp>()
    private val device = UiDevice.getInstance(instrumentation)

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun capture(name: String) {
        SystemClock.sleep(600) // Allow the platform scene transition to finish before capture.
        val directory = requireNotNull(app.getExternalFilesDir("redesign"))
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }

    private fun openActivity(): ActivityScenario<MainActivity> {
        device.wakeUp()
        cleanup()
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        return ActivityScenario.launch(MainActivity::class.java).also { activity ->
            activity.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun cleanup() {
        onMain { app.stopService(Intent(app, TimerService::class.java)) }
        instrumentation.waitForIdleSync()
        onMain {
            app.engine?.stop()
            app.dismiss()
        }
    }

    private fun openTimer() {
        val manager = app.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (
            manager.activeNotifications.isEmpty() && SystemClock.elapsedRealtime() < deadline
        ) SystemClock.sleep(10)
        manager.activeNotifications.first().notification.contentIntent.send()
    }

    private fun click(text: String) {
        requireNotNull(device.wait(Until.findObject(By.text(text)), 5000)).click()
    }

    private fun swipeBack() {
        device.swipe(
            2,
            device.displayHeight / 2,
            device.displayWidth - 2,
            device.displayHeight / 2,
            20,
        )
        SystemClock.sleep(600) // A second swipe must start after the previous scene has settled.
    }

    @Test
    fun firstViewAndSwipeNavigationPreserveRunningSession() {
        try {
            openActivity().use { activity ->
                val routine =
                    Routine("glance", "Glance check", listOf(Block(listOf(Step("Fast", 30_000)))))
                onMain { app.start(routine, SessionOptions(limitMs = 30_000)) }
                openTimer()
                val pause = requireNotNull(device.wait(Until.findObject(By.text("Pause")), 5000))
                assertTrue(pause.visibleBounds.bottom < device.displayHeight)
                assertTrue(device.hasObject(By.text("Fast")))
                assertTrue(device.hasObject(By.text("Controls")))
                assertFalse(device.hasObject(By.text("Stop session")))
                capture("timer-running-small")
                click("Controls")
                assertTrue(device.wait(Until.hasObject(By.text("Skip step")), 5000))
                capture("controls-small")
                swipeBack()
                assertTrue(device.wait(Until.hasObject(By.text("Pause")), 5000))
                swipeBack()
                capture("after-library-swipe")
                assertTrue(device.wait(Until.hasObject(By.text("Active session")), 5000))
                assertTrue(device.hasObject(By.text("Japanese Walk")))
                activity.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("Active session")), 5000))
                onMain {
                    val session = requireNotNull(app.sessions.value)
                    assertEquals(Phase.RUNNING, session.phase)
                    assertEquals(routine, session.routine)
                    assertEquals(1L, session.cueSerial)
                    assertTrue(session.activeMs > 0)
                }
            }
        } finally {
            cleanup()
        }
    }

    @Test
    fun confirmationWaitHasContinueAndRetainsItsNextStep() {
        try {
            openActivity().use {
                val routine =
                    Routine(
                        "wait",
                        "Wait check",
                        listOf(Block(listOf(Step("Fast", 3000), Step("Regular", 3000, Cue.REST)))),
                        transition = Transition.CONFIRM,
                    )
                onMain { app.start(routine, SessionOptions()) }
                openTimer()
                val waitingVisible = device.wait(Until.hasObject(By.text("Continue")), 7000)
                capture("confirmation-entry")
                assertTrue(
                    "Continue should be visible; engine phase = ${app.sessions.value?.phase}",
                    waitingVisible,
                )
                capture("timer-waiting-small")
                onMain { assertEquals(Phase.WAITING, app.sessions.value?.phase) }
                click("Controls")
                assertTrue(device.wait(Until.hasObject(By.text("Next: Regular · 0:03")), 5000))
                assertFalse(device.hasObject(By.text("Skip step")))
            }
        } finally {
            cleanup()
        }
    }
}
