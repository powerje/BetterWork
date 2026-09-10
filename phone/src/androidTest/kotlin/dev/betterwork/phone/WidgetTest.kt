package dev.betterwork.phone

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<BetterApp>()
    private val device = UiDevice.getInstance(instrumentation)
    private val manager = AppWidgetManager.getInstance(app)
    private val host = AppWidgetHost(app, WidgetHostActivity.HOST_ID)
    private val ids = mutableListOf<Int>()
    private val routines = mutableListOf<String>()

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Before
    fun prepare() {
        device.wakeUp()
        onMain { app.stopService(Intent(app, TimerService::class.java)) }
        instrumentation.waitForIdleSync()
        onMain {
            app.engine?.stop()
            app.dismiss()
        }
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.BIND_APPWIDGET
        )
    }

    @After
    fun cleanup() {
        ids.forEach(host::deleteAppWidgetId)
        onMain { app.stopService(Intent(app, TimerService::class.java)) }
        instrumentation.waitForIdleSync()
        onMain {
            app.engine?.stop()
            app.dismiss()
            routines.forEach(app.repository::delete)
        }
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    private fun allocate(): Int {
        val id = host.allocateAppWidgetId()
        assertTrue(
            manager.bindAppWidgetIdIfAllowed(
                id,
                ComponentName(app, RoutineWidgetReceiver::class.java),
            )
        )
        ids += id
        return id
    }

    private fun routine(): Routine =
        Routine(localId(), "Widget intervals", listOf(Block(listOf(Step("Work", 30_000))))).also {
            routines += it.id
            onMain { app.repository.upsert(it) }
        }

    private fun show(vararg id: Int, height: Int = 112): ActivityScenario<WidgetHostActivity> =
        ActivityScenario.launch(
            Intent(app, WidgetHostActivity::class.java)
                .putExtra("widgetIds", id)
                .putExtra("heightDp", height)
        )

    private fun refresh() = runBlocking { WidgetUpdates.refresh(app) }

    private fun capture(name: String) {
        SystemClock.sleep(600)
        assertTrue(
            device.takeScreenshot(
                File(requireNotNull(app.getExternalFilesDir("redesign")), "$name.png")
            )
        )
    }

    private fun click(text: String) {
        requireNotNull(device.wait(Until.findObject(By.text(text)), 10_000)).click()
    }

    private fun awaitPhase(phase: Phase) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (
            app.sessions.value?.phase != phase && SystemClock.elapsedRealtime() < deadline
        ) SystemClock.sleep(10)
        assertEquals(phase, app.sessions.value?.phase)
    }

    @Test
    fun realWidgetsResolveEditsAndOpenAnExistingPausedSession() {
        val original = routine()
        val first = allocate()
        val second = allocate()
        val bindings = WidgetBindings(app)
        bindings.bind(first, original.id)
        bindings.bind(second, Presets.tabata.id)
        refresh()
        show(first, second).use {
            assertTrue(device.wait(Until.hasObject(By.text(original.name)), 15_000))
            val edited = original.copy(name = "Morning intervals")
            onMain { app.repository.upsert(edited) }
            assertTrue(device.wait(Until.hasObject(By.text(edited.name)), 15_000))
            capture("widgets-small")
            click("Start")
            assertTrue(device.wait(Until.hasObject(By.text("Pause")), 5000))
            onMain {
                assertEquals(edited, app.sessions.value?.routine)
                app.command(SessionCommand.PAUSE)
            }
            awaitPhase(Phase.PAUSED)
            val handle = app.sessionHandle
            val snapshot = app.sessions.value
            show(second).use {
                requireNotNull(device.wait(Until.findObject(By.desc("Open active timer")), 10_000))
                    .click()
                assertTrue(device.wait(Until.hasObject(By.text("Resume")), 5000))
                onMain {
                    assertEquals(handle, app.sessionHandle)
                    assertEquals(snapshot, app.sessions.value)
                }
            }
        }
    }

    @Test
    fun singleRowWidgetKeepsItsStartActionReachable() {
        val routine = routine()
        val id = allocate()
        WidgetBindings(app).bind(id, routine.id)
        refresh()
        show(id, height = 64).use {
            val label = "Start ${routine.name}"
            assertTrue(device.wait(Until.hasObject(By.text(label)), 15_000))
            capture("widget-single-row")
            click(label)
            assertTrue(device.wait(Until.hasObject(By.text("Pause")), 5000))
            onMain { assertEquals(routine, app.sessions.value?.routine) }
        }
    }

    @Test
    fun configurationCancellationPreservesBindingAndSaveReconfigures() {
        val id = allocate()
        WidgetBindings(app).bind(id, Presets.japaneseWalk.id)
        val intent = widgetConfigurationIntent(app, id)
        ActivityScenario.launch<WidgetConfigurationActivity>(intent).use {
            click("Tabata")
            requireNotNull(device.wait(Until.findObject(By.desc("Cancel")), 5000)).click()
        }
        assertEquals(Presets.japaneseWalk.id, WidgetBindings(app).read(id).routineId)
        ActivityScenario.launch<WidgetConfigurationActivity>(intent).use {
            click("Tabata")
            capture("widget-configuration")
            click("Save")
        }
        assertEquals(Presets.tabata.id, WidgetBindings(app).read(id).routineId)
    }

    @Test
    fun deletedRoutineShowsConfigurationAndRestoredIdsKeepOnlyIdentity() {
        val original = routine()
        val old = allocate()
        WidgetBindings(app).bind(old, original.id)
        val restored = allocate()
        WidgetBindings(app).restore(intArrayOf(old), intArrayOf(restored))
        assertNull(WidgetBindings(app).read(old).routineId)
        assertEquals(original.id, WidgetBindings(app).read(restored).routineId)
        onMain {
            app.repository.delete(original.id)
            routines.remove(original.id)
        }
        refresh()
        show(restored, height = 160).use {
            assertTrue(device.wait(Until.hasObject(By.text("Choose a routine")), 15_000))
            capture("widget-missing")
            click("Choose")
            assertTrue(device.wait(Until.hasObject(By.text("Widget routine")), 5000))
            assertFalse(app.hasActiveSession)
        }
    }

    @Test
    fun duplicateActivationAndRecreationCannotStartTwoSessions() {
        val routine = routine()
        val id = allocate()
        WidgetBindings(app).bind(id, routine.id)
        val token = WidgetBindings(app).read(id).activationToken
        val intent =
            Intent(app, MainActivity::class.java)
                .setAction(WIDGET_START)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(WIDGET_TOKEN, token)
        ActivityScenario.launch<MainActivity>(intent).use { activity ->
            awaitPhase(Phase.RUNNING)
            val handle = app.sessionHandle
            onMain { assertEquals(WidgetActivation.OPEN_TIMER, activateWidget(app, id, token)) }
            assertEquals(handle, app.sessionHandle)
            onMain { app.command(SessionCommand.STOP) }
            awaitPhase(Phase.STOPPED)
            activity.recreate()
            assertTrue(device.wait(Until.hasObject(By.text("Done")), 5000))
            onMain {
                assertEquals(handle, app.sessionHandle)
                assertEquals(Phase.STOPPED, app.sessions.value?.phase)
            }
        }
    }
}
