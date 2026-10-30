package org.opencairn.cairn.core.data

import cairn.v1.CreateGearRequest
import cairn.v1.DeleteGearRequest
import cairn.v1.ListGearRequest
import cairn.v1.ListGearResponse
import cairn.v1.UpdateGearRequest
import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.GearDraft
import org.opencairn.cairn.core.domain.model.GearKind
import org.opencairn.cairn.core.domain.model.GearProviderRef
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.repository.GearRepository
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.time.Duration.Companion.seconds
import cairn.v1.Gear as ProtoGear
import cairn.v1.GearKind as ProtoKind

class GearRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : GearRepository {
    override fun observeGear(): Flow<Cached<List<Gear>>> =
        snapshots.observe(KEY) { bytes ->
            ListGearResponse.ADAPTER
                .decode(bytes)
                .gear
                .map { it.toDomain() }
        }

    override suspend fun refreshGear(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .gear
                    .ListGear()
                    .execute(ListGearRequest(include_retired = true))
            snapshots.put(KEY, response.encode())
        }

    override suspend fun createGear(draft: GearDraft): Result<Gear> =
        apiResult {
            val request =
                CreateGearRequest(
                    kind = draft.kind.toProto(),
                    name = draft.name,
                    brand = draft.brand,
                    model = draft.model,
                    notes = draft.notes,
                    purchase_date = draft.purchaseDate,
                )
            requireNotNull(
                scope
                    .api()
                    .gear
                    .CreateGear()
                    .execute(request)
                    .gear,
            ).toDomain()
        }.alsoRefresh()

    override suspend fun updateGear(
        id: String,
        draft: GearDraft,
    ): Result<Gear> =
        apiResult {
            val request =
                UpdateGearRequest(
                    id = id,
                    field_mask = listOf("name", "brand", "model", "notes", "purchase_date", "kind"),
                    name = draft.name,
                    brand = draft.brand,
                    model = draft.model,
                    notes = draft.notes,
                    purchase_date = draft.purchaseDate,
                    kind = draft.kind.toProto(),
                )
            requireNotNull(
                scope
                    .api()
                    .gear
                    .UpdateGear()
                    .execute(request)
                    .gear,
            ).toDomain()
        }.alsoRefresh()

    override suspend fun setRetired(
        id: String,
        retired: Boolean,
    ): Result<Unit> =
        apiResult {
            val request = UpdateGearRequest(id = id, field_mask = listOf("retired"), retired = retired)
            scope
                .api()
                .gear
                .UpdateGear()
                .execute(request)
        }.mapCatching { refreshGear().getOrThrow() }

    override suspend fun deleteGear(id: String): Result<Unit> =
        apiResult {
            scope
                .api()
                .gear
                .DeleteGear()
                .execute(DeleteGearRequest(id = id))
        }.mapCatching { refreshGear().getOrThrow() }

    private suspend fun <T> Result<T>.alsoRefresh(): Result<T> =
        mapCatching {
            refreshGear().getOrThrow()
            it
        }

    private companion object {
        const val KEY = "gear"
    }
}

internal fun ProtoGear.toDomain() =
    Gear(
        id = id,
        kind = kind.toDomain(),
        name = name,
        brand = brand,
        model = model,
        notes = notes,
        purchaseDate = purchase_date,
        retired = retired,
        retiredAt = retired_at?.toInstant(),
        totalDistance = Meters(total_distance_m),
        totalMoving = total_moving_s.seconds,
        activityCount = activity_count,
        providers = external_refs.map { GearProviderRef(it.provider, it.provider_name, it.primary) },
        defaultFor = external_refs.flatMap { r -> r.default_for.map { it.toDomain() } }.distinct(),
    )

internal fun ProtoKind.toDomain() =
    when (this) {
        ProtoKind.GEAR_KIND_BIKE -> GearKind.BIKE
        ProtoKind.GEAR_KIND_SHOE -> GearKind.SHOE
        else -> GearKind.OTHER
    }

internal fun GearKind.toProto() =
    when (this) {
        GearKind.BIKE -> ProtoKind.GEAR_KIND_BIKE
        GearKind.SHOE -> ProtoKind.GEAR_KIND_SHOE
        GearKind.OTHER -> ProtoKind.GEAR_KIND_OTHER
    }
