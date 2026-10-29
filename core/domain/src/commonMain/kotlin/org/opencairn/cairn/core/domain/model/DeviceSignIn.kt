package org.opencairn.cairn.core.domain.model

import kotlin.time.Instant

/**
 * A device sign-in in progress (RFC 8628): this device shows [userCode] and the verification
 * address while it waits for a signed-in device to approve it.
 */
data class DeviceSignIn(
    /** The secret this device polls with; never shown. */
    val deviceCode: String,
    /** Display form, "1234-5678". */
    val userCode: String,
    val verificationUri: String,
    /** The verification address with the code filled in; what the QR code encodes. */
    val verificationUriComplete: String,
    val expiresAt: Instant,
    val intervalSeconds: Long,
)

/** What the approving device sees before deciding. */
data class PendingDevice(
    val clientName: String,
    val softwareId: String,
    val softwareVersion: String,
    /** What approval by this account would grant. */
    val scopes: List<DeviceScope>,
    val expiresAt: Instant?,
)

data class DeviceScope(
    val scope: String,
    val description: String,
    val write: Boolean,
)
