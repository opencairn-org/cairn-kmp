package org.opencairn.cairn.core.domain.model

import kotlin.jvm.JvmInline
import kotlin.time.Duration

/** All server values are SI; these value classes keep units explicit until formatting. */
@JvmInline
value class Meters(
    val value: Double,
) {
    val kilometers: Double get() = value / 1000.0
    val miles: Double get() = value / 1609.344
    val feet: Double get() = value * 3.280839895
}

@JvmInline
value class MetersPerSecond(
    val value: Double,
) {
    val kmh: Double get() = value * 3.6
    val mph: Double get() = value * 2.2369362921

    /** Seconds per kilometer; null when not moving. */
    val secondsPerKm: Double? get() = if (value > 0) 1000.0 / value else null
    val secondsPerMile: Double? get() = if (value > 0) 1609.344 / value else null
}

typealias Elapsed = Duration
