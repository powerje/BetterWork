package dev.betterwork.phone

import androidx.compose.runtime.*
import dev.betterwork.data.*
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp

internal class EditorState(val app: BetterApp, val id: String) {
    private val stored = app.repository.draft(id)
    private val source = app.repository.library.value.routines.firstOrNull { it.id == id }
    var draft by
        mutableStateOf(
            stored
                ?: (source ?: Routine(id, "", listOf(Block(listOf(Step("Work", 60_000)))))).toDraft(
                    source
                )
        )
        private set

    var error by mutableStateOf<String?>(null)
    var dialog by mutableStateOf<String?>(null)
    var undo by mutableStateOf<RoutineDraft?>(null)
    val dirty: Boolean
        get() = runCatching { draft.toRoutine() != draft.source }.getOrDefault(true)

    fun update(value: RoutineDraft) {
        draft = value
        app.repository.saveDraft(value)
        error = null
        undo = null
    }

    fun remove(value: RoutineDraft) {
        val previous = draft
        update(value)
        undo = previous
    }

    fun restoreRemoval() {
        undo?.let(::update)
        undo = null
    }

    fun focus(value: DraftFocus) {
        update(draft.copy(focus = value))
    }

    fun finishFocus(): Boolean {
        error = draft.focusProblem()
        if (error != null) {
            dialog = "invalid"
            return false
        }
        update(draft.copy(focus = null))
        return true
    }

    fun save(asNew: Boolean = false): Boolean {
        return try {
            val value = draft.toRoutine()
            value.validate()
            val current = app.repository.library.value.routines.firstOrNull { it.id == id }
            if (!asNew && current != draft.source) {
                dialog = "conflict"
                return false
            }
            app.repository.upsert(if (asNew) value.copy(id = localId()) else value)
            app.repository.discardDraft(id)
            app.message.value =
                if (app.hasActiveSession) "Saved for future sessions. Current session unchanged."
                else "Routine saved"
            true
        } catch (invalid: IllegalArgumentException) {
            error = invalid.message ?: "Check the routine fields"
            draft.firstInvalidFocus()?.let {
                update(draft.copy(focus = it))
                error = invalid.message
            }
            false
        }
    }

    fun discard() {
        app.repository.discardDraft(id)
    }
}
