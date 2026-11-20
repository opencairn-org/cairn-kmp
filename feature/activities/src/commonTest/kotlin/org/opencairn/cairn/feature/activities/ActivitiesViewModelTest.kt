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
import org.opencairn.cairn.core.domain.model.ActivityFacets
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.ActivityType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivitiesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo =
        FakeActivities().apply {
            page.value =
                ActivitySearchPage(listOf(activity("a1")), ActivityFacets(), 60, 60, hasMore = true, fetchedAt = null)
        }

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun filterAndSortSearchAgainAndClearResets() =
        runTest(dispatcher) {
            val vm = ActivitiesViewModel(repo)
            vm.state.test {
                awaitItemWith { it.page != null && !it.refreshing }
                val runs = ActivityFilter(types = setOf(ActivityType.RUN))
                vm.setFilter(runs)
                awaitItemWith { it.filter == runs && !it.refreshing }
                vm.setSort(ActivitySort.DISTANCE_DESC)
                awaitItemWith { it.sort == ActivitySort.DISTANCE_DESC && !it.refreshing }
                vm.toggleFilters()
                assertTrue(awaitItemWith { it.showFilters }.showFilters)
                vm.clearFilter()
                val cleared = awaitItemWith { it.filter.isEmpty && !it.refreshing }
                assertEquals(ActivitySort.DATE_DESC, cleared.sort)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(
                    ActivityFilter() to ActivitySort.DATE_DESC,
                    ActivityFilter(types = setOf(ActivityType.RUN)) to ActivitySort.DATE_DESC,
                    ActivityFilter(types = setOf(ActivityType.RUN)) to ActivitySort.DISTANCE_DESC,
                    ActivityFilter() to ActivitySort.DATE_DESC,
                ),
                repo.searches,
            )
        }

    @Test
    fun liveChangesReloadTheFirstPageButNeverUnderAReaderFurtherDown() =
        runTest(dispatcher) {
            val live = MutableSharedFlow<ActivityChange>(extraBufferCapacity = 8)
            val vm = ActivitiesViewModel(repo, liveChanges = live)
            vm.state.test {
                awaitItemWith { it.page != null && !it.refreshing }
                // A new record changes no list row.
                live.emit(ActivityChange(ActivityChange.Kind.RECORD, "a1"))
                live.emit(ActivityChange(ActivityChange.Kind.CREATED, "a2"))
                awaitItemWith { it.refreshing }
                awaitItemWith { !it.refreshing }
                assertEquals(2, repo.searches.size)
                vm.loadMore()
                awaitItemWith { it.loadingMore }
                awaitItemWith { !it.loadingMore }
                live.emit(ActivityChange(ActivityChange.Kind.CREATED, "a3"))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, repo.searches.size)
        }

    @Test
    fun failuresOfRefreshAndPagingShow() =
        runTest(dispatcher) {
            repo.fail = AppError.Network()
            val vm = ActivitiesViewModel(repo)
            vm.state.test {
                assertEquals("Offline, showing cached data", awaitItemWith { it.error != null }.error)
                vm.loadMore()
                val st = awaitItemWith { !it.loadingMore && it.error != null }
                assertFalse(st.refreshing)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
