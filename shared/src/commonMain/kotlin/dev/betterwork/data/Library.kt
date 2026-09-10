package dev.betterwork.data

import dev.betterwork.db.BetterDatabase
import dev.betterwork.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val FORMAT_VERSION = 1
const val MAX_FILE_BYTES = 1_000_000

@Serializable
data class Library(
    val formatVersion: Int = FORMAT_VERSION,
    val origin: String,
    val revision: Long = 0,
    val routines: List<Routine>,
) {
    fun validate() {
        require(formatVersion == FORMAT_VERSION) {
            "Unsupported library format version: $formatVersion"
        }
        require(origin.isNotBlank() && origin.length <= 100 && revision >= 0) {
            "Invalid library identity"
        }
        require(routines.size <= 100) { "A library supports at most 100 routines" }
        require(routines.map { it.id }.distinct().size == routines.size) { "Duplicate routine IDs" }
        routines.forEach { it.validate() }
    }
}

object LibraryCodec {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(library: Library): String {
        library.validate()
        return json.encodeToString(library).also {
            require(it.encodeToByteArray().size <= MAX_FILE_BYTES) { "Library exceeds 1 MB" }
        }
    }

    fun decode(text: String): Library {
        require(text.encodeToByteArray().size <= MAX_FILE_BYTES) { "File exceeds 1 MB" }
        val document = json.parseToJsonElement(text)
        require(document.jsonObject["formatVersion"]?.jsonPrimitive?.intOrNull == FORMAT_VERSION) {
            "Missing or unsupported library format version"
        }
        return json.decodeFromJsonElement(Library.serializer(), document).also { it.validate() }
    }
}

class LibraryRepository(
    private val database: BetterDatabase,
    private val newId: () -> String,
    private val editable: Boolean,
) {
    private val queries = database.storeQueries
    private val initial =
        queries.selectValue("library").executeAsOneOrNull()?.let(LibraryCodec::decode)
            ?: Library(origin = newId(), routines = Presets.all).also { save(it) }
    private val mutableLibrary = MutableStateFlow(initial)
    val library: StateFlow<Library> = mutableLibrary
    private val mutablePreferences = MutableStateFlow(loadAppPreferences())
    val appPreferences: StateFlow<AppPreferences> = mutablePreferences

    private fun loadAppPreferences(): AppPreferences {
        val saved = queries.selectValue("app-preferences-v1").executeAsOneOrNull()
        if (saved != null) return LibraryCodec.json.decodeFromString<AppPreferences>(saved)
        val legacy =
            queries.selectValue("preferences").executeAsOneOrNull()?.let {
                LibraryCodec.json.decodeFromString<SessionOptions>(it)
            } ?: SessionOptions()
        return AppPreferences(sound = legacy.sound, completionCue = legacy.completionCue).also {
            queries.putValue("app-preferences-v1", LibraryCodec.json.encodeToString(it))
        }
    }

    fun appPreferences(value: AppPreferences) {
        require(value.version == 1)
        queries.putValue("app-preferences-v1", LibraryCodec.json.encodeToString(value))
        mutablePreferences.value = value
    }

    fun draft(id: String): RoutineDraft? =
        queries.selectValue("draft:$id").executeAsOneOrNull()?.let {
            LibraryCodec.json.decodeFromString<RoutineDraft>(it)
        }

    fun saveDraft(value: RoutineDraft) {
        require(value.version == 1)
        queries.putValue("draft:${value.id}", LibraryCodec.json.encodeToString(value))
    }

    fun discardDraft(id: String) {
        queries.removeValue("draft:$id")
    }

    fun upsert(routine: Routine) {
        check(editable) { "Edit the library on Android" }
        routine.validate()
        val old = library.value
        val routines = old.routines.toMutableList()
        val index = routines.indexOfFirst { it.id == routine.id }
        if (index < 0) routines += routine.frozen() else routines[index] = routine.frozen()
        commit(old.copy(revision = old.revision + 1, routines = routines))
    }

    fun delete(id: String) {
        check(editable)
        commit(
            library.value.copy(
                revision = library.value.revision + 1,
                routines = library.value.routines.filterNot { it.id == id },
            )
        )
    }

    fun importCopies(text: String) {
        check(editable)
        val imported = LibraryCodec.decode(text)
        val copies = imported.routines.map { it.copy(id = newId()) }
        commit(
            library.value.copy(
                revision = library.value.revision + 1,
                routines = library.value.routines + copies,
            )
        )
    }

    fun receive(text: String): Boolean {
        check(!editable)
        val incoming = LibraryCodec.decode(text)
        val old = library.value
        if (incoming.origin == old.origin && incoming.revision <= old.revision) return false
        commit(incoming)
        return true
    }

    fun export(): String = LibraryCodec.encode(library.value)

    fun checkpoint(): Checkpoint? =
        queries.selectValue("session").executeAsOneOrNull()?.let {
            LibraryCodec.json.decodeFromString<Checkpoint>(it)
        }

    fun checkpoint(value: Checkpoint?) {
        if (value == null) queries.removeValue("session")
        else queries.putValue("session", LibraryCodec.json.encodeToString(value))
    }

    fun preferences(): SessionOptions =
        appPreferences.value.let {
            SessionOptions(sound = it.sound, completionCue = it.completionCue)
        }

    fun preferences(value: SessionOptions) {
        value.validate()
        appPreferences(
            appPreferences.value.copy(sound = value.sound, completionCue = value.completionCue)
        )
    }

    private fun save(value: Library) {
        val encoded = LibraryCodec.encode(value)
        database.transaction { queries.putValue("library", encoded) }
    }

    private fun commit(value: Library) {
        save(value)
        mutableLibrary.value = value
    }
}
