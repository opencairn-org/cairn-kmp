package org.opencairn.cairn.ui.map

import org.opencairn.cairn.core.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoundsTest {
    @Test
    fun boundsEncloseEveryPoint() {
        val points = listOf(GeoPoint(48.0, 7.8), GeoPoint(47.6, 8.4), GeoPoint(48.3, 7.5))
        assertEquals(GeoBounds(south = 47.6, west = 7.5, north = 48.3, east = 8.4), boundsOf(points))
        assertNull(boundsOf(emptyList()))
    }
}
