package org.opencairn.cairn.app.screenshots

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.AthleteMetricEntry
import org.opencairn.cairn.core.domain.model.AthleteMetricSpec
import org.opencairn.cairn.core.domain.model.AthleteProfile
import org.opencairn.cairn.core.domain.model.BestEffortHistory
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.GearDraft
import org.opencairn.cairn.core.domain.model.GearKind
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.NotificationSeverity
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.QueueCounts
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.RateLimitWindow
import org.opencairn.cairn.core.domain.model.RecordKey
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.core.domain.model.UnreadCount
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.core.domain.model.UserRole
import org.opencairn.cairn.core.domain.repository.AthleteRepository
import org.opencairn.cairn.core.domain.repository.GearRepository
import org.opencairn.cairn.core.domain.repository.NotificationRepository
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.domain.repository.RecordsRepository
import org.opencairn.cairn.core.domain.repository.SyncRepository
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val done = Result.success(Unit)

val sampleUser =
    User("u1", "jo", "jo@cairn.example", "Jo Brandt", null, UserRole.USER, Preferences(), emptyList(), NOW - 400.days)

val sampleAccounts =
    listOf(
        SyncAccount(
            id = "x1",
            provider = "strava",
            label = "",
            status = SyncAccountStatus.ACTIVE,
            statusReason = "",
            lastSyncAt = NOW - 4.minutes,
            lastSuccessfulSyncAt = NOW - 4.minutes,
            imported = 296,
            providerTotal = 309,
            newestActivityAt = NOW - 1.days,
            discovering = false,
            rateLimited = false,
            autoImport = true,
            queue = QueueCounts(pending = 11, inProgress = 2, done = 296, failed = 1),
            shortWindow = RateLimitWindow(184, 200, NOW + 9.minutes),
            dailyWindow = RateLimitWindow(612, 2000, NOW + 11.hours),
        ),
        SyncAccount(
            id = "x2",
            provider = "garmin",
            label = "Forerunner",
            status = SyncAccountStatus.AUTH_INVALID,
            statusReason = "refresh token rejected",
            lastSyncAt = NOW - 3.days,
            lastSuccessfulSyncAt = NOW - 9.days,
            imported = 118,
            providerTotal = 0,
            newestActivityAt = NOW - 9.days,
            discovering = false,
            rateLimited = false,
            autoImport = true,
            queue = QueueCounts(),
            shortWindow = null,
            dailyWindow = null,
        ),
    )

val sampleQueue =
    listOf(
        QueueItem("q1", "activity", "14402211873", "in_progress", 1, "", NOW - 5.minutes, NOW - 1.days),
        QueueItem("q2", "activity", "14398830112", "pending", 0, "", NOW - 5.minutes, NOW - 2.days),
        QueueItem(
            "q3",
            "activity",
            "14371199054",
            "failed",
            3,
            "stream download timed out",
            NOW - 2.hours,
            NOW - 6.days,
        ),
    )

val sampleHistory =
    listOf(
        SyncEvent("activity_imported", 1, "Morning ride", NOW - 4.minutes, "a0", "completed"),
        SyncEvent("activity_imported", 1, "Lunch run", NOW - 6.minutes, "a1", "completed"),
        SyncEvent("failed", 1, "stream download timed out", NOW - 2.hours, null, "failed"),
        SyncEvent("sync_started", 13, "", NOW - 3.hours, null, "ongoing"),
    )

private fun notification(
    id: String,
    type: String,
    severity: NotificationSeverity,
    read: Boolean,
    age: kotlin.time.Duration,
    params: Map<String, String> = emptyMap(),
    activityId: String? = null,
    count: Int = 1,
) = Notification(id, type, severity, params, activityId, count, read, NOW - age)

val sampleNotifications =
    listOf(
        notification(
            "n1",
            "achievement_new_personal_record",
            NotificationSeverity.INFO,
            read = false,
            age = 4.minutes,
            params = mapOf("metric" to "power", "window" to "20 min", "value" to "268 W"),
            activityId = "a0",
        ),
        notification(
            "n2",
            "account_external_auth_expired",
            NotificationSeverity.WARN,
            read = false,
            age = 3.days,
            params = mapOf("provider" to "garmin"),
        ),
        notification(
            "n3",
            "sync_bulk_import_completed",
            NotificationSeverity.INFO,
            read = true,
            age = 9.days,
            params = mapOf("provider" to "strava"),
            count = 12,
        ),
        notification("n4", "security_login_new_device", NotificationSeverity.WARN, read = true, age = 20.days),
    )

class SampleSync(
    private val accounts: List<SyncAccount> = sampleAccounts,
) : SyncRepository {
    override fun observeAccounts(): Flow<Cached<List<SyncAccount>>> = cached(accounts)

    override suspend fun refreshAccounts() = done

    override fun observeHistory(accountId: String): Flow<Cached<List<SyncEvent>>> = cached(sampleHistory)

    override suspend fun refreshHistory(accountId: String) = done

    override fun observeQueue(accountId: String): Flow<Cached<List<QueueItem>>> = cached(sampleQueue)

    override suspend fun refreshQueue(accountId: String) = done
}

class SampleNotifications : NotificationRepository {
    override fun observeNotifications(): Flow<Cached<List<Notification>>> = cached(sampleNotifications)

    override suspend fun refreshNotifications() = done

    override fun observeUnread(): Flow<Cached<UnreadCount>> = cached(UnreadCount(total = 2, warnings = 1))

    override suspend fun refreshUnread() = done

    override suspend fun markRead(id: String) = done

    override suspend fun markAllRead() = done
}

class SampleOverview : OverviewRepository {
    override fun observeOverview(): Flow<Cached<Overview>> =
        cached(
            Overview(
                totals = sampleStats.totals,
                bySport = sampleStats.bySport,
                recent = sampleActivities.take(6),
                thisWeek = Totals(4, Meters(164_200.0), 7.hours + 12.minutes, Meters(2_140.0)),
                lastWeek = Totals(5, Meters(201_800.0), 8.hours + 47.minutes, Meters(2_930.0)),
                thisWeekRange = null,
                lastWeekRange = null,
            ),
        )

    override suspend fun refreshOverview() = done

    override fun observeHomeFeed(): Flow<Cached<List<FeedItem>>> =
        cached(
            sampleActivities.take(4).map { a ->
                FeedItem(
                    source = FeedItem.Source.SELF,
                    owner = null,
                    id = a.id,
                    title = a.title,
                    type = a.type,
                    sport = a.type.wire,
                    startTime = a.startTime,
                    distance = a.summary.distance,
                    elevationGain = a.summary.elevationGain,
                    elapsed = a.elapsed,
                    startPlace = a.startPlace,
                    mapImageUrl = null,
                    externalUrl = null,
                )
            },
        )

    override suspend fun refreshHomeFeed() = done
}

class SampleAthlete : AthleteRepository {
    override fun observeProfile(): Flow<Cached<AthleteProfile>> =
        cached(
            AthleteProfile(
                specs =
                    listOf(
                        AthleteMetricSpec("ftp_watts", "FTP", "W", 30.0, 700.0),
                        AthleteMetricSpec("threshold_hr", "Threshold heart rate", "bpm", 60.0, 230.0),
                        AthleteMetricSpec("weight_kg", "Weight", "kg", 25.0, 250.0),
                    ),
                entries =
                    listOf(
                        AthleteMetricEntry("m1", "ftp_watts", "2026-09-01", 265.0),
                        AthleteMetricEntry("m2", "threshold_hr", "2026-03-14", 165.0),
                        AthleteMetricEntry("m3", "weight_kg", "2026-11-02", 71.5),
                    ),
            ),
        )

    override suspend fun refreshProfile() = done

    override suspend fun addEntry(
        key: String,
        date: String,
        value: Double,
    ) = done

    override suspend fun deleteEntry(id: String) = done
}

private val sampleGear =
    Gear(
        id = "g1",
        kind = GearKind.BIKE,
        name = "Canyon Ultimate",
        brand = "Canyon",
        model = "Ultimate CF SL",
        notes = "",
        purchaseDate = "2024-03-01",
        retired = false,
        retiredAt = null,
        totalDistance = Meters(8_420_000.0),
        totalMoving = 331.hours,
        activityCount = 204,
        providers = emptyList(),
        defaultFor = listOf(ActivityType.RIDE),
    )

class SampleGear : GearRepository {
    override fun observeGear(): Flow<Cached<List<Gear>>> =
        cached(listOf(sampleGear, sampleGear.copy(id = "g2", name = "Saucony Ride")))

    override suspend fun refreshGear() = done

    override suspend fun createGear(draft: GearDraft): Result<Gear> = Result.success(sampleGear)

    override suspend fun updateGear(
        id: String,
        draft: GearDraft,
    ): Result<Gear> = Result.success(sampleGear)

    override suspend fun setRetired(
        id: String,
        retired: Boolean,
    ) = done

    override suspend fun deleteGear(id: String) = done
}

class SampleRecords : RecordsRepository {
    override fun observeRecords(): Flow<Cached<List<PersonalRecord>>> =
        cached(
            List(7) { i ->
                PersonalRecord(
                    ActivityType.RIDE,
                    BestEffortMetric.POWER,
                    BestEffortWindowKind.DURATION,
                    60 * (i + 1),
                    320.0 - i * 9,
                    "a$i",
                    NOW - i.days,
                )
            },
        )

    override suspend fun refreshRecords() = done

    override fun observeHistory(key: RecordKey): Flow<Cached<BestEffortHistory>> = flowOf(Cached(null, null))

    override suspend fun refreshHistory(key: RecordKey) = done
}
