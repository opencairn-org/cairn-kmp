package org.opencairn.cairn.core.domain.model

import kotlin.time.Instant

enum class Units { METRIC, IMPERIAL }

enum class DateFormat { LOCALE, ISO, US, EU }

enum class TimeFormat { LOCALE, H24, H12 }

data class Preferences(
    val units: Units = Units.METRIC,
    val dateFormat: DateFormat = DateFormat.LOCALE,
    val timeFormat: TimeFormat = TimeFormat.LOCALE,
    val locale: String = "",
    val timezone: String = "",
)

enum class UserRole { USER, ADMIN }

data class User(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: UserRole,
    val preferences: Preferences,
    val permissions: List<String>,
    val createdAt: Instant?,
)
