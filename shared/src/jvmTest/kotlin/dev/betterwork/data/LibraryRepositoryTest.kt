package dev.betterwork.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.betterwork.db.BetterDatabase
import dev.betterwork.domain.*
import kotlin.test.*

class LibraryRepositoryTest {
    @Test
    fun legacyPreferencesKeepDefaultsButDiscardSessionOverrides() {
        val db = database()
        db.storeQueries.putValue(
            "preferences",
            """{"sound":true,"completionCue":"GENTLE","limitMs":300000,"cueOverride":"NONE"}""",
        )
        val repo = LibraryRepository(db, ::id, true)
        val defaults = repo.appPreferences.value
        assertTrue(defaults.sound)
        assertEquals(Cue.GENTLE, defaults.completionCue)
        assertEquals(Appearance.SYSTEM, defaults.appearance)
        assertNull(defaults.optionsFor(Presets.tabata).limitMs)
        assertNull(defaults.optionsFor(Presets.tabata).cueOverride)
        repo.appPreferences(defaults.copy(appearance = Appearance.DARK))
        assertEquals(
            Appearance.DARK,
            LibraryRepository(db, ::id, true).appPreferences.value.appearance,
        )
    }

    @Test
    fun invalidDraftSurvivesRestartWithoutChangingTheLibrary() {
        val db = database()
        val repo = LibraryRepository(db, ::id, true)
        val before = repo.export()
        val draft =
            RoutineDraft(
                "draft",
                null,
                "",
                listOf(DraftBlock("block", "invalid", listOf(DraftStep("step", seconds = "")))),
            )
        repo.saveDraft(draft)
        val restarted = LibraryRepository(db, ::id, true)
        assertEquals(draft, restarted.draft("draft"))
        assertEquals(before, restarted.export())
        restarted.discardDraft("draft")
        assertNull(repo.draft("draft"))
    }

    private var nextId = 0

    private fun id() = "new-${nextId++}"

    private fun database(): BetterDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BetterDatabase.Schema.create(driver)
        return BetterDatabase(driver)
    }

    @Test
    fun presetsSeedOnceAndDeletionSurvivesRestart() {
        val db = database()
        val repo = LibraryRepository(db, ::id, true)
        assertEquals(Presets.all, repo.library.value.routines)
        repo.library.value.routines.forEach { repo.delete(it.id) }
        val restarted = LibraryRepository(db, ::id, true)
        assertTrue(restarted.library.value.routines.isEmpty())
        assertEquals(3, restarted.library.value.revision)
    }

    @Test
    fun importMakesFreshCopiesAndRoundTripsVersionedJson() {
        val repo = LibraryRepository(database(), ::id, true)
        val exported = repo.export()
        assertEquals(repo.library.value, LibraryCodec.decode(exported))
        repo.importCopies(exported)
        val routines = repo.library.value.routines
        assertEquals(6, routines.size)
        assertEquals(6, routines.map { it.id }.distinct().size)
        assertEquals(Presets.all.map { it.name }, routines.takeLast(3).map { it.name })
    }

    @Test
    fun invalidImportsAreAtomic() {
        val db = database()
        val repo = LibraryRepository(db, ::id, true)
        val before = repo.export()
        val invalidFiles =
            listOf(
                before.replace("\"formatVersion\":1", "\"formatVersion\":2"),
                before.replace("\"durationMs\":180000", "\"durationMs\":0"),
                before.replace("\"name\":\"Tabata\"", "\"name\":\"\""),
                before.replace("\"formatVersion\":1,", ""),
                before.dropLast(1),
                before.dropLast(1) + ",\"unknown\":true}",
                "x".repeat(MAX_FILE_BYTES + 1),
            )
        for (file in invalidFiles) {
            assertFailsWith<IllegalArgumentException> { repo.importCopies(file) }
            assertEquals(before, repo.export())
            assertEquals(before, LibraryRepository(db, ::id, true).export())
        }
    }

    @Test
    fun duplicateIdsAndCapacityOverflowAreRejectedBeforeWriting() {
        val repo = LibraryRepository(database(), ::id, true)
        val before = repo.export()
        assertFailsWith<IllegalArgumentException> {
            LibraryCodec.encode(
                Library(origin = "phone", routines = listOf(Presets.tabata, Presets.tabata))
            )
        }
        val large =
            Library(
                origin = "phone",
                routines = List(99) { Presets.tabata.copy(id = "import-$it") },
            )
        assertFailsWith<IllegalArgumentException> { repo.importCopies(LibraryCodec.encode(large)) }
        assertEquals(before, repo.export())
    }

    @Test
    fun syncPropagatesDeletionRejectsStaleAndAllowsNewPhoneOrigin() {
        val phone = LibraryRepository(database(), ::id, true)
        val watch = LibraryRepository(database(), ::id, false)
        val initial = phone.export()
        assertTrue(watch.receive(initial))
        phone.delete(Presets.tabata.id)
        assertTrue(watch.receive(phone.export()))
        assertFalse(watch.receive(initial))
        assertFalse(watch.receive(phone.export()))
        assertEquals(phone.library.value, watch.library.value)
        assertFailsWith<IllegalStateException> { watch.delete(Presets.japaneseWalk.id) }
        val replacement = Library(origin = "replacement-phone", routines = emptyList())
        assertTrue(watch.receive(LibraryCodec.encode(replacement)))
        assertTrue(watch.library.value.routines.isEmpty())
    }

    @Test
    fun editsAndSyncDoNotChangeAnActiveSession() {
        val phone = LibraryRepository(database(), ::id, true)
        val watch = LibraryRepository(database(), ::id, false)
        watch.receive(phone.export())
        val session =
            Session.start(
                watch.library.value.routines.first(),
                SessionOptions(),
                MonotonicClock { 0 },
            )
        phone.upsert(Presets.japaneseWalk.copy(name = "Changed"))
        watch.receive(phone.export())
        assertEquals("Japanese Walk", session.state.routine.name)
    }

    @Test
    fun recoveryAndPreferencesPersistWithoutSessionHistory() {
        val db = database()
        val repo = LibraryRepository(db, ::id, true)
        val checkpoint =
            Session.start(Presets.tabata, SessionOptions(sound = true), MonotonicClock { 0 }).state
        repo.checkpoint(checkpoint)
        repo.preferences(SessionOptions(completionCue = Cue.GENTLE))
        val restarted = LibraryRepository(db, ::id, true)
        assertEquals(checkpoint, restarted.checkpoint())
        assertEquals(Cue.GENTLE, restarted.preferences().completionCue)
        restarted.checkpoint(null)
        assertNull(repo.checkpoint())
    }
}
