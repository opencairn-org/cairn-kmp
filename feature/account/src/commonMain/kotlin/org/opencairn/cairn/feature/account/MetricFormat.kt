package org.opencairn.cairn.feature.account

import org.opencairn.cairn.core.domain.model.AthleteMetricSpec
import kotlin.math.roundToInt

/** Threshold pace is stored as seconds per kilometre and shown as m:ss /km, like the web. */
fun AthleteMetricSpec.format(value: Double): String =
    when {
        key == "threshold_pace" -> {
            val total = value.roundToInt()
            "${total / 60}:${(total % 60).toString().padStart(2, '0')} /km"
        }

        key == "weight_kg" -> {
            "${((value * 10).roundToInt() / 10.0)} $unit"
        }

        else -> {
            "${value.roundToInt()} $unit"
        }
    }

/** Bounds for the add form, without decimals. */
fun AthleteMetricSpec.rangeLabel(): Pair<String, String> = min.roundToInt().toString() to max.roundToInt().toString()
