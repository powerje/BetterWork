package dev.betterwork.domain

import kotlin.test.*

class SessionTest {
    @Test
    fun changingCuesPreservesBoundaryEventsAndDoesNotReplay() {
        val clock = Clock()
        val session = walk(clock)
        session.drainCues()
        clock.time = 180_000
        session.updateCues(Cue.GENTLE, Cue.NONE, true)
        val boundary = session.drainCues().single()
        assertEquals(Cue.REST, boundary.cue)
        assertFalse(boundary.sound)
        assertEquals(180_000, session.state.activeMs)
        session.updateCues(Cue.GENTLE, Cue.NONE, true)
        assertTrue(session.drainCues().isEmpty())
        clock.time = 360_000
        session.tick()
        val next = session.drainCues().single()
        assertEquals(Cue.GENTLE, next.cue)
        assertTrue(next.sound)
        assertEquals(Cue.WORK, session.state.routine.expandedSteps().first().cue)
        assertEquals(1_800_000, session.state.options.limitMs)
    }

    private class Clock(var time: Long = 0) : MonotonicClock {
        override fun nowMs() = time
    }

    private fun walk(clock: Clock, limit: Long = 1_800_000) =
        Session.start(Presets.japaneseWalk, SessionOptions(limitMs = limit), clock)

    @Test
    fun walkingSchedulesHaveNoCumulativeDriftOrDuplicateCues() {
        for (minutes in listOf(30, 60)) {
            val clock = Clock()
            val session = walk(clock, minutes * 60_000L)
            val cues = session.drainCues().toMutableList()
            // Uneven samples exercise deadline arithmetic rather than counting ticks.
            while (clock.time < minutes * 60_000L) {
                clock.time = minOf(clock.time + 731, minutes * 60_000L)
                session.tick()
                cues += session.drainCues()
                session.tick()
                assertTrue(session.drainCues().isEmpty())
            }
            assertEquals(Phase.COMPLETED, session.state.phase)
            assertEquals(minutes * 60_000L, session.state.activeMs)
            assertEquals(minutes / 3 + 1, cues.size)
            assertEquals((1L..cues.size.toLong()).toList(), cues.map { it.serial })
            assertEquals(Cue.COMPLETE, cues.last().cue)
            cues.dropLast(1).forEachIndexed { i, event ->
                assertEquals(if (i % 2 == 0) Cue.WORK else Cue.REST, event.cue)
            }
        }
    }

    @Test
    fun delayedSampleCrossesEveryBoundaryWithoutChangingDeadline() {
        val clock = Clock()
        val session = walk(clock)
        session.drainCues()
        clock.time = 550_123
        session.tick()
        assertEquals(169_877, session.state.remainingMs)
        assertEquals(550_123, session.state.activeMs)
        assertEquals(listOf(Cue.REST, Cue.WORK, Cue.REST), session.drainCues().map { it.cue })
    }

    @Test
    fun limitStopsMidStepAndAtBoundaryWithoutStartingAnotherStep() {
        for (limit in listOf(100_001L, 180_000L)) {
            val clock = Clock()
            val session = walk(clock, limit)
            session.drainCues()
            clock.time = 200_000
            session.tick()
            assertEquals(limit, session.state.activeMs)
            assertEquals(Phase.COMPLETED, session.state.phase)
            assertEquals(listOf(Cue.COMPLETE), session.drainCues().map { it.cue })
        }
    }

    @Test
    fun pauseAndResumeExcludePausedTime() {
        val clock = Clock()
        val session = walk(clock)
        clock.time = 10_000
        session.pause()
        clock.time = 999_000
        session.tick()
        assertEquals(10_000, session.state.activeMs)
        session.resume()
        clock.time += 20_000
        session.tick()
        assertEquals(30_000, session.state.activeMs)
        assertEquals(150_000, session.state.remainingMs)
    }

    @Test
    fun confirmationWaitDoesNotConsumeActiveTimeOrRepeatCues() {
        val clock = Clock()
        val session = Session.start(Presets.pomodoro, SessionOptions(), clock)
        session.drainCues()
        clock.time = 1_800_000
        session.tick()
        assertEquals(Phase.WAITING, session.state.phase)
        assertEquals(1_500_000, session.state.activeMs)
        assertEquals(1, session.drainCues().size)
        clock.time += 9_000_000
        session.tick()
        assertTrue(session.drainCues().isEmpty())
        session.confirm()
        assertEquals("Short break", session.step.name)
        assertEquals(1_500_000, session.state.activeMs)
        assertEquals(listOf(Cue.REST), session.drainCues().map { it.cue })
    }

    @Test
    fun tabataCompletesExactlyEightRounds() {
        val clock = Clock()
        val session = Session.start(Presets.tabata, SessionOptions(), clock)
        clock.time = 500_000
        session.tick()
        assertEquals(240_000, session.state.activeMs)
        assertEquals(Phase.COMPLETED, session.state.phase)
        assertEquals(17, session.drainCues().size)
    }

    @Test
    fun pomodoroHasLongBreakAfterFourthWork() {
        assertEquals(
            listOf(
                "Work",
                "Short break",
                "Work",
                "Short break",
                "Work",
                "Short break",
                "Work",
                "Long break",
            ),
            Presets.pomodoro.expandedSteps().map { it.name },
        )
    }

    @Test
    fun skipDoesNotAddDiscardedTimeAndPreservesPause() {
        val clock = Clock()
        val session = walk(clock)
        clock.time = 5_000
        session.pause()
        session.drainCues()
        session.skip()
        assertEquals(Phase.PAUSED, session.state.phase)
        assertEquals("Regular", session.step.name)
        assertEquals(5_000, session.state.activeMs)
        assertTrue(session.drainCues().isEmpty())
        session.resume()
        clock.time += 180_000
        session.tick()
        assertEquals("Fast", session.step.name)
        assertEquals(2, session.state.cycle)
    }

    @Test
    fun stopIsTerminalAndHasNoCompletionCue() {
        val clock = Clock()
        val session = walk(clock)
        session.stop()
        clock.time = 10_000_000
        session.tick()
        session.resume()
        session.confirm()
        session.skip()
        assertEquals(Phase.STOPPED, session.state.phase)
        assertTrue(session.drainCues().isEmpty())
    }

    @Test
    fun recoveryRequiresResumeAndIgnoresDowntime() {
        val clock = Clock()
        val session = walk(clock)
        clock.time = 5_000
        session.tick()
        val recovered = Session.recover(session.state, Clock(99_999_999))
        assertEquals(Phase.PAUSED, recovered.state.phase)
        assertEquals(175_000, recovered.state.remainingMs)
        assertTrue(recovered.drainCues().isEmpty())
    }

    @Test
    fun runningSessionOwnsAnImmutableRoutineSnapshot() {
        val steps = mutableListOf(Step("Original", 10_000))
        val blocks = mutableListOf(Block(steps))
        val routine = Routine("custom", "Custom", blocks)
        val session = Session.start(routine, SessionOptions(), Clock())
        steps.clear()
        blocks.clear()
        assertEquals("Original", session.step.name)
        assertEquals(1, session.state.routine.blocks.size)
    }

    @Test
    fun malformedRoutinesAndCheckpointsFailEarly() {
        assertFailsWith<IllegalArgumentException> {
            Presets.tabata.copy(blocks = emptyList()).validate()
        }
        assertFailsWith<IllegalArgumentException> { Presets.tabata.copy(limitMs = 0).validate() }
        assertFailsWith<IllegalArgumentException> {
            Routine("id", "name", listOf(Block(listOf(Step("", -1))))).validate()
        }
        val checkpoint = walk(Clock()).state
        assertFailsWith<IllegalArgumentException> {
            Session.recover(checkpoint.copy(index = 999), Clock())
        }
    }
}
