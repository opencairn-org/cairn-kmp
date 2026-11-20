package org.opencairn.cairn.core.domain

import org.opencairn.cairn.core.domain.model.QueueCounts
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import org.opencairn.cairn.core.domain.model.SyncNotice
import org.opencairn.cairn.core.domain.model.syncNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncNoticeTest {
    private fun account(
        provider: String,
        status: SyncAccountStatus = SyncAccountStatus.ACTIVE,
        queue: QueueCounts = QueueCounts(),
        discovering: Boolean = false,
        rateLimited: Boolean = false,
    ) = SyncAccount(
        id = provider,
        provider = provider,
        label = "",
        status = status,
        statusReason = "",
        lastSyncAt = null,
        lastSuccessfulSyncAt = null,
        imported = 0,
        providerTotal = 0,
        newestActivityAt = null,
        discovering = discovering,
        rateLimited = rateLimited,
        autoImport = true,
        queue = queue,
        shortWindow = null,
        dailyWindow = null,
    )

    @Test
    fun idleHealthyAndDisabledAccountsSayNothing() {
        assertNull(emptyList<SyncAccount>().syncNotice())
        assertNull(listOf(account("strava"), account("garmin", SyncAccountStatus.DISABLED)).syncNotice())
        // Finished and failed items are history, not work in progress.
        assertNull(listOf(account("strava", queue = QueueCounts(done = 40, failed = 2))).syncNotice())
    }

    @Test
    fun aLostSignInOutranksWorkInProgress() {
        val accounts =
            listOf(
                account("strava", queue = QueueCounts(pending = 3, inProgress = 1)),
                account("garmin", SyncAccountStatus.AUTH_INVALID),
            )
        assertEquals(SyncNotice(SyncNotice.Kind.NEEDS_SIGN_IN, "Garmin"), accounts.syncNotice())
        assertEquals(SyncNotice(SyncNotice.Kind.IMPORTING, "Strava", 4), accounts.take(1).syncNotice())
    }

    @Test
    fun waitingOnTheProviderOrAWorkerComesLast() {
        assertEquals(
            SyncNotice.Kind.DISCOVERING,
            listOf(account("strava", rateLimited = true), account("polar", discovering = true)).syncNotice()?.kind,
        )
        assertEquals(SyncNotice.Kind.RATE_LIMITED, listOf(account("strava", rateLimited = true)).syncNotice()?.kind)
        assertEquals(
            SyncNotice.Kind.WORKER_OFFLINE,
            listOf(account("wahoo", SyncAccountStatus.WORKER_OFFLINE)).syncNotice()?.kind,
        )
    }
}
