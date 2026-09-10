package dev.betterwork.presentation

import app.cash.turbine.test
import dev.betterwork.data.Library
import dev.betterwork.domain.*
import kotlin.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class PresenterTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun unchangedCountdownDigitsDoNotPublishTicksButPhaseChangesKeepExactTime() = runTest {
        val routine = Routine("short", "Short", listOf(Block(listOf(Step("Work", 2000)))))
        val library = MutableStateFlow(Library(origin = "test", routines = listOf(routine)))
        val initial = Session.start(routine, SessionOptions(), MonotonicClock { 0 }).state
        val session = MutableStateFlow<Checkpoint?>(initial)
        backgroundScope.present(library, session).test {
            awaitItem()
            session.value = initial.copy(activeMs = 100, remainingMs = 1900)
            assertEquals("0:02", awaitItem().timer.time)
            session.value = initial.copy(activeMs = 200, remainingMs = 1800)
            runCurrent()
            expectNoEvents()
            session.value = session.value!!.copy(phase = Phase.PAUSED)
            assertEquals(1800L, awaitItem().timer.session?.remainingMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presenterUsesPartialLimitAndFrozenNextStep() = runTest {
        val library = MutableStateFlow(Library(origin = "test", routines = Presets.all))
        val engine =
            Session.start(
                Presets.japaneseWalk,
                SessionOptions(limitMs = 3_000),
                MonotonicClock { 0 },
            )
        val session = MutableStateFlow<Checkpoint?>(engine.state)
        backgroundScope.present(library, session).test {
            val timer = awaitItem().timer
            assertEquals("0:03", timer.time)
            assertTrue(timer.endsDuringStep)
            assertEquals("Regular", timer.nextStep?.name)
            assertEquals(SessionCommand.PAUSE, timer.primaryCommand)
            library.value = library.value.copy(routines = emptyList())
            assertEquals("Regular", awaitItem().timer.nextStep?.name)
            session.value = engine.state.copy(phase = Phase.WAITING, remainingMs = 0)
            val waiting = awaitItem().timer
            assertEquals("Continue", waiting.primaryLabel)
            assertEquals("0:00", waiting.time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presenterObservesEngineStateWithoutOwningItsExecution() = runTest {
        val library = MutableStateFlow(Library(origin = "test", routines = Presets.all))
        val session = MutableStateFlow<Checkpoint?>(null)
        val ui = backgroundScope.present(library, session)
        ui.test {
            assertEquals("Choose a routine", awaitItem().timer.status)
            session.value =
                Session.start(Presets.japaneseWalk, SessionOptions(), MonotonicClock { 0 }).state
            val active = awaitItem()
            assertEquals("Fast", active.timer.label)
            assertEquals("3:00", active.timer.time)
            session.value = session.value!!.copy(phase = Phase.PAUSED)
            assertEquals("Paused", awaitItem().timer.status)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
