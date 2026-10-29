package org.opencairn.cairn.core.data

import cairn.v1.ActivitySummary
import cairn.v1.ContentUrl
import cairn.v1.Discipline
import cairn.v1.GearSetBy
import cairn.v1.Privacy
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import cairn.v1.ActivityType as ProtoType

class MappersTest {
    @Test
    fun activityMapsFieldsAndUnits() {
        val proto =
            cairn.v1.Activity(
                id = "a1",
                type = ProtoType.ACTIVITY_TYPE_RIDE,
                discipline = Discipline.DISCIPLINE_RIDE_GRAVEL,
                title = "",
                start_time = Instant.fromEpochSeconds(1_700_000_000).toWire(),
                elapsed_duration = com.squareup.wire.durationOfSeconds(3600, 0),
                summary = ActivitySummary(distance_m = 42_195.0, avg_speed_mps = 5.0, avg_heart_rate_bpm = 150),
                gear_set_by = GearSetBy.GEAR_SET_BY_USER,
                gear_name = "Gravel bike",
                privacy = Privacy.PRIVACY_PUBLIC,
                map_image = ContentUrl(url = "https://blob/map.png"),
                map_image_etag = "etag1",
                is_race = true,
            )
        val a = proto.toDomain()
        assertEquals(ActivityType.RIDE, a.type)
        assertEquals("ride_gravel", a.discipline)
        assertEquals("Ride activity", a.displayTitle)
        assertEquals(3600.seconds, a.elapsed)
        assertEquals(42.195, a.summary.distance!!.kilometers, 1e-9)
        assertEquals(150, a.summary.avgHeartRate)
        assertEquals("Gravel bike", a.gearName)
        assertEquals("https://blob/map.png", a.mapImageUrl)
        assertTrue(a.isRace)
        assertNull(a.endTime)
    }

    @Test
    fun typeMappingIsBijective() {
        ProtoType.entries.forEach { assertEquals(it, it.toDomain().toProto()) }
    }

    @Test
    fun filterAndSortMapToProto() {
        val from = Instant.fromEpochSeconds(1_000)
        val f =
            ActivityFilter(
                types = setOf(ActivityType.RUN),
                from = from,
                race = ActivityFilter.Flag.ONLY,
                distanceMinM = 5_000.0,
            ).toProto()
        assertEquals(listOf(ProtoType.ACTIVITY_TYPE_RUN), f.types)
        assertEquals(1_000L, f.time_range?.from?.getEpochSecond())
        assertNull(f.time_range?.to)
        assertEquals(cairn.v1.ActivityFilter.Flag.FLAG_ONLY, f.race)
        assertEquals(5_000.0, f.distance_min_m)
        assertEquals(cairn.v1.ActivitySort.ACTIVITY_SORT_SPEED_DESC, ActivitySort.SPEED_DESC.toProto())
        assertNull(ActivityFilter().toProto().time_range)
    }

    @Test
    fun cacheKeyIsStableAndDistinct() {
        assertEquals(cacheKey("a", 1), cacheKey("a", 1))
        assertTrue(cacheKey("a", 1) != cacheKey("a", 2))
    }
}
