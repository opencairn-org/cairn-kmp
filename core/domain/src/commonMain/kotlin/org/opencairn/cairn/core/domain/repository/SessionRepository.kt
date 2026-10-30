package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.DeviceSignIn
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.model.User

/** Sign-in state across app restarts. Implemented in core/auth. */
interface SessionRepository {
    /** Null while signed out. */
    val activeAccount: Flow<AccountKey?>
    val currentUser: Flow<User?>

    suspend fun discoverServer(baseUrl: String): Result<Server>

    suspend fun signIn(server: Server): Result<User>

    suspend fun signInWithToken(
        server: Server,
        token: String,
    ): Result<User>

    /** Asks the server for a code pair; the UI shows the [DeviceSignIn] while [completeDeviceSignIn] waits. */
    suspend fun startDeviceSignIn(server: Server): Result<DeviceSignIn>

    /** Polls until the code is approved (signed in), denied or expired; cancelling stops the wait. */
    suspend fun completeDeviceSignIn(
        server: Server,
        signIn: DeviceSignIn,
    ): Result<User>

    suspend fun refreshCurrentUser(): Result<User>

    /** Saves format and unit preferences on the server and in the local account. */
    suspend fun updatePreferences(preferences: Preferences): Result<User>

    /** What the app knows about the active account's server, null while signed out. */
    suspend fun activeServer(): Server?

    suspend fun signOut()
}
