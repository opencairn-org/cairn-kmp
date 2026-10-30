package org.opencairn.cairn.core.domain.model

/** One physiological parameter the server tracks over time (FTP, weight, ...). */
data class AthleteMetricSpec(
    val key: String,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
)

/** A dated measurement; the value applies from [effectiveDate] (ISO `YYYY-MM-DD`) on. */
data class AthleteMetricEntry(
    val id: String,
    val key: String,
    val effectiveDate: String,
    val value: Double,
)

data class AthleteProfile(
    val specs: List<AthleteMetricSpec>,
    val entries: List<AthleteMetricEntry>,
) {
    /** Newest entry first. */
    fun history(key: String): List<AthleteMetricEntry> =
        entries.filter { it.key == key }.sortedByDescending { it.effectiveDate }

    fun latest(key: String): AthleteMetricEntry? = history(key).firstOrNull()

    fun spec(key: String): AthleteMetricSpec? = specs.firstOrNull { it.key == key }

    companion object {
        /** The parameters the account overview surfaces, in display order (web: FTP first). */
        val HIGHLIGHTED = listOf("ftp_watts", "threshold_hr", "max_hr", "resting_hr", "weight_kg")
    }
}
