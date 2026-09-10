package dev.betterwork.phone

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class WidgetBinding(val routineId: String?, val activationToken: String)

/** Versioned, platform-local widget identities; routine snapshots are never stored here. */
internal class WidgetBindings(context: Context) {
    private val preferences =
        context.getSharedPreferences("routine-widgets-v1", Context.MODE_PRIVATE)

    fun read(id: Int) =
        WidgetBinding(
            preferences.getString("routine.$id", null),
            preferences.getString("token.$id", "") ?: "",
        )

    fun bind(id: Int, routineId: String) {
        require(id > 0 && routineId.isNotBlank())
        check(
            preferences
                .edit()
                .putString("routine.$id", routineId)
                .putString("token.$id", UUID.randomUUID().toString())
                .commit()
        ) {
            "Widget could not be saved. Try again."
        }
    }

    fun refresh(id: Int) {
        preferences.edit().putString("token.$id", UUID.randomUUID().toString()).apply()
    }

    fun consume(id: Int, token: String): Boolean {
        if (token.isBlank() || preferences.getString("consumed.$id", null) == token) return false
        return preferences.edit().putString("consumed.$id", token).commit()
    }

    fun retry(id: Int) {
        preferences.edit().remove("consumed.$id").apply()
    }

    fun delete(ids: IntArray) {
        preferences
            .edit()
            .apply {
                ids.forEach {
                    remove("routine.$it")
                    remove("token.$it")
                    remove("consumed.$it")
                }
            }
            .apply()
    }

    fun restore(oldIds: IntArray, newIds: IntArray) {
        val saved = oldIds.map(::read)
        delete(oldIds)
        oldIds.indices.forEach { index ->
            val target = newIds.getOrNull(index) ?: return@forEach
            saved[index].routineId?.let { bind(target, it) }
        }
    }

    fun observe(id: Int) =
        callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == "routine.$id" || key == "token.$id") trySend(read(id))
                    }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                trySend(read(id))
                awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            .distinctUntilChanged()
}
