package org.opencairn.cairn.core.data

import cairn.v1.DecideDeviceAuthorizationRequest
import cairn.v1.LookupDeviceAuthorizationRequest
import org.opencairn.cairn.core.domain.model.DeviceScope
import org.opencairn.cairn.core.domain.model.PendingDevice
import org.opencairn.cairn.core.domain.repository.DeviceApprovalRepository
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.time.Instant

/** Approving another device's sign-in over the AuthService device RPCs; nothing is cached. */
class DeviceApprovalRepositoryImpl(
    private val scope: CacheScope,
) : DeviceApprovalRepository {
    override suspend fun lookup(userCode: String): Result<PendingDevice> =
        apiResult {
            val r =
                scope
                    .api()
                    .auth
                    .LookupDeviceAuthorization()
                    .execute(LookupDeviceAuthorizationRequest(user_code = userCode))
            PendingDevice(
                clientName = r.client_name,
                softwareId = r.software_id,
                softwareVersion = r.software_version,
                scopes = r.scopes.map { DeviceScope(it.scope, it.description, it.write) },
                expiresAt = r.expires_at?.let { Instant.fromEpochSeconds(it.getEpochSecond(), it.getNano()) },
            )
        }

    override suspend fun decide(
        userCode: String,
        approve: Boolean,
    ): Result<List<String>> =
        apiResult {
            scope
                .api()
                .auth
                .DecideDeviceAuthorization()
                .execute(DecideDeviceAuthorizationRequest(user_code = userCode, approve = approve))
                .granted_scopes
        }
}
