package org.opencairn.cairn.core.data

import app.cash.turbine.test
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.AppRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class UpdateRepositoryTest {
    private class TestClock(
        var now: Instant = Instant.fromEpochSeconds(1_800_000_000),
    ) : Clock {
        override fun now(): Instant = now
    }

    private val page = "https://github.com/opencairn-org/cairn-kmp/releases"
    private val clock = TestClock()

    private fun FakeCore.release(
        tag: String,
        url: String = "$page/tag/$tag",
    ) {
        routes["/latest"] = { Reply.Text("""{"tag_name":"$tag","html_url":"$url","prerelease":false}""") }
    }

    private fun repo(
        core: FakeCore,
        version: String = "0.1.2",
        supported: Boolean = true,
    ) = UpdateRepositoryImpl(
        core.http,
        core.db,
        core.dispatchers,
        version,
        supported,
        clock,
        "https://releases.test/latest",
    )

    @Test
    fun aNewerReleaseIsOfferedUntilDismissed() =
        runTest {
            val core = FakeCore().apply { release("v0.2.0") }
            val repo = repo(core)
            repo.availableUpdate.test {
                assertNull(awaitItem())
                repo.checkIfDue().getOrThrow()
                assertEquals(AppRelease("0.2.0", "$page/tag/v0.2.0"), awaitItem())
                repo.dismiss("0.2.0")
                assertNull(awaitItem())
                // A later release shows the notice again.
                core.release("v0.2.1")
                clock.now += 25.hours
                repo.checkIfDue().getOrThrow()
                assertEquals("0.2.1", awaitItem()?.version)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theReleasePageIsAskedAtMostOnceADay() =
        runTest {
            val core = FakeCore().apply { release("v0.1.2") }
            val repo = repo(core)
            repo.checkIfDue().getOrThrow()
            clock.now += 23.hours
            repo.checkIfDue().getOrThrow()
            assertEquals(1, core.count("latest"))
            clock.now += 2.hours
            repo.checkIfDue().getOrThrow()
            assertEquals(2, core.count("latest"))
            // The running version is the latest: nothing to offer.
            assertNull(repo.availableUpdate.first())
        }

    @Test
    fun switchedOffOrUnsupportedBuildsNeverAsk() =
        runTest {
            val core = FakeCore().apply { release("v9.0.0") }
            val off = repo(core)
            assertTrue(off.checkEnabled.first())
            off.setCheckEnabled(false)
            assertFalse(off.checkEnabled.first())
            off.checkIfDue().getOrThrow()
            repo(core, supported = false).checkIfDue().getOrThrow()
            assertEquals(0, core.count("latest"))
            // Switching the check off also hides a notice that was already found.
            off.setCheckEnabled(true)
            off.checkIfDue().getOrThrow()
            assertEquals("9.0.0", off.availableUpdate.first()?.version)
            off.setCheckEnabled(false)
            assertNull(off.availableUpdate.first())
        }

    @Test
    fun aFailedCheckIsRetriedAndForeignLinksAreReplaced() =
        runTest {
            val core = FakeCore()
            core.routes["/latest"] = { Reply.Text("rate limited", HttpStatusCode.Forbidden) }
            val repo = repo(core)
            assertIs<AppError.Unavailable>(repo.checkIfDue().exceptionOrNull())
            core.release("v0.3.0", url = "https://evil.example/download")
            repo.checkIfDue().getOrThrow()
            assertEquals(AppRelease("0.3.0", page), repo.availableUpdate.first())
        }

    @Test
    fun versionsCompareNumericallyAndDevBuildsAreOlderThanTheirRelease() {
        assertTrue(isNewerVersion("0.2.0", "0.1.9"))
        assertTrue(isNewerVersion("v0.10.0", "0.9.3"))
        assertTrue(isNewerVersion("1.0", "0.9.9"))
        assertTrue(isNewerVersion("0.2.0", "0.2.0-dev"))
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("0.1.9", "0.2.0"))
        assertFalse(isNewerVersion("0.3.0-rc1", "0.2.0"))
        assertFalse(isNewerVersion("nightly", "0.2.0"))
        assertFalse(isNewerVersion("0.3.0", "e2e"))
    }
}
