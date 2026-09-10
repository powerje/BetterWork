package dev.betterwork.phone

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable internal data class PhoneRoute(val screen: String, val id: String = "") : NavKey
