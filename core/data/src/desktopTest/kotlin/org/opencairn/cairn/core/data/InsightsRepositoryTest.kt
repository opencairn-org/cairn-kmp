package org.opencairn.cairn.core.data

import app.cash.turbine.test
import cairn.v1.ActivityLap
import cairn.v1.ActivitySegmentEffort
import cairn.v1.Comment
import cairn.v1.ContentUrl
import cairn.v1.Decoupling
import cairn.v1.ExportActivityRequest
import cairn.v1.ExportActivityResponse
import cairn.v1.ExportFormat
import cairn.v1.GetActivityZonesResponse
import cairn.v1.ListActivitySegmentEffortsResponse
import cairn.v1.ListCommentsResponse
import cairn.v1.ListKudosResponse
import cairn.v1.ListLapsResponse
import cairn.v1.ListSimilarActivitiesResponse
import cairn.v1.SimilarActivity
import cairn.v1.UserCard
import cairn.v1.ZoneBand
import cairn.v1.ZoneSummary
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.common.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import org.opencairn.cairn.core.domain.model.ExportFormat as DomainFormat

class InsightsRepositoryTest {
    private fun FakeCore.scriptEverything() {
        on("ActivityService/ListLaps") {
            Reply.Ok(ListLapsResponse(laps = listOf(ActivityLap(index = 1, distance_m = 1000.0, start_offset_s = 60))))
        }
        on("ActivityService/GetActivityZones") {
            val hr =
                ZoneSummary(
                    basis = "lthr",
                    reference = 165.0,
                    total_seconds = 600,
                    zones = listOf(ZoneBand("Z1", 0.0, 0.8, 150), ZoneBand("Z2", 0.8, 0.9, 450)),
                )
            Reply.Ok(GetActivityZonesResponse(hr = hr, decoupling = Decoupling(3.2, "power", true)))
        }
        on("ActivityService/ListActivitySegmentEfforts") {
            val effort =
                ActivitySegmentEffort(
                    segment_id = "s1",
                    segment_name = "Hill",
                    segment_distance_m = 1200.0,
                    climb_category = "4",
                    elapsed_s = 300.0,
                    is_personal_record = true,
                    personal_rank = 1,
                )
            Reply.Ok(ListActivitySegmentEffortsResponse(efforts = listOf(effort)))
        }
        on("ActivityService/ListSimilarActivities") {
            val both =
                listOf(
                    SimilarActivity(id = "a0", title = "Before", moving_s = 3000, overlap_pct = 0.75),
                    SimilarActivity(id = "a1", is_current = true, title = "Now", moving_s = 2400),
                )
            Reply.Ok(
                ListSimilarActivitiesResponse(activities = both, best_moving_s = 2400, current_rank = 1),
            )
        }
        on("ActivityService/ListKudos") {
            val ann = UserCard(id = "u2", username = "ann")
            Reply.Ok(ListKudosResponse(count = 2, has_kudos = true, kudosers = listOf(ann)))
        }
        on("ActivityService/ListComments") {
            val nice = Comment(id = "c1", body = "Nice", author = UserCard(id = "u2"))
            Reply.Ok(ListCommentsResponse(comments = listOf(nice)))
        }
    }

    @Test
    fun everyPartIsFetchedAndServedFromTheCache() =
        runTest {
            val core = FakeCore().signIn().apply { scriptEverything() }
            val repo = ActivityInsightsRepositoryImpl(core.scope, core.snapshots)
            repo.refreshInsights("a1").getOrThrow()
            core.online = false
            repo.observeInsights("a1").test {
                val i = awaitItem()
                assertEquals(1.minutes, i.laps.single().startOffset)
                val hr = assertNotNull(i.zones?.heartRate)
                assertEquals(0.75f, hr.share(hr.bands[1]))
                assertTrue(i.zones?.decoupling?.coupled == true)
                assertNull(i.zones?.power)
                assertEquals("4", i.segmentEfforts.single().climbCategory)
                assertEquals(listOf("a0", "a1"), i.similar?.activities?.map { it.id })
                assertEquals(
                    "ann",
                    i.kudos
                        ?.users
                        ?.single()
                        ?.username,
                )
                assertEquals("Nice", i.comments.single().body)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun partsTheServerLacksOrDoesNotShareAreSkipped() =
        runTest {
            val core = FakeCore().signIn().apply { scriptEverything() }
            core.routes.remove("/cairn.v1.ActivityService/GetActivityZones")
            core.on("ActivityService/ListKudos") { Reply.Failure("permission_denied", HttpStatusCode.Forbidden) }
            core.on("ActivityService/ListSimilarActivities") {
                val only = SimilarActivity(id = "a1", is_current = true)
                Reply.Ok(ListSimilarActivitiesResponse(activities = listOf(only)))
            }
            val repo = ActivityInsightsRepositoryImpl(core.scope, core.snapshots)
            repo.refreshInsights("a1").getOrThrow()
            repo.observeInsights("a1").test {
                val i = awaitItem()
                assertEquals(1, i.laps.size)
                assertNull(i.zones)
                assertNull(i.kudos)
                // Only the activity itself rides this route: nothing to compare.
                assertNull(i.similar)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRealFailureIsReported() =
        runTest {
            val core = FakeCore().signIn().apply { scriptEverything() }
            val repo = ActivityInsightsRepositoryImpl(core.scope, core.snapshots)
            core.online = false
            assertIs<AppError.Network>(repo.refreshInsights("a1").exceptionOrNull())
        }

    @Test
    fun exportAsksForTheFormatAndReturnsTheLink() =
        runTest {
            val core = FakeCore().signIn()
            var asked: ExportActivityRequest? = null
            core.on("ActivityService/ExportActivity") { body ->
                asked = ExportActivityRequest.ADAPTER.decode(body)
                Reply.Ok(ExportActivityResponse(file_ = ContentUrl(url = "https://c/dl/1"), filename = "ride.gpx"))
            }
            val repo = ActivityInsightsRepositoryImpl(core.scope, core.snapshots)
            val file = repo.export("a1", DomainFormat.GPX).getOrThrow()
            assertEquals("https://c/dl/1", file.url)
            assertEquals("ride.gpx", file.filename)
            assertEquals(ExportFormat.EXPORT_FORMAT_GPX, asked?.format)
            core.on("ActivityService/ExportActivity") { Reply.Ok(ExportActivityResponse()) }
            assertIs<AppError.Server>(repo.export("a1", DomainFormat.FIT).exceptionOrNull())
        }
}
