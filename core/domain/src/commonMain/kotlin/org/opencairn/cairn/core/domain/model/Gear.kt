package org.opencairn.cairn.core.domain.model

import kotlin.time.Duration
import kotlin.time.Instant

enum class GearKind { BIKE, SHOE, OTHER }

/** Equipment with its lifetime use; provider links tell where it was imported from. */
data class Gear(
    val id: String,
    val kind: GearKind,
    val name: String,
    val brand: String,
    val model: String,
    val notes: String,
    /** ISO `YYYY-MM-DD`, empty when unknown. */
    val purchaseDate: String,
    val retired: Boolean,
    val retiredAt: Instant?,
    val totalDistance: Meters,
    val totalMoving: Duration,
    val activityCount: Long,
    val providers: List<GearProviderRef>,
    val defaultFor: List<ActivityType>,
) {
    val subtitle: String get() = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
}

data class GearProviderRef(
    val provider: String,
    val providerName: String,
    val primary: Boolean,
)

/** The editable fields of a gear item (create and update). */
data class GearDraft(
    val kind: GearKind = GearKind.BIKE,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val notes: String = "",
    val purchaseDate: String = "",
)
