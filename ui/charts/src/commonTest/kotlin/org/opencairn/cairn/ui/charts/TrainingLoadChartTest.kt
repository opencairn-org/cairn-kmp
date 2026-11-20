package org.opencairn.cairn.ui.charts

import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingLoadChartTest {
    @Test
    fun crosshairSnapsToTheNearestDayAndStaysInside() {
        assertEquals(0, indexAt(0f, 300, 31))
        assertEquals(15, indexAt(150f, 300, 31))
        assertEquals(30, indexAt(300f, 300, 31))
        // A drag that leaves the plot keeps the edge day.
        assertEquals(30, indexAt(900f, 300, 31))
        assertEquals(0, indexAt(-40f, 300, 31))
        assertNull(indexAt(10f, 300, 0))
        assertNull(indexAt(10f, 0, 31))
    }

    @Test
    fun everySeriesReadsItsOwnValue() {
        val day = TrainingLoadDay("2026-11-20", fitness = 62.0, fatigue = 71.0, form = -9.0, stress = 80.0)
        assertEquals(listOf(62.0, 71.0, -9.0), LoadSeries.entries.map { it.value(day) })
    }
}
