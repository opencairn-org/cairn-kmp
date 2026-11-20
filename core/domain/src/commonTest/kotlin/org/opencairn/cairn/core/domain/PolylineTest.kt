package org.opencairn.cairn.core.domain

import org.opencairn.cairn.core.domain.model.GeoPoint
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.NotificationSeverity
import org.opencairn.cairn.core.domain.model.decodePolyline
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineTest {
    private fun assertClose(
        expected: GeoPoint,
        actual: GeoPoint,
    ) {
        assertTrue(abs(expected.latitude - actual.latitude) < 1e-9, "$expected vs $actual")
        assertTrue(abs(expected.longitude - actual.longitude) < 1e-9, "$expected vs $actual")
    }

    @Test
    fun decodesTheReferenceExampleAtPrecisionFive() {
        // The example from the polyline algorithm's documentation.
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertClose(GeoPoint(38.5, -120.2), points[0])
        assertClose(GeoPoint(40.7, -120.95), points[1])
        assertClose(GeoPoint(43.252, -126.453), points[2])
    }

    @Test
    fun precisionSixScalesTheSameDeltasDown() {
        val points = decodePolyline("_p~iF~ps|U", precision = 6)
        assertClose(GeoPoint(3.85, -12.02), points.single())
    }

    @Test
    fun truncatedInputKeepsTheCompletePoints() {
        assertEquals(1, decodePolyline("_p~iF~ps|U_ulL").size)
        assertEquals(emptyList(), decodePolyline(""))
    }

    @Test
    fun notificationTitleDropsTheFamilyPrefix() {
        val n =
            Notification(
                "1",
                "achievement_new_personal_record",
                NotificationSeverity.INFO,
                emptyMap(),
                null,
                1,
                false,
                null,
            )
        assertEquals("New personal record", n.title)
        assertEquals("Plain", n.copy(type = "plain").title)
    }
}
