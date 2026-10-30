package org.opencairn.cairn.feature.account

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.AthleteMetricEntry
import org.opencairn.cairn.core.domain.model.AthleteMetricSpec
import org.opencairn.cairn.core.domain.model.AthleteProfile
import org.opencairn.cairn.core.domain.model.BestEffortHistory
import org.opencairn.cairn.core.domain.model.BestEffortHistoryItem
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.GearDraft
import org.opencairn.cairn.core.domain.model.GearKind
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.model.RecordKey
import org.opencairn.cairn.core.domain.repository.AthleteRepository
import org.opencairn.cairn.core.domain.repository.GearRepository
import org.opencairn.cairn.core.domain.repository.RecordsRepository
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

val ftp = AthleteMetricSpec("ftp_watts", "FTP", "W", 30.0, 700.0)
val weight = AthleteMetricSpec("weight_kg", "Weight", "kg", 25.0, 250.0)

class FakeAthlete : AthleteRepository {
    val profile =
        MutableStateFlow(
            Cached(
                AthleteProfile(
                    specs = listOf(ftp, weight),
                    entries =
                        listOf(
                            AthleteMetricEntry("a", "ftp_watts", "2026-01-01", 250.0),
                            AthleteMetricEntry("b", "ftp_watts", "2026-06-01", 265.0),
                        ),
                ),
                Instant.fromEpochSeconds(1),
            ),
        )
    val added = mutableListOf<Triple<String, String, Double>>()
    val deleted = mutableListOf<String>()
    var fail: Throwable? = null

    override fun observeProfile(): Flow<Cached<AthleteProfile>> = profile

    override suspend fun refreshProfile(): Result<Unit> = fail?.let { Result.failure(it) } ?: Result.success(Unit)

    override suspend fun addEntry(
        key: String,
        date: String,
        value: Double,
    ): Result<Unit> {
        added += Triple(key, date, value)
        return Result.success(Unit)
    }

    override suspend fun deleteEntry(id: String): Result<Unit> {
        deleted += id
        return Result.success(Unit)
    }
}

fun gear(
    id: String,
    retired: Boolean = false,
) = Gear(
    id = id,
    kind = GearKind.BIKE,
    name = "Bike $id",
    brand = "Canyon",
    model = "Ultimate",
    notes = "",
    purchaseDate = "2024-03-01",
    retired = retired,
    retiredAt = null,
    totalDistance = Meters(1000.0),
    totalMoving = 2.hours,
    activityCount = 3,
    providers = emptyList(),
    defaultFor = listOf(ActivityType.RIDE),
)

class FakeGear : GearRepository {
    val gear = MutableStateFlow(Cached(listOf(gear("1"), gear("2", retired = true)), Instant.fromEpochSeconds(1)))
    val calls = mutableListOf<String>()

    override fun observeGear(): Flow<Cached<List<Gear>>> = gear

    override suspend fun refreshGear(): Result<Unit> = Result.success(Unit)

    override suspend fun createGear(draft: GearDraft): Result<Gear> {
        calls += "create:${draft.name}"
        return if (draft.name == "boom") Result.failure(AppError.Server("nope")) else Result.success(gear("3"))
    }

    override suspend fun updateGear(
        id: String,
        draft: GearDraft,
    ): Result<Gear> {
        calls += "update:$id:${draft.name}"
        return Result.success(gear(id))
    }

    override suspend fun setRetired(
        id: String,
        retired: Boolean,
    ): Result<Unit> {
        calls += "retire:$id:$retired"
        return Result.success(Unit)
    }

    override suspend fun deleteGear(id: String): Result<Unit> {
        calls += "delete:$id"
        return Result.success(Unit)
    }
}

fun record(
    type: ActivityType,
    metric: BestEffortMetric,
    window: Int,
) = PersonalRecord(type, metric, BestEffortWindowKind.DURATION, window, 300.0, "act-$window", null)

class FakeRecords : RecordsRepository {
    val records =
        MutableStateFlow(
            Cached(
                listOf(
                    record(ActivityType.RUN, BestEffortMetric.PACE, 600),
                    record(ActivityType.RIDE, BestEffortMetric.POWER, 1200),
                    record(ActivityType.RIDE, BestEffortMetric.POWER, 300),
                ),
                Instant.fromEpochSeconds(1),
            ),
        )
    val history =
        MutableStateFlow(
            Cached(
                BestEffortHistory(
                    best = 310.0,
                    count = 2,
                    items =
                        listOf(
                            BestEffortHistoryItem("a1", "Ride 1", null, 290.0, false),
                            BestEffortHistoryItem("a2", "Ride 2", null, 310.0, true),
                        ),
                ),
                Instant.fromEpochSeconds(1),
            ),
        )
    val refreshedKeys = mutableListOf<RecordKey>()

    override fun observeRecords(): Flow<Cached<List<PersonalRecord>>> = records

    override suspend fun refreshRecords(): Result<Unit> = Result.success(Unit)

    override fun observeHistory(key: RecordKey): Flow<Cached<BestEffortHistory>> = history

    override suspend fun refreshHistory(key: RecordKey): Result<Unit> {
        refreshedKeys += key
        return Result.success(Unit)
    }
}
