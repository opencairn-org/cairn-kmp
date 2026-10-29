package org.opencairn.cairn.feature.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.DeviceScope
import org.opencairn.cairn.core.domain.model.PendingDevice
import org.opencairn.cairn.core.domain.repository.DeviceApprovalRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApproveDeviceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeApprovals : DeviceApprovalRepository {
        val decisions = mutableListOf<Pair<String, Boolean>>()
        val pending =
            PendingDevice("TV", "org.example.tv", "1.0", listOf(DeviceScope("activities:read", "Read", false)), null)

        override suspend fun lookup(userCode: String): Result<PendingDevice> =
            if (userCode == "1234-5678") Result.success(pending) else Result.failure(AppError.NotFound("no device"))

        override suspend fun decide(
            userCode: String,
            approve: Boolean,
        ): Result<List<String>> {
            decisions += userCode to approve
            return Result.success(if (approve) listOf("activities:read") else emptyList())
        }
    }

    @Test
    fun codeIsFormattedAndLookedUp() =
        runTest(dispatcher) {
            val repo = FakeApprovals()
            val vm = ApproveDeviceViewModel(repo)
            vm.onCodeInput("1234 5678 9")
            assertEquals("1234-5678", vm.state.value.code)
            vm.lookup()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "TV",
                vm.state.value.pending
                    ?.clientName,
            )
            vm.decide(true)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("1234-5678" to true), repo.decisions)
            assertEquals(true, vm.state.value.decided)
            assertNull(vm.state.value.pending)
        }

    @Test
    fun unknownCodeShowsAnError() =
        runTest(dispatcher) {
            val vm = ApproveDeviceViewModel(FakeApprovals())
            vm.onCodeInput("0000-0000")
            vm.lookup()
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(vm.state.value.pending)
            assertNotNull(vm.state.value.error)
            // Editing the code clears the error and any earlier result.
            vm.onCodeInput("1")
            assertNull(vm.state.value.error)
        }
}
