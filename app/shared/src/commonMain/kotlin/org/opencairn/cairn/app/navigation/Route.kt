package org.opencairn.cairn.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed navigation keys; features reference these, never each other. */
sealed interface Route : NavKey {
    @Serializable data object Login : Route

    @Serializable data object Home : Route
}
