package org.opencairn.cairn.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed navigation keys; features reference these, never each other. */
sealed interface Route : NavKey {
    @Serializable data object Overview : Route

    @Serializable data object Activities : Route

    /** The account landing page behind the avatar in the bottom bar. */
    @Serializable data object Account : Route

    @Serializable data object AthleteProfile : Route

    @Serializable data class MetricHistory(
        val key: String,
    ) : Route

    @Serializable data object Gear : Route

    @Serializable data class GearDetail(
        val id: String,
    ) : Route

    /** Null id creates a new item. */
    @Serializable data class GearEdit(
        val id: String?,
    ) : Route

    @Serializable data object Records : Route

    @Serializable data class RecordHistory(
        val activityType: String,
        val metric: String,
        val windowKind: String,
        val windowValue: Int,
    ) : Route

    @Serializable data object Settings : Route

    @Serializable data object About : Route

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
val topLevelRoutes: List<Route> = listOf(Route.Overview, Route.Activities, Route.Account)
