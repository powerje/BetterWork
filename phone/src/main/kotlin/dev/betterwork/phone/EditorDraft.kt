package dev.betterwork.phone

import dev.betterwork.data.*
import dev.betterwork.domain.*
import dev.betterwork.presentation.durationSeconds
import dev.betterwork.presentation.parseDurationSeconds
import java.util.UUID

internal fun localId(): String = UUID.randomUUID().toString()

internal fun Routine.toDraft(source: Routine? = this) =
    RoutineDraft(
        id,
        source,
        name,
        blocks.map { block ->
            DraftBlock(
                localId(),
                block.repetitions.toString(),
                block.steps.map {
                    DraftStep(localId(), it.name, durationSeconds(it.durationMs), it.cue)
                },
            )
        },
        repeat,
        transition == Transition.CONFIRM,
        limitMs?.let(::durationSeconds) ?: "",
    )

internal fun DraftStep.durationMs(): Long = duration?.toMillis() ?: parseDurationSeconds(seconds)

internal fun RoutineDraft.limitMs(): Long? =
    limitDuration?.toMillis()
        ?: limitSeconds.takeIf { it.isNotBlank() }?.let(::parseDurationSeconds)

internal fun RoutineDraft.toRoutine() =
    Routine(
        id,
        name,
        blocks.map { block ->
            Block(
                block.steps.map { Step(it.name, it.durationMs(), it.cue) },
                block.repetitions.toIntOrNull() ?: 0,
            )
        },
        repeat,
        limitMs(),
        if (confirm) Transition.CONFIRM else Transition.AUTOMATIC,
    )

internal fun durationFields(ms: Long) =
    DraftDuration(
        minutes = ((ms / 60_000) % 60).toString(),
        seconds = ((ms / 1000) % 60).toString(),
        hours = (ms / 3_600_000).toString(),
        milliseconds = (ms % 1000).toString(),
    )

internal fun DraftDuration.toMillis(): Long {
    fun component(text: String, max: Long): Long {
        val value = text.toLongOrNull()
        require(value != null && value in 0..max) { "Use whole numbers within the duration range" }
        return value
    }
    val total =
        component(hours, 168) * 3_600_000 +
            component(minutes, 59) * 60_000 +
            component(seconds, 59) * 1000 +
            component(milliseconds, 999)
    require(total in 1..MAX_DURATION_MS) { "Duration must be positive and at most 7 days" }
    return total
}

internal fun DraftStep.problem(): String? =
    when {
        name.isBlank() || name.length > 80 -> "Step name must contain 1–80 characters"
        else -> runCatching { durationMs() }.exceptionOrNull()?.message
    }

internal fun RoutineDraft.replaceBlock(value: DraftBlock) =
    copy(blocks = blocks.map { if (it.id == value.id) value else it })

internal fun RoutineDraft.focusProblem(): String? {
    val target = focus ?: return null
    val block = blocks.firstOrNull { it.id == target.blockId }
    return when (target.screen) {
        "step" -> block?.steps?.firstOrNull { it.id == target.stepId }?.problem()
        "count" -> if (block?.repetitions?.toIntOrNull() !in 1..100) "Repeat 1–100 times" else null
        "rules" -> runCatching { limitMs() }.exceptionOrNull()?.message
        else -> null
    }
}

internal fun RoutineDraft.discardFocus(): RoutineDraft {
    val target = focus ?: return this
    return if (target.screen == "rules")
        copy(
            repeat = target.repeat,
            confirm = target.confirm,
            limitSeconds = target.limitSeconds,
            limitDuration = target.limitDuration,
            focus = null,
        )
    else target.originalBlock?.let { replaceBlock(it).copy(focus = null) } ?: copy(focus = null)
}

internal fun RoutineDraft.firstInvalidFocus(): DraftFocus? {
    blocks.forEach { block ->
        if (block.repetitions.toIntOrNull() !in 1..100)
            return DraftFocus("count", block.id, originalBlock = block)
        block.steps
            .firstOrNull { it.problem() != null }
            ?.let {
                return DraftFocus("step", block.id, it.id, block)
            }
    }
    if (runCatching { limitMs() }.isFailure)
        return DraftFocus(
            "rules",
            repeat = repeat,
            confirm = confirm,
            limitSeconds = limitSeconds,
            limitDuration = limitDuration,
        )
    return null
}

internal fun <T> List<T>.moved(index: Int, delta: Int): List<T> =
    toMutableList().apply {
        val destination = index + delta
        if (destination in indices) add(destination, removeAt(index))
    }
