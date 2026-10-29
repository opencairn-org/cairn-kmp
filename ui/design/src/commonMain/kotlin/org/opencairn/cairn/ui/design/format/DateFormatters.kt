package org.opencairn.cairn.ui.design.format

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.opencairn.cairn.core.domain.model.DateFormat
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.TimeFormat
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Dates and times according to the user's preferences (web/src/lib/format.ts). */
class DateFormatters(
    private val prefs: Preferences,
    private val clock: Clock = Clock.System,
) {
    private val dateStyle: DateFormat =
        when (prefs.dateFormat) {
            DateFormat.LOCALE -> {
                when (prefs.locale.substringBefore('-').lowercase()) {
                    "en" -> if (prefs.locale.endsWith("US", ignoreCase = true)) DateFormat.US else DateFormat.ISO
                    "de", "fr", "it", "es", "nl", "pl", "cs" -> DateFormat.EU
                    else -> DateFormat.ISO
                }
            }

            else -> {
                prefs.dateFormat
            }
        }
    private val h24: Boolean =
        prefs.timeFormat != TimeFormat.H12 &&
            !(prefs.timeFormat == TimeFormat.LOCALE && prefs.locale.endsWith("US", ignoreCase = true))

    fun date(
        instant: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val d = instant.toLocalDateTime(zone).date
        val dd = d.day.toString().padStart(2, '0')
        val mm =
            d.month.ordinal
                .plus(1)
                .toString()
                .padStart(2, '0')
        return when (dateStyle) {
            DateFormat.US -> "$mm/$dd/${d.year}"
            DateFormat.EU -> "$dd.$mm.${d.year}"
            else -> "${d.year}-$mm-$dd"
        }
    }

    fun time(
        instant: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val t = instant.toLocalDateTime(zone).time
        val mm = t.minute.toString().padStart(2, '0')
        if (h24) return "${t.hour.toString().padStart(2, '0')}:$mm"
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$h:$mm ${if (t.hour < 12) "AM" else "PM"}"
    }

    fun dateTime(
        instant: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = "${date(instant, zone)} ${time(instant, zone)}"

    /** "today", "yesterday", "3 days ago", else the date. */
    fun relative(
        instant: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val now = clock.now()
        val days = (now - instant).inWholeDays
        return when {
            instant > now -> date(instant, zone)
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            days < 30 -> "${days / 7} weeks ago"
            else -> date(instant, zone)
        }
    }

    fun localDateTime(
        instant: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): LocalDateTime = instant.toLocalDateTime(zone)

    companion object {
        /** Monday-start week containing `now`, as UTC instants, like the web's overview cards. */
        fun weekRange(now: Instant): Pair<Instant, Instant> {
            val d = now.toLocalDateTime(TimeZone.UTC).date
            val start = d.toEpochDays() - (d.dayOfWeek.ordinal)
            val from = Instant.fromEpochSeconds(start * 86_400)
            return from to from + 7.days
        }
    }
}
