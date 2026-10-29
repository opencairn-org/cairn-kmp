package org.opencairn.cairn.core.domain.repository

import org.opencairn.cairn.core.domain.model.PendingDevice

/** The approving side of a device sign-in: look a code up, then approve or deny it. */
interface DeviceApprovalRepository {
    suspend fun lookup(userCode: String): Result<PendingDevice>

    /** Returns the granted scopes (empty when denied). */
    suspend fun decide(
        userCode: String,
        approve: Boolean,
    ): Result<List<String>>
}
