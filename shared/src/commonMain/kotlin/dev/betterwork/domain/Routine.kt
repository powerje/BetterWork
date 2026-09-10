package dev.betterwork.domain

import kotlinx.serialization.Serializable

const val MAX_DURATION_MS: Long = 604_800_000

@Serializable
enum class Cue {
    WORK,
    REST,
    COMPLETE,
    GENTLE,
    NONE,
}

@Serializable
enum class Transition {
    AUTOMATIC,
    CONFIRM,
}

@Serializable data class Step(val name: String, val durationMs: Long, val cue: Cue = Cue.WORK)

/** Blocks contain steps, never other blocks: nesting is limited structurally. */
@Serializable data class Block(val steps: List<Step>, val repetitions: Int = 1)

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val blocks: List<Block>,
    val repeat: Boolean = false,
    val limitMs: Long? = null,
    val transition: Transition = Transition.AUTOMATIC,
) {
    fun validate() {
        require(id.isNotBlank() && id.length <= 100) { "Routine ID must contain 1–100 characters" }
        require(name.isNotBlank() && name.length <= 80) { "Name must contain 1–80 characters" }
        require(blocks.size in 1..100) { "Use 1–100 blocks" }
        require(limitMs == null || limitMs in 1..MAX_DURATION_MS) {
            "Limit must be positive and at most 7 days"
        }
        var expanded = 0L
        blocks.forEach { block ->
            require(block.repetitions in 1..100) { "Repeat a block 1–100 times" }
            require(block.steps.size in 1..100) { "Use 1–100 steps per block" }
            expanded += block.steps.size * block.repetitions
            block.steps.forEach { step ->
                require(step.name.isNotBlank() && step.name.length <= 80) {
                    "Step names must contain 1–80 characters"
                }
                require(step.durationMs in 1..MAX_DURATION_MS) {
                    "Step duration must be positive and at most 7 days"
                }
            }
        }
        require(expanded <= 10_000) { "Routine exceeds 10,000 expanded steps" }
    }

    fun expandedSteps(): List<Step> {
        validate()
        return blocks.flatMap { block -> List(block.repetitions) { block.steps }.flatten() }
    }

    fun frozen(): Routine = copy(blocks = blocks.map { it.copy(steps = it.steps.toList()) })
}

@Serializable
data class SessionOptions(
    val limitMs: Long? = null,
    val cueOverride: Cue? = null,
    val completionCue: Cue = Cue.COMPLETE,
    val sound: Boolean = false,
) {
    fun validate() {
        require(limitMs == null || limitMs in 1..MAX_DURATION_MS) {
            "Limit must be positive and at most 7 days"
        }
    }
}

object Presets {
    val japaneseWalk =
        Routine(
            "preset-walk",
            "Japanese Walk",
            listOf(Block(listOf(Step("Fast", 180_000), Step("Regular", 180_000, Cue.REST)))),
            repeat = true,
            limitMs = 1_800_000,
        )
    val tabata =
        Routine(
            "preset-tabata",
            "Tabata",
            listOf(Block(listOf(Step("Work", 20_000), Step("Rest", 10_000, Cue.REST)), 8)),
        )
    val pomodoro =
        Routine(
            "preset-pomodoro",
            "Pomodoro",
            listOf(
                Block(listOf(Step("Work", 1_500_000), Step("Short break", 300_000, Cue.REST)), 3),
                Block(listOf(Step("Work", 1_500_000), Step("Long break", 900_000, Cue.REST))),
            ),
            repeat = true,
            transition = Transition.CONFIRM,
        )
    val all = listOf(japaneseWalk, tabata, pomodoro)
}
