package org.opencairn.cairn.ui.design.format

import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import org.opencairn.cairn.core.domain.model.Units
import kotlin.math.roundToInt
import kotlin.time.Duration

/** Display conversion of SI values according to the user's units; mirrors web/src/lib/format.ts. */
class Formatters(
    private val units: Units,
) {
    fun distance(m: Meters): String =
        when (units) {
            Units.METRIC -> "${fixed(m.kilometers, 2)} km"
            Units.IMPERIAL -> "${fixed(m.miles, 2)} mi"
        }

    fun elevation(m: Meters): String =
        when (units) {
            Units.METRIC -> "${m.value.roundToInt()} m"
            Units.IMPERIAL -> "${m.feet.roundToInt()} ft"
        }

    fun speed(v: MetersPerSecond): String =
        when (units) {
            Units.METRIC -> "${fixed(v.kmh, 1)} km/h"
            Units.IMPERIAL -> "${fixed(v.mph, 1)} mph"
        }

    /** min:ss per km or mile; "--" when not moving. */
    fun pace(v: MetersPerSecond): String {
        val s = (if (units == Units.METRIC) v.secondsPerKm else v.secondsPerMile) ?: return "--"
        val total = s.roundToInt()
        val suffix = if (units == Units.METRIC) "/km" else "/mi"
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}$suffix"
    }

    fun duration(d: Duration): String {
        val total = d.inWholeSeconds
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m ${s.toString().padStart(2, '0')}s"
    }

    fun temperature(celsius: Double): String =
        when (units) {
            Units.METRIC -> "${celsius.roundToInt()} °C"
            Units.IMPERIAL -> "${(celsius * 9 / 5 + 32).roundToInt()} °F"
        }

    private fun fixed(
        v: Double,
        decimals: Int,
    ): String {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        val scaled = (v * factor).roundToInt()
        val whole = scaled / factor.toInt()
        val frac = (scaled % factor.toInt()).toString().padStart(decimals, '0')
        return if (decimals == 0) whole.toString() else "$whole.$frac"
    }
}
