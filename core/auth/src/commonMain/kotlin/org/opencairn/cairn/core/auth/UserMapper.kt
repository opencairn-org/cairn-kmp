package org.opencairn.cairn.core.auth

import cairn.v1.UnitSystem
import org.opencairn.cairn.core.database.Account
import org.opencairn.cairn.core.domain.model.DateFormat
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.TimeFormat
import org.opencairn.cairn.core.domain.model.Units
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.core.domain.model.UserRole
import kotlin.time.Instant
import cairn.v1.DateFormat as ProtoDateFormat
import cairn.v1.TimeFormat as ProtoTimeFormat
import cairn.v1.UserRole as ProtoRole

fun cairn.v1.User.toDomain(permissions: List<String>): User =
    User(
        id = id,
        username = username,
        email = email,
        displayName = display_name.ifBlank { username },
        avatarUrl = avatar_url,
        role = if (role == ProtoRole.USER_ROLE_ADMIN) UserRole.ADMIN else UserRole.USER,
        preferences =
            Preferences(
                units = if (units == UnitSystem.UNIT_SYSTEM_IMPERIAL) Units.IMPERIAL else Units.METRIC,
                dateFormat =
                    when (date_format) {
                        ProtoDateFormat.DATE_FORMAT_ISO -> DateFormat.ISO
                        ProtoDateFormat.DATE_FORMAT_US -> DateFormat.US
                        ProtoDateFormat.DATE_FORMAT_EU -> DateFormat.EU
                        else -> DateFormat.LOCALE
                    },
                timeFormat =
                    when (time_format) {
                        ProtoTimeFormat.TIME_FORMAT_24H -> TimeFormat.H24
                        ProtoTimeFormat.TIME_FORMAT_12H -> TimeFormat.H12
                        else -> TimeFormat.LOCALE
                    },
                locale = locale,
                timezone = timezone,
            ),
        permissions = permissions,
        createdAt = created_at?.let { Instant.fromEpochSeconds(it.getEpochSecond(), it.getNano()) },
    )

fun Account.toDomain(): User =
    User(
        id = user_id,
        username = username,
        email = email,
        displayName = display_name,
        avatarUrl = avatar_url,
        role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.USER),
        preferences =
            Preferences(
                units = runCatching { Units.valueOf(units) }.getOrDefault(Units.METRIC),
                dateFormat = runCatching { DateFormat.valueOf(date_format) }.getOrDefault(DateFormat.LOCALE),
                timeFormat = runCatching { TimeFormat.valueOf(time_format) }.getOrDefault(TimeFormat.LOCALE),
                locale = locale,
                timezone = timezone,
            ),
        permissions = permissions.split(',').filter { it.isNotBlank() },
        createdAt = created_at?.let { Instant.fromEpochSeconds(it) },
    )

fun Units.toProto(): UnitSystem =
    if (this == Units.IMPERIAL) UnitSystem.UNIT_SYSTEM_IMPERIAL else UnitSystem.UNIT_SYSTEM_METRIC

fun DateFormat.toProto(): ProtoDateFormat =
    when (this) {
        DateFormat.ISO -> ProtoDateFormat.DATE_FORMAT_ISO
        DateFormat.US -> ProtoDateFormat.DATE_FORMAT_US
        DateFormat.EU -> ProtoDateFormat.DATE_FORMAT_EU
        DateFormat.LOCALE -> ProtoDateFormat.DATE_FORMAT_UNSPECIFIED
    }

fun TimeFormat.toProto(): ProtoTimeFormat =
    when (this) {
        TimeFormat.H24 -> ProtoTimeFormat.TIME_FORMAT_24H
        TimeFormat.H12 -> ProtoTimeFormat.TIME_FORMAT_12H
        TimeFormat.LOCALE -> ProtoTimeFormat.TIME_FORMAT_UNSPECIFIED
    }
