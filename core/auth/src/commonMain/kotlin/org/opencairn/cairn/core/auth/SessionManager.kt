package org.opencairn.cairn.core.auth

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import cairn.v1.GetCurrentUserRequest
import cairn.v1.GetInstanceInfoRequest
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.common.logger
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.database.upsertAccount
import org.opencairn.cairn.core.database.upsertServer
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.DeviceSignIn
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.core.domain.repository.SessionRepository
import org.opencairn.cairn.core.network.CairnApi
import org.opencairn.cairn.core.network.connect.ConnectCode
import org.opencairn.cairn.core.network.connect.ConnectException
import org.opencairn.cairn.core.network.connect.RequestAuthenticator
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Read-only scopes the viewer requests (core's scope families); a write-capable release adds `:write`. */
val READ_SCOPES =
    listOf(
        "activities:read",
        "profile:read",
        "social:read",
        "segments:read",
        "training:read",
        "gear:read",
        "notifications:read",
        "connections:read",
    )

/** Approving sign-ins of other devices (RFC 8628) from this one. */
val DEVICE_SCOPES = listOf("devices:read", "devices:write")

/** Everything the app asks for at registration and sign-in. */
val APP_SCOPES = READ_SCOPES + DEVICE_SCOPES

/**
 * Owns sign-in state: servers, the active account, its tokens (in the [SecretStore]) and the
 * authenticated [CairnApi]. Also the [RequestAuthenticator] every RPC goes through.
 */
class SessionManager(
    private val http: HttpClient,
    private val db: CairnDb,
    private val platform: AuthPlatform,
    private val dispatchers: DispatcherProvider,
    private val appVersion: String,
    private val clock: Clock = Clock.System,
) : SessionRepository,
    RequestAuthenticator {
    private val log = logger("session")
    private val oauth = OAuthHttp(http)
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()

    @Volatile private var cachedTokens: TokenSet? = null

    @Volatile private var cachedKey: AccountKey? = null

    override val activeAccount: Flow<AccountKey?> =
        db.accountsQueries.active().asFlow().mapToOneOrNull(dispatchers.io).map { row ->
            row?.let { AccountKey(it.server_base_url, it.user_id) }
        }

    override val currentUser: Flow<User?> =
        db.accountsQueries
            .active()
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { it?.toDomain() }

    /** The active account, or null when signed out. */
    suspend fun activeAccountKey(): AccountKey? = activeKey()

    /** The API bound to the active account's server and credentials. */
    suspend fun api(): CairnApi {
        val key = activeKey() ?: throw AppError.Unauthenticated()
        return CairnApi.connect(http, key.serverBaseUrl, this)
    }

    override suspend fun discoverServer(baseUrl: String): Result<Server> =
        apiResult {
            val url = normalize(baseUrl)
            val metadata = oauth.metadata(url)
            val info =
                runCatching {
                    CairnApi
                        .connect(http, url)
                        .auth
                        .GetInstanceInfo()
                        .execute(GetInstanceInfoRequest())
                }.getOrElse { e ->
                    // Core releases before handover H1 have no GetInstanceInfo; the server still works.
                    if (e is ConnectException && e.code == ConnectCode.UNIMPLEMENTED) null else throw e
                }
            val server =
                Server(
                    baseUrl = url,
                    version = info?.version?.ifBlank { null },
                    federationEnabled = info?.federation_enabled ?: false,
                    oauthEnabled = info?.oauth_enabled ?: (metadata.registrationEndpoint != null),
                )
            withContext(dispatchers.io) {
                db.upsertServer(
                    url,
                    server.version,
                    server.federationEnabled,
                    server.oauthEnabled,
                    clientId = null,
                    now = clock.now().epochSeconds,
                )
            }
            server
        }

    override suspend fun signIn(server: Server): Result<User> =
        apiResult {
            val metadata = oauth.metadata(server.baseUrl)
            val clientId = ensureClient(server.baseUrl, metadata)
            val scope = requestedScope(metadata)
            val config =
                AuthorizeConfig(
                    authorizationEndpoint = metadata.authorizationEndpoint,
                    tokenEndpoint = metadata.tokenEndpoint,
                    clientId = clientId,
                    scope = scope,
                    redirectUri = platform.redirectUri,
                )
            val tokens = platform.authFlow.authorize(config)
            activate(server, tokens.toTokenSet(clientId, scope))
        }

    override suspend fun signInWithToken(
        server: Server,
        token: String,
    ): Result<User> =
        apiResult {
            activate(server, TokenSet(kind = AuthKind.PAT, accessToken = token.trim()))
        }

    override suspend fun startDeviceSignIn(server: Server): Result<DeviceSignIn> =
        apiResult {
            val metadata = oauth.metadata(server.baseUrl)
            val endpoint =
                metadata.deviceAuthorizationEndpoint
                    ?: throw AppError.Server("this server does not offer sign-in from another device")
            val scope = requestedScope(metadata)
            val response =
                try {
                    oauth.deviceAuthorize(endpoint, ensureClient(server.baseUrl, metadata), scope)
                } catch (e: OAuthRequestException) {
                    // A client registered before the server knew the device grant: register once more.
                    if (e.error != "unauthorized_client") throw AppError.Server(e.message ?: e.error, e)
                    forgetClient(server.baseUrl)
                    oauth.deviceAuthorize(endpoint, ensureClient(server.baseUrl, metadata), scope)
                }
            DeviceSignIn(
                deviceCode = response.deviceCode,
                userCode = response.userCode,
                verificationUri = response.verificationUri,
                verificationUriComplete = response.verificationUriComplete ?: response.verificationUri,
                expiresAt = clock.now() + response.expiresIn.seconds,
                intervalSeconds = response.interval.coerceAtLeast(1),
            )
        }

    override suspend fun completeDeviceSignIn(
        server: Server,
        signIn: DeviceSignIn,
    ): Result<User> =
        apiResult {
            val metadata = oauth.metadata(server.baseUrl)
            val clientId = ensureClient(server.baseUrl, metadata)
            var interval = signIn.intervalSeconds
            while (true) {
                if (clock.now() >= signIn.expiresAt) throw AppError.Unauthenticated("the code expired; start again")
                delay(interval.seconds)
                when (val poll = oauth.pollDevice(metadata.tokenEndpoint, clientId, signIn.deviceCode)) {
                    is DevicePoll.Tokens -> return@apiResult activate(server, poll.tokens.toTokenSet(clientId))
                    DevicePoll.Pending -> Unit
                    DevicePoll.SlowDown -> interval += SLOW_DOWN_SECONDS
                    DevicePoll.Denied -> throw AppError.PermissionDenied("the sign-in was denied on the other device")
                    DevicePoll.Expired -> throw AppError.Unauthenticated("the code expired; start again")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }

    override suspend fun refreshCurrentUser(): Result<User> =
        apiResult {
            val key = activeKey() ?: throw AppError.Unauthenticated()
            val response = api().auth.GetCurrentUser().execute(GetCurrentUserRequest())
            val user = requireNotNull(response.user) { "server returned no user" }.toDomain(response.permissions)
            storeAccount(key.serverBaseUrl, user, currentTokens()?.kind ?: AuthKind.OAUTH, active = true)
            user
        }

    override suspend fun signOut() {
        val key = activeKey() ?: return
        val tokens = currentTokens()
        if (tokens?.kind == AuthKind.OAUTH && tokens.refreshToken != null && tokens.clientId != null) {
            revokeQuietly(key.serverBaseUrl, tokens.clientId, tokens.refreshToken)
        }
        platform.secretStore.remove(tokenKey(key))
        withContext(dispatchers.io) {
            db.accountsQueries.delete(key.serverBaseUrl, key.userId)
        }
        cachedTokens = null
        cachedKey = null
    }

    // RequestAuthenticator ------------------------------------------------------------------

    override suspend fun headers(): Map<String, String> {
        val tokens = currentTokens() ?: return emptyMap()
        val expiring = tokens.kind == AuthKind.OAUTH && tokens.expiresWithin(REFRESH_AHEAD_SECONDS, clock.now())
        val fresh = if (expiring) refresh(tokens) ?: tokens else tokens
        return mapOf("Authorization" to "Bearer ${fresh.accessToken}")
    }

    override suspend fun onUnauthenticated(): Boolean {
        val tokens = currentTokens() ?: return false
        if (tokens.kind != AuthKind.OAUTH) return false
        return refresh(tokens) != null
    }

    // internals -----------------------------------------------------------------------------

    private suspend fun activate(
        server: Server,
        tokens: TokenSet,
    ): User {
        val tempKey = AccountKey(server.baseUrl, PENDING_USER)
        platform.secretStore.put(tokenKey(tempKey), json.encodeToString(TokenSet.serializer(), tokens))
        cachedKey = tempKey
        cachedTokens = tokens
        val response =
            runCatching {
                CairnApi
                    .connect(http, server.baseUrl, this)
                    .auth
                    .GetCurrentUser()
                    .execute(GetCurrentUserRequest())
            }.getOrElse { e ->
                platform.secretStore.remove(tokenKey(tempKey))
                cachedKey = null
                cachedTokens = null
                throw e
            }
        val user = requireNotNull(response.user) { "server returned no user" }.toDomain(response.permissions)
        val key = AccountKey(server.baseUrl, user.id)
        platform.secretStore.put(tokenKey(key), json.encodeToString(TokenSet.serializer(), tokens))
        platform.secretStore.remove(tokenKey(tempKey))
        storeAccount(server.baseUrl, user, tokens.kind, active = true)
        cachedKey = key
        cachedTokens = tokens
        return user
    }

    /**
     * The client id registered on this server. A stored registration whose scopes miss what
     * the app asks for now (an older release registered it) is replaced by a new one.
     */
    private suspend fun ensureClient(
        baseUrl: String,
        metadata: OAuthMetadata,
    ): String {
        val row = withContext(dispatchers.io) { db.serversQueries.byUrl(baseUrl).executeAsOneOrNull() }
        val wanted = requestedScope(metadata).split(' ').filter { it.isNotBlank() }.toSet()
        val stored =
            row
                ?.oauth_client_scope
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                ?.toSet()
        val existing = row?.oauth_client_id
        if (existing != null && stored != null && stored.containsAll(wanted)) return existing
        val endpoint =
            metadata.registrationEndpoint
                ?: throw AppError.Server("this server does not allow apps to register (no registration_endpoint)")
        val registered =
            oauth.register(
                endpoint,
                ClientRegistrationRequest(
                    clientName = "Cairn app",
                    redirectUris = listOf(platform.redirectUri),
                    scope = APP_SCOPES.joinToString(" "),
                    softwareId = SOFTWARE_ID,
                    softwareVersion = appVersion,
                ),
            )
        withContext(dispatchers.io) {
            db.upsertServer(
                baseUrl,
                version = null,
                federation = false,
                oauth = true,
                clientId = registered.clientId,
                now = clock.now().epochSeconds,
                clientScope = registered.scope.ifBlank { wanted.joinToString(" ") },
            )
        }
        return registered.clientId
    }

    /** The catalog scopes the app wants, limited to what this server advertises. */
    private fun requestedScope(metadata: OAuthMetadata): String {
        val supported = metadata.scopesSupported
        return APP_SCOPES.filter { supported.isEmpty() || it in supported }.joinToString(" ")
    }

    private fun TokenResponse.toTokenSet(
        clientId: String,
        requestedScope: String = "",
    ) = TokenSet(
        kind = AuthKind.OAUTH,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = expiresIn?.let { clock.now().epochSeconds + it },
        scope = scope ?: requestedScope,
        clientId = clientId,
    )

    private suspend fun forgetClient(baseUrl: String) =
        withContext(dispatchers.io) { db.serversQueries.setClient(null, null, baseUrl) }

    private suspend fun refresh(tokens: TokenSet): TokenSet? =
        refreshMutex.withLock {
            val key = cachedKey ?: return null
            // Another caller may have refreshed while we waited for the lock.
            cachedTokens?.takeIf { it.accessToken != tokens.accessToken }?.let { return it }
            val refreshToken = tokens.refreshToken ?: return null
            val clientId = tokens.clientId ?: return null
            val metadata = runCatching { oauth.metadata(key.serverBaseUrl) }.getOrNull() ?: return null
            val response =
                runCatching { oauth.refresh(metadata.tokenEndpoint, clientId, refreshToken) }
                    .getOrElse { e ->
                        if (e is AppError.Unauthenticated) {
                            log.i(e) { "refresh token rejected; signing out" }
                            platform.secretStore.remove(tokenKey(key))
                            cachedTokens = null
                        } else {
                            log.w(e) { "refresh failed" }
                        }
                        return null
                    }
            val fresh =
                tokens.copy(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken ?: refreshToken,
                    expiresAtEpochSeconds = response.expiresIn?.let { clock.now().epochSeconds + it },
                    scope = response.scope ?: tokens.scope,
                )
            platform.secretStore.put(tokenKey(key), json.encodeToString(TokenSet.serializer(), fresh))
            cachedTokens = fresh
            fresh
        }

    private suspend fun revokeQuietly(
        baseUrl: String,
        clientId: String,
        refreshToken: String,
    ) {
        val endpoint = runCatching { oauth.metadata(baseUrl) }.getOrNull()?.revocationEndpoint ?: return
        runCatching { oauth.revoke(endpoint, clientId, refreshToken) }
            .onFailure { log.w(it) { "revoke failed" } }
    }

    private suspend fun activeKey(): AccountKey? {
        cachedKey?.takeIf { it.userId != PENDING_USER }?.let { return it }
        val row = withContext(dispatchers.io) { db.accountsQueries.active().executeAsOneOrNull() } ?: return null
        return AccountKey(row.server_base_url, row.user_id).also { cachedKey = it }
    }

    private suspend fun currentTokens(): TokenSet? {
        cachedTokens?.let { return it }
        val key = cachedKey ?: activeKey()
        val raw = key?.let { platform.secretStore.get(tokenKey(it)) }
        val tokens = raw?.let { r -> runCatching { json.decodeFromString(TokenSet.serializer(), r) }.getOrNull() }
        cachedTokens = tokens
        return tokens
    }

    private suspend fun storeAccount(
        baseUrl: String,
        user: User,
        kind: AuthKind,
        active: Boolean,
    ) = withContext(dispatchers.io) {
        db.upsertAccount(
            baseUrl,
            user.id,
            kind.name.lowercase(),
            user.username,
            user.email,
            user.displayName,
            user.avatarUrl,
            user.role.name,
            user.preferences.units.name,
            user.preferences.dateFormat.name,
            user.preferences.timeFormat.name,
            user.preferences.locale,
            user.preferences.timezone,
            user.permissions.joinToString(","),
            user.createdAt?.epochSeconds,
            clock.now().epochSeconds,
            active,
        )
    }

    private fun tokenKey(key: AccountKey) = "tokens:${key.storageId}"

    private fun normalize(input: String): String {
        var url = input.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        require(url.length > "https://".length) { "enter a server address" }
        return url
    }

    private companion object {
        const val SOFTWARE_ID = "org.opencairn.cairn"
        const val REFRESH_AHEAD_SECONDS = 60L
        const val SLOW_DOWN_SECONDS = 5L
        const val PENDING_USER = "pending"
    }
}
