package org.opencairn.cairn.feature.login

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.core.domain.repository.SessionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeSession(
        var discover: Result<Server>,
    ) : SessionRepository {
        override val activeAccount: Flow<AccountKey?> = flowOf(null)
        override val currentUser: Flow<User?> = flowOf(null)
        var signedIn = false

        override suspend fun discoverServer(baseUrl: String) = discover

        override suspend fun signIn(server: Server): Result<User> {
            signedIn = true
            return Result.failure(AppError.Unknown("n/a"))
        }

        override suspend fun signInWithToken(
            server: Server,
            token: String,
        ): Result<User> = Result.failure(AppError.Unauthenticated())

        override suspend fun refreshCurrentUser(): Result<User> = Result.failure(AppError.Unauthenticated())

        override suspend fun signOut() = Unit
    }

    private val server = Server("https://cairn.test", "v0.6.0", federationEnabled = false, oauthEnabled = true)

    @Test
    fun discoverMovesToServerStep() =
        runTest(dispatcher) {
            val vm = LoginViewModel(FakeSession(Result.success(server)))
            vm.onServerInput("cairn.test")
            vm.discover()
            vm.state.test {
                val done = awaitItem().let { if (it.busy) awaitItem() else it }
                assertNotNull(done.server)
                assertEquals("cairn.test", done.server.host)
                assertNull(done.error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun networkErrorIsReadable() =
        runTest(dispatcher) {
            val vm = LoginViewModel(FakeSession(Result.failure(AppError.Network())))
            vm.onServerInput("cairn.test")
            vm.discover()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("Could not reach this server.", vm.state.value.error)
            assertNull(vm.state.value.server)
        }

    @Test
    fun tokenSignInFailureShowsMessage() =
        runTest(dispatcher) {
            val vm = LoginViewModel(FakeSession(Result.success(server)))
            vm.onServerInput("cairn.test")
            vm.discover()
            dispatcher.scheduler.advanceUntilIdle()
            vm.toggleTokenEntry()
            vm.onTokenInput("cairn_pat_abc")
            vm.signInWithToken()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("The server rejected the credentials.", vm.state.value.error)
        }
}
