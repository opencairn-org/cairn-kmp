package org.opencairn.cairn.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.domain.model.AppRelease
import org.opencairn.cairn.core.domain.repository.UpdateRepository
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Looks at the project's release page for a newer version, at most once a day. The request
 * carries nothing about the user or the server they use; the result lives in the app settings.
 */
class UpdateRepositoryImpl(
    private val http: HttpClient,
    private val db: CairnDb,
    private val dispatchers: DispatcherProvider,
    private val currentVersion: String,
    override val supported: Boolean,
    private val clock: Clock = Clock.System,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) : UpdateRepository {
    private val settings get() = db.settingsQueries

    override val checkEnabled: Flow<Boolean> = setting(ENABLED).map { it != "false" }.distinctUntilChanged()

    override val availableUpdate: Flow<AppRelease?> =
        combine(setting(LATEST_VERSION), setting(LATEST_URL), setting(DISMISSED), checkEnabled) { v, url, gone, on ->
            val newer = supported && on && v != null && v != gone && isNewerVersion(v, currentVersion)
            if (newer) AppRelease(v, url ?: RELEASES_PAGE) else null
        }.distinctUntilChanged()

    override suspend fun setCheckEnabled(enabled: Boolean) = put(ENABLED, enabled.toString())

    override suspend fun dismiss(version: String) = put(DISMISSED, version)

    override suspend fun checkIfDue(): Result<Unit> =
        apiResult {
            if (!supported || !checkEnabled.first()) return@apiResult
            val now = clock.now().epochSeconds
            val last = withContext(dispatchers.io) { settings.byKey(CHECKED_AT).executeAsOneOrNull()?.toLongOrNull() }
            if (last != null && now - last < 1.days.inWholeSeconds) return@apiResult
            val release = fetchLatest()
            withContext(dispatchers.io) {
                db.transaction {
                    settings.put(CHECKED_AT, now.toString())
                    settings.put(LATEST_VERSION, release.version)
                    settings.put(LATEST_URL, release.url)
                }
            }
        }

    private suspend fun fetchLatest(): AppRelease {
        val response = http.get(latestReleaseUrl) { header(HttpHeaders.Accept, "application/vnd.github+json") }
        if (response.status != HttpStatusCode.OK) {
            throw AppError.Unavailable("the release page answered ${response.status.value}")
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val tag = body["tag_name"]?.jsonPrimitive?.content ?: throw AppError.Server("the release has no tag")
        // Only ever send the user to the project's own release pages.
        val page = body["html_url"]?.jsonPrimitive?.content?.takeIf { it.startsWith(RELEASES_PAGE) }
        return AppRelease(tag.removePrefix("v"), page ?: RELEASES_PAGE)
    }

    private fun setting(key: String): Flow<String?> = settings.byKey(key).asFlow().mapToOneOrNull(dispatchers.io)

    private suspend fun put(
        key: String,
        value: String,
    ) {
        withContext(dispatchers.io) { settings.put(key, value) }
    }

    private companion object {
        const val RELEASES_PAGE = "https://github.com/opencairn-org/cairn-kmp/releases"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/opencairn-org/cairn-kmp/releases/latest"
        const val ENABLED = "update.enabled"
        const val CHECKED_AT = "update.checked_at"
        const val LATEST_VERSION = "update.latest_version"
        const val LATEST_URL = "update.latest_url"
        const val DISMISSED = "update.dismissed"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Whether release [candidate] is newer than the running [current]: compared by the numeric
 * `major.minor.patch`; with equal numbers a version without a suffix beats one with (`0.2.0` is
 * newer than `0.2.0-dev`). A candidate that is itself a pre-release is never offered.
 */
internal fun isNewerVersion(
    candidate: String,
    current: String,
): Boolean {
    val new = candidate.removePrefix("v")
    val old = current.removePrefix("v")
    val a = new.takeUnless { '-' in it }?.versionNumbers()
    val b = old.substringBefore('-').versionNumbers()
    if (a == null || b == null) return false
    val order =
        (0 until maxOf(a.size, b.size))
            .map { i -> a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 }) }
            .firstOrNull { it != 0 }
    return if (order == null) '-' in old else order > 0
}

private fun String.versionNumbers(): List<Int>? {
    val parts = split('.').map { it.toIntOrNull() }
    return if (parts.isEmpty() || parts.any { it == null }) null else parts.filterNotNull()
}
