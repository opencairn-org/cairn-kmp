package org.opencairn.cairn.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed navigation keys; features reference these, never each other. */
sealed interface Route : NavKey {
    @Serializable data object Overview : Route

    @Serializable data object Activities : Route

    @Serializable data object Settings : Route

    /** Approve another device's sign-in code (reached from Settings). */
    @Serializable data object ApproveDevice : Route

    @Serializable data class ActivityDetail(
        val id: String,
    ) : Route

    @Serializable data class ActivityMap(
        val id: String,
    ) : Route

    @Serializable data class ActivityStreams(
        val id: String,
    ) : Route
}

/** The bottom-bar destinations, in order. */
val topLevelRoutes: List<Route> = listOf(Route.Overview, Route.Activities, Route.Settings)
