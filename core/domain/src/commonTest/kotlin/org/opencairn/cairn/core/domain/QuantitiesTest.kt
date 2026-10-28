package org.opencairn.cairn.core.domain

import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuantitiesTest {
    @Test
    fun metersConvert() {
        assertEquals(1.0, Meters(1000.0).kilometers)
        assertEquals(1.0, Meters(1609.344).miles, 1e-9)
    }

    @Test
    fun paceIsNullWhenNotMoving() {
        assertNull(MetersPerSecond(0.0).secondsPerKm)
        assertEquals(300.0, MetersPerSecond(1000.0 / 300.0).secondsPerKm!!, 1e-9)
    }
}
