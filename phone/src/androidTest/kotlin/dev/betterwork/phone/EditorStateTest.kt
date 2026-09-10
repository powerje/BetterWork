package dev.betterwork.phone

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.betterwork.data.*
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorStateTest {
    private val app = ApplicationProvider.getApplicationContext<BetterApp>()

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun presetsAndPreciseDurationsRoundTripWithoutRounding() = onMain {
        (Presets.all +
                Routine(
                    "precision",
                    "Precision",
                    listOf(
                        Block(
                            listOf(Step("Fraction", 1250), Step("Seven days", MAX_DURATION_MS)),
                            2,
                        )
                    ),
                    limitMs = 1001,
                ))
            .forEach { original ->
                val draft = original.toDraft()
                assertEquals(original, draft.toRoutine())
                val withFields =
                    draft.copy(
                        blocks =
                            draft.blocks.map { block ->
                                block.copy(
                                    steps =
                                        block.steps.map {
                                            it.copy(duration = durationFields(it.durationMs()))
                                        }
                                )
                            },
                        limitDuration = original.limitMs?.let(::durationFields),
                    )
                assertEquals(original, withFields.toRoutine())
            }
        assertTrue(runCatching { DraftDuration(minutes = "0", seconds = "0").toMillis() }.isFailure)
        assertTrue(runCatching { DraftDuration(hours = "168", seconds = "1").toMillis() }.isFailure)
        assertTrue(
            runCatching { DraftDuration(minutes = "999999999999999999999").toMillis() }.isFailure
        )
    }

    @Test
    fun invalidFocusedInputSurvivesReloadAndCanBeDiscarded() = onMain {
        val id = localId()
        try {
            val editor = EditorState(app, id)
            val block = editor.draft.blocks.first()
            val step = block.steps.first()
            editor.focus(DraftFocus("step", block.id, step.id, block))
            editor.update(
                editor.draft.replaceBlock(
                    block.copy(
                        steps =
                            listOf(
                                step.copy(duration = DraftDuration(minutes = "", seconds = "oops"))
                            )
                    )
                )
            )
            assertFalse(editor.finishFocus())
            val restored = EditorState(app, id)
            assertEquals("oops", restored.draft.blocks.first().steps.first().duration?.seconds)
            assertNotNull(restored.draft.focus)
            restored.update(restored.draft.discardFocus())
            assertEquals(block, restored.draft.blocks.first())
            assertNull(restored.draft.focus)
            assertNull(app.repository.library.value.routines.firstOrNull { it.id == id })
        } finally {
            app.repository.discardDraft(id)
        }
    }

    @Test
    fun externalEditsRequireSaveAsNewAndDoNotMutateTheSessionSnapshot() = onMain {
        val original = Presets.tabata.copy(id = localId(), name = "Editor conflict")
        val previous = app.repository.library.value
        try {
            app.repository.upsert(original)
            val engine = Session.start(original, SessionOptions(), MonotonicClock { 0L })
            val editor = EditorState(app, original.id)
            editor.update(editor.draft.copy(name = "My draft"))
            app.repository.upsert(original.copy(name = "External change"))
            assertFalse(editor.save())
            assertEquals("conflict", editor.dialog)
            assertTrue(editor.save(asNew = true))
            assertEquals(
                "External change",
                app.repository.library.value.routines.first { it.id == original.id }.name,
            )
            assertTrue(
                app.repository.library.value.routines.any {
                    it.id != original.id && it.name == "My draft"
                }
            )
            assertEquals(original, engine.state.routine)
        } finally {
            app.repository.library.value.routines
                .filter { it.id !in previous.routines.map { it.id } }
                .forEach { app.repository.delete(it.id) }
            app.repository.discardDraft(original.id)
        }
    }
}
