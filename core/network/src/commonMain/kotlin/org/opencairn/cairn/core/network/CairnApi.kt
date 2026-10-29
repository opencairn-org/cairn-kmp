package org.opencairn.cairn.core.network

import cairn.v1.ActivityServiceClient
import cairn.v1.AuthServiceClient
import cairn.v1.GearServiceClient
import cairn.v1.GrpcActivityServiceClient
import cairn.v1.GrpcAuthServiceClient
import cairn.v1.GrpcGearServiceClient
import cairn.v1.GrpcMetricsServiceClient
import cairn.v1.GrpcNotificationServiceClient
import cairn.v1.GrpcSegmentServiceClient
import cairn.v1.GrpcSocialServiceClient
import cairn.v1.GrpcStatsServiceClient
import cairn.v1.MetricsServiceClient
import cairn.v1.NotificationServiceClient
import cairn.v1.SegmentServiceClient
import cairn.v1.SocialServiceClient
import cairn.v1.StatsServiceClient
import com.squareup.wire.GrpcClient
import io.ktor.client.HttpClient
import org.opencairn.cairn.core.network.connect.ConnectGrpcClient
import org.opencairn.cairn.core.network.connect.NoAuthenticator
import org.opencairn.cairn.core.network.connect.RequestAuthenticator

/** The `cairn.v1` service stubs for one server; one instance per (server, credentials). */
class CairnApi(
    val grpc: GrpcClient,
) {
    val auth: AuthServiceClient by lazy { GrpcAuthServiceClient(grpc) }
    val activities: ActivityServiceClient by lazy { GrpcActivityServiceClient(grpc) }
    val gear: GearServiceClient by lazy { GrpcGearServiceClient(grpc) }
    val notifications: NotificationServiceClient by lazy { GrpcNotificationServiceClient(grpc) }
    val segments: SegmentServiceClient by lazy { GrpcSegmentServiceClient(grpc) }
    val metrics: MetricsServiceClient by lazy { GrpcMetricsServiceClient(grpc) }
    val stats: StatsServiceClient by lazy { GrpcStatsServiceClient(grpc) }
    val social: SocialServiceClient by lazy { GrpcSocialServiceClient(grpc) }

    companion object {
        fun connect(
            httpClient: HttpClient,
            baseUrl: String,
            authenticator: RequestAuthenticator = NoAuthenticator,
        ): CairnApi = CairnApi(ConnectGrpcClient(httpClient, baseUrl, authenticator))
    }
}
