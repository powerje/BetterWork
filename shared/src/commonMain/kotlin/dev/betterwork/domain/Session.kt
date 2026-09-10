package dev.betterwork.domain

import kotlinx.serialization.Serializable

fun interface MonotonicClock {
    fun nowMs(): Long
}

@Serializable
enum class Phase {
    RUNNING,
    PAUSED,
    WAITING,
    COMPLETED,
    STOPPED,
}

@Serializable
data class Checkpoint(
    val routine: Routine,
    val options: SessionOptions,
    val index: Int = 0,
    val cycle: Long = 1,
    val remainingMs: Long,
    val activeMs: Long = 0,
    val phase: Phase = Phase.RUNNING,
    val cueSerial: Long = 0,
)

data class CueEvent(val serial: Long, val cue: Cue, val sound: Boolean = false)

@Serializable
enum class SessionCommand(val action: String) {
    PAUSE("pause"),
    RESUME("resume"),
    CONTINUE("confirm"),
    SKIP("skip"),
    STOP("stop"),
}

/**
 * Pure state transitions; only the injected clock supplies time. No UI lifecycle or tick counting.
 */
class Session private constructor(initial: Checkpoint, private val clock: MonotonicClock) {
    private val steps = initial.routine.expandedSteps()
    var state: Checkpoint = initial
        private set

    private var anchor = clock.nowMs()
    private val events = mutableListOf<CueEvent>()
    val step: Step
        get() = steps[state.index]

    fun drainCues(): List<CueEvent> = events.toList().also { events.clear() }

    /** Account for old boundaries before applying options to future cue events. */
    fun updateCues(override: Cue?, completion: Cue, sound: Boolean) {
        tick()
        if (state.phase !in listOf(Phase.COMPLETED, Phase.STOPPED)) {
            state =
                state.copy(
                    options =
                        state.options.copy(
                            cueOverride = override,
                            completionCue = completion,
                            sound = sound,
                        )
                )
        }
    }

    fun tick() {
        val now = clock.nowMs()
        var elapsed = (now - anchor).coerceAtLeast(0)
        anchor = now
        while (state.phase == Phase.RUNNING && elapsed > 0) {
            val budget = state.options.limitMs?.minus(state.activeMs) ?: Long.MAX_VALUE
            val consumed = minOf(elapsed, state.remainingMs, budget)
            state =
                state.copy(
                    remainingMs = state.remainingMs - consumed,
                    activeMs = state.activeMs + consumed,
                )
            elapsed -= consumed
            when {
                state.options.limitMs != null && state.activeMs >= state.options.limitMs!! ->
                    complete()
                state.remainingMs == 0L -> boundary()
            }
        }
    }

    fun pause() {
        tick()
        if (state.phase == Phase.RUNNING) state = state.copy(phase = Phase.PAUSED)
    }

    fun resume() {
        anchor = clock.nowMs()
        if (state.phase == Phase.PAUSED) state = state.copy(phase = Phase.RUNNING)
    }

    fun confirm() {
        anchor = clock.nowMs()
        if (state.phase == Phase.WAITING) advance()
    }

    fun skip() {
        tick()
        if (state.phase in listOf(Phase.RUNNING, Phase.PAUSED, Phase.WAITING)) {
            val paused = state.phase == Phase.PAUSED
            advance(emitCue = !paused)
            if (paused && state.phase == Phase.RUNNING) state = state.copy(phase = Phase.PAUSED)
        }
    }

    fun stop() {
        tick()
        if (state.phase != Phase.COMPLETED) state = state.copy(phase = Phase.STOPPED)
        events.clear()
    }

    private fun boundary() {
        if (!state.routine.repeat && state.index == steps.lastIndex) complete()
        else if (state.routine.transition == Transition.CONFIRM) {
            state = state.copy(phase = Phase.WAITING)
            cue(state.options.completionCue)
        } else advance()
    }

    private fun advance(emitCue: Boolean = true) {
        if (state.index == steps.lastIndex && !state.routine.repeat) {
            complete()
            return
        }
        val next = (state.index + 1) % steps.size
        state =
            state.copy(
                index = next,
                cycle = state.cycle + if (next == 0) 1 else 0,
                remainingMs = steps[next].durationMs,
                phase = Phase.RUNNING,
            )
        if (emitCue) cue(state.options.cueOverride ?: step.cue)
    }

    private fun complete() {
        state = state.copy(phase = Phase.COMPLETED)
        cue(state.options.completionCue)
    }

    private fun cue(pattern: Cue) {
        state = state.copy(cueSerial = state.cueSerial + 1)
        events += CueEvent(state.cueSerial, pattern, state.options.sound)
    }

    companion object {
        fun start(routine: Routine, options: SessionOptions, clock: MonotonicClock): Session {
            routine.validate()
            options.validate()
            val frozen = routine.frozen()
            return Session(
                    Checkpoint(
                        frozen,
                        options,
                        remainingMs = frozen.expandedSteps().first().durationMs,
                    ),
                    clock,
                )
                .also { it.cue(options.cueOverride ?: it.step.cue) }
        }

        fun recover(checkpoint: Checkpoint, clock: MonotonicClock): Session {
            val steps = checkpoint.routine.expandedSteps()
            checkpoint.options.validate()
            require(checkpoint.index in steps.indices && checkpoint.cycle > 0)
            require(checkpoint.remainingMs in 0..steps[checkpoint.index].durationMs)
            require(checkpoint.activeMs >= 0 && checkpoint.cueSerial >= 0)
            require(
                checkpoint.options.limitMs == null ||
                    checkpoint.activeMs <= checkpoint.options.limitMs
            )
            require(checkpoint.phase != Phase.RUNNING || checkpoint.remainingMs > 0)
            val phase = if (checkpoint.phase == Phase.RUNNING) Phase.PAUSED else checkpoint.phase
            return Session(
                checkpoint.copy(routine = checkpoint.routine.frozen(), phase = phase),
                clock,
            )
        }
    }
}
