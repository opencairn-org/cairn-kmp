package org.opencairn.cairn.feature.account

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.GearDraft
import org.opencairn.cairn.core.domain.model.RecordKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccountViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun accountOverviewHighlightsLatestValuesAndCounts() =
        runTest(dispatcher) {
            val vm = AccountViewModel(FakeAthlete(), FakeGear(), FakeRecords())
            vm.state.test {
                val st = awaitItemWith { it.profile.value != null && it.gear.value != null && it.records.value != null }
                // Only FTP has entries; the newest one wins. Weight is listed without a value.
                assertEquals(
                    listOf("ftp_watts" to 265.0, "weight_kg" to null),
                    st.highlights.map { (s, e) ->
                        s.key to
                            e?.value
                    },
                )
                assertEquals(1, st.activeGearCount)
                assertEquals(3, st.recordCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun accountOverviewReportsRefreshFailure() =
        runTest(dispatcher) {
            val athlete = FakeAthlete().apply { fail = AppError.Network() }
            val vm = AccountViewModel(athlete, FakeGear(), FakeRecords())
            vm.state.test {
                val st = awaitItemWith { it.error != null }
                assertEquals("Offline, showing cached data", st.error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun athleteHistoryIsNewestFirstAndEditsReachTheRepository() =
        runTest(dispatcher) {
            val repo = FakeAthlete()
            val vm = AthleteViewModel(repo)
            vm.state.test {
                val st = awaitItemWith { it.profile.value != null }
                assertEquals(
                    listOf("2026-06-01", "2026-01-01"),
                    st.profile.value!!
                        .history("ftp_watts")
                        .map { it.effectiveDate },
                )
                cancelAndIgnoreRemainingEvents()
            }
            vm.add("weight_kg", "2026-10-30", 72.5)
            dispatcher.scheduler.advanceUntilIdle()
            vm.delete("a")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(Triple("weight_kg", "2026-10-30", 72.5)), repo.added)
            assertEquals(listOf("a"), repo.deleted)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun metricFormattingMatchesTheWeb() {
        val pace = ftp.copy(key = "threshold_pace", unit = "sec/km")
        assertEquals("4:05 /km", pace.format(245.0))
        assertEquals("72.5 kg", weight.format(72.46))
        assertEquals("265 W", ftp.format(265.4))
    }

    @Test
    fun gearSplitsActiveAndRetiredAndActionsReachTheRepository() =
        runTest(dispatcher) {
            val repo = FakeGear()
            val vm = GearViewModel(repo)
            vm.state.test {
                val st = awaitItemWith { it.gear.value != null }
                assertEquals(listOf("1"), st.active.map { it.id })
                assertEquals(listOf("2"), st.retired.map { it.id })
                assertNotNull(st.byId("2"))
                vm.setRetired("1", true)
                dispatcher.scheduler.advanceUntilIdle()
                vm.delete("2")
                // The deleted id is published so the detail page can leave.
                assertEquals("2", awaitItemWith { it.deletedId != null }.deletedId)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("retire:1:true", "delete:2"), repo.calls)
        }

    @Test
    fun gearEditValidatesAndSaves() =
        runTest(dispatcher) {
            val repo = FakeGear()
            val vm = GearEditViewModel(repo, gearId = null)
            assertFalse(vm.state.value.valid)
            vm.update { it.copy(name = "Gravel", purchaseDate = "bad") }
            assertFalse(vm.state.value.valid)
            vm.update { it.copy(purchaseDate = "2026-05-01") }
            assertTrue(vm.state.value.valid)
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("create:Gravel"), repo.calls)
            assertTrue(vm.state.value.saved)

            val failing = GearEditViewModel(repo, gearId = null, initial = GearDraft(name = "boom"))
            failing.save()
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(failing.state.value.saved)
            assertEquals("nope", failing.state.value.error)

            val edit = GearEditViewModel(repo, gearId = "1", initial = GearDraft(name = "Renamed"))
            edit.save()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(repo.calls.contains("update:1:Renamed"))
        }

    @Test
    fun recordsAreGroupedInTheWebOrderAndSortedWithinAGroup() =
        runTest(dispatcher) {
            val vm = RecordsViewModel(FakeRecords())
            vm.state.test {
                val st = awaitItemWith { it.records.value != null }
                assertEquals(listOf(ActivityType.RIDE, ActivityType.RUN), st.groups.map { it.first })
                assertEquals(
                    listOf(300, 1200),
                    st.groups
                        .first()
                        .second
                        .map { it.windowValue },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun recordHistoryRefreshesItsBucket() =
        runTest(dispatcher) {
            val repo = FakeRecords()
            val key = RecordKey(ActivityType.RIDE, BestEffortMetric.POWER, BestEffortWindowKind.DURATION, 300)
            val vm = RecordHistoryViewModel(repo, key)
            vm.state.test {
                val st = awaitItemWith { it.history.value != null }
                assertEquals(310.0, st.history.value!!.best)
                cancelAndIgnoreRemainingEvents()
            }
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(key), repo.refreshedKeys)
        }

    private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemWith(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
