package dev.betterwork.wear

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable internal data class WatchRoute(val screen: String, val id: String = "") : NavKey
