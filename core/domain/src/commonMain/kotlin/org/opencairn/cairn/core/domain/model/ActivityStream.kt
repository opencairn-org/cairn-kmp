package org.opencairn.cairn.core.domain.model

/** Columnar samples of one source; a channel list is empty when the source has no such data. */
data class ActivityStream(
    val sourceId: String,
    val resolutionHz: Double,
    val timeS: List<Double>,
    val latitude: List<Double>,
    val longitude: List<Double>,
    val altitudeM: List<Double>,
    val distanceM: List<Double>,
    val speedMps: List<Double>,
    val heartRateBpm: List<Int>,
    val powerW: List<Int>,
    val cadence: List<Int>,
    val temperatureC: List<Double>,
    val grade: List<Double>,
) {
    val sampleCount: Int get() = timeS.size
    val hasGps: Boolean get() = latitude.size == timeS.size && longitude.size == timeS.size && timeS.isNotEmpty()

    /** Channels with data, in the order the web shows them. */
    val channels: List<StreamChannel>
        get() =
            buildList {
                if (altitudeM.size == timeS.size && timeS.isNotEmpty()) add(StreamChannel.ELEVATION)
                if (heartRateBpm.size == timeS.size && timeS.isNotEmpty()) add(StreamChannel.HEART_RATE)
                if (powerW.size == timeS.size && timeS.isNotEmpty()) add(StreamChannel.POWER)
                if (speedMps.size == timeS.size && timeS.isNotEmpty()) add(StreamChannel.SPEED)
                if (cadence.size == timeS.size && timeS.isNotEmpty()) add(StreamChannel.CADENCE)
                if (temperatureC.size == timeS.size && timeS.isNotEmpty()) add(StreamChannel.TEMPERATURE)
            }

    fun values(channel: StreamChannel): List<Double> =
        when (channel) {
            StreamChannel.ELEVATION -> altitudeM
            StreamChannel.HEART_RATE -> heartRateBpm.map { it.toDouble() }
            StreamChannel.POWER -> powerW.map { it.toDouble() }
            StreamChannel.SPEED -> speedMps
            StreamChannel.CADENCE -> cadence.map { it.toDouble() }
            StreamChannel.TEMPERATURE -> temperatureC
        }

    /** Index of the sample closest to `t` seconds. */
    fun indexAt(t: Double): Int {
        if (timeS.isEmpty()) return 0
        var lo = 0
        var hi = timeS.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (timeS[mid] < t) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

enum class StreamChannel { ELEVATION, HEART_RATE, POWER, SPEED, CADENCE, TEMPERATURE }
