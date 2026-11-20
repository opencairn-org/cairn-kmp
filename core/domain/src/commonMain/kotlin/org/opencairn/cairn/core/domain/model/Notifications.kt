package org.opencairn.cairn.core.domain.model

import kotlin.time.Instant

enum class NotificationSeverity { INFO, WARN, ERROR }

/** Something the server wants the user to know: a new record, an expired provider login, … */
data class Notification(
    val id: String,
    /** The event type in lower snake case, e.g. `achievement_new_personal_record`. */
    val type: String,
    val severity: NotificationSeverity,
    val params: Map<String, String>,
    val activityId: String?,
    /** How many events of the same kind this entry stands for. */
    val count: Int,
    val read: Boolean,
    val createdAt: Instant?,
) {
    /** `achievement_new_personal_record` → "New personal record": the type without its family prefix. */
    val title: String
        get() =
            type
                .substringAfter('_', type)
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
}

data class UnreadCount(
    val total: Int = 0,
    val warnings: Int = 0,
    val errors: Int = 0,
)

/** A change the server pushed while the app was listening. */
data class ActivityChange(
    val kind: Kind,
    val activityId: String?,
) {
    enum class Kind { CREATED, UPDATED, DELETED, IMPORT_FINISHED, RECORD }
}

/** A published version of the app that is newer than the running one. */
data class AppRelease(
    val version: String,
    val url: String,
)
