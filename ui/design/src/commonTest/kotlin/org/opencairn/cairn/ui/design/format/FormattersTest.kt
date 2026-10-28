package org.opencairn.cairn.ui.design.format

import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import org.opencairn.cairn.core.domain.model.Units
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class FormattersTest {
    private val metric = Formatters(Units.METRIC)
    private val imperial = Formatters(Units.IMPERIAL)

    @Test
    fun distance() {
        assertEquals("12.35 km", metric.distance(Meters(12345.0)))
        assertEquals("7.67 mi", imperial.distance(Meters(12345.0)))
    }

    @Test
    fun pace() {
        assertEquals("5:00/km", metric.pace(MetersPerSecond(1000.0 / 300)))
        assertEquals("--", metric.pace(MetersPerSecond(0.0)))
    }

    @Test
    fun duration() {
        assertEquals("1h 05m", metric.duration(3900.seconds))
        assertEquals("5m 07s", metric.duration(307.seconds))
    }
}
