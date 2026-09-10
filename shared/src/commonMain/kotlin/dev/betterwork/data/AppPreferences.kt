package dev.betterwork.data

import dev.betterwork.domain.Cue
import dev.betterwork.domain.Routine
import dev.betterwork.domain.SessionOptions
import kotlinx.serialization.Serializable

@Serializable
enum class Appearance {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
data class AppPreferences(
    val version: Int = 1,
    val appearance: Appearance = Appearance.SYSTEM,
    val wallpaperColors: Boolean = false,
    val sound: Boolean = false,
    val completionCue: Cue = Cue.COMPLETE,
) {
    fun optionsFor(routine: Routine) =
        SessionOptions(limitMs = routine.limitMs, sound = sound, completionCue = completionCue)
}

/** Raw editor input is saved separately from validated library routines. */
@Serializable
data class RoutineDraft(
    val id: String,
    val source: Routine?,
    val name: String,
    val blocks: List<DraftBlock>,
    val repeat: Boolean = false,
    val confirm: Boolean = false,
    val limitSeconds: String = "",
    val version: Int = 1,
    val limitDuration: DraftDuration? = null,
    val focus: DraftFocus? = null,
)

@Serializable
data class DraftBlock(val id: String, val repetitions: String = "1", val steps: List<DraftStep>)

@Serializable
data class DraftStep(
    val id: String,
    val name: String = "Work",
    val seconds: String = "60",
    val cue: Cue = Cue.WORK,
    val duration: DraftDuration? = null,
)

@Serializable
data class DraftDuration(
    val minutes: String = "1",
    val seconds: String = "0",
    val hours: String = "0",
    val milliseconds: String = "0",
)

/** A focused form keeps its entry value so invalid edits can be discarded after recovery. */
@Serializable
data class DraftFocus(
    val screen: String,
    val blockId: String = "",
    val stepId: String = "",
    val originalBlock: DraftBlock? = null,
    val repeat: Boolean = false,
    val confirm: Boolean = false,
    val limitSeconds: String = "",
    val limitDuration: DraftDuration? = null,
)
