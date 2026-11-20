package org.opencairn.cairn.feature.activities

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.model.Kudos
import org.opencairn.cairn.core.domain.model.StreamChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = FakeActivities().apply { activities.value = mapOf("a1" to activity("a1")) }
    private val insights = FakeInsights()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun pageShowsTheActivityWithItsInsightsAndIgnoresMissingExtras() =
        runTest(dispatcher) {
            insights.insights.value = ActivityInsights(kudos = Kudos(3, false, emptyList()))
            val vm = ActivityDetailViewModel(activities, insights, "a1")
            vm.state.test {
                val st = awaitItemWith { it.activity.value != null && !it.refreshing }
                assertEquals("Ride a1", st.activity.value?.title)
                assertEquals(3, st.insights.kudos?.count)
                // Best efforts answered Unimplemented: an older server, not an error of the page.
                assertNull(st.error)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("a1"), activities.refreshedActivities)
        }

    @Test
    fun failedInsightsShowTheStaleBannerAndCursorStateSurvives() =
        runTest(dispatcher) {
            insights.refreshFailure = AppError.Network()
            val vm = ActivityDetailViewModel(activities, insights, "a1")
            vm.state.test {
                assertEquals("Offline, showing cached data", awaitItemWith { it.error != null }.error)
                vm.select(42)
                vm.toggleChannel(StreamChannel.POWER)
                val st = awaitItemWith { it.hiddenChannels.isNotEmpty() }
                assertEquals(42, st.selectedIndex)
                vm.toggleChannel(StreamChannel.POWER)
                assertEquals(emptySet(), awaitItemWith { it.hiddenChannels.isEmpty() }.hiddenChannels)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun exportHandsTheLinkOverOnceAndReportsFailures() =
        runTest(dispatcher) {
            val vm = ActivityDetailViewModel(activities, insights, "a1")
            vm.state.test {
                awaitItemWith { it.activity.value != null && !it.refreshing }
                vm.export(ExportFormat.GPX)
                val ready = awaitItemWith { it.export != null }
                assertEquals("https://c/dl/a1", ready.export?.url)
                assertEquals("ride.gpx", ready.export?.filename)
                vm.exportOpened()
                assertNull(awaitItemWith { it.export == null }.export)
                insights.exportFailure = AppError.NotFound()
                vm.export(ExportFormat.FIT)
                assertEquals("Not found on the server", awaitItemWith { it.error != null }.error)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(ExportFormat.GPX, ExportFormat.FIT), insights.exports)
        }

    @Test
    fun aLiveChangeOfThisActivityReloadsThePage() =
        runTest(dispatcher) {
            val live = MutableSharedFlow<ActivityChange>(extraBufferCapacity = 4)
            val vm = ActivityDetailViewModel(activities, insights, "a1", live)
            vm.state.test {
                awaitItemWith { it.activity.value != null && !it.refreshing }
                live.emit(ActivityChange(ActivityChange.Kind.UPDATED, "other"))
                live.emit(ActivityChange(ActivityChange.Kind.UPDATED, "a1"))
                awaitItemWith { it.refreshing }
                awaitItemWith { !it.refreshing }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("a1", "a1"), activities.refreshedActivities)
        }
}
