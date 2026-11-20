package org.opencairn.cairn.core.data

import cairn.v1.ActivityEvent
import cairn.v1.ActivityEventType
import cairn.v1.StreamActivityEventsRequest
import cairn.v1.StreamNotificationsRequest
import com.squareup.wire.GrpcServerStreamingCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.common.logger
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.repository.LiveEventsRepository
import org.opencairn.cairn.core.network.CairnApi
import org.opencairn.cairn.core.network.connect.toAppError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Server streams as shared flows: each holds one connection while somebody collects, closes it
 * [LINGER] after the last collector left and reconnects with a growing pause when it breaks.
 */
class LiveEventsRepositoryImpl(
    private val scope: CacheScope,
    sharing: CoroutineScope,
    private val firstPause: Duration = 2.seconds,
) : LiveEventsRepository {
    private val log = logger("live")

    override val activityChanges: Flow<ActivityChange> =
        scope
            .perAccount { account ->
                if (account == null) {
                    emptyFlow()
                } else {
                    stream(StreamActivityEventsRequest()) { it.activities.StreamActivityEvents() }
                }
            }.mapNotNull { it.toChange() }
            .shareIn(sharing, SharingStarted.WhileSubscribed(LINGER.inWholeMilliseconds), replay = 0)

    override val notificationArrivals: Flow<Unit> =
        scope
            .perAccount { account ->
                if (account == null) {
                    emptyFlow()
                } else {
                    stream(StreamNotificationsRequest()) { it.notifications.StreamNotifications() }
                }
            }
            // The heartbeat is an event without an id.
            .filter { it.id.isNotEmpty() }
            .map { }
            .shareIn(sharing, SharingStarted.WhileSubscribed(LINGER.inWholeMilliseconds), replay = 0)

    private fun <S : Any, R : Any> stream(
        request: S,
        open: (CairnApi) -> GrpcServerStreamingCall<S, R>,
    ): Flow<R> =
        channelFlow {
            var pause = firstPause
            while (true) {
                val failure =
                    try {
                        for (message in open(scope.api()).executeIn(this, request)) {
                            pause = firstPause
                            send(message)
                        }
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        e.toAppError()
                    }
                if (failure != null && !failure.isWorthRetrying()) {
                    log.i { "live updates are off: ${failure.message}" }
                    break
                }
                delay(pause)
                pause = (pause * 2).coerceAtMost(LONGEST_PAUSE)
            }
        }

    /** A server without streaming, or a sign-in that may not listen, will not change by trying again. */
    private fun AppError.isWorthRetrying() =
        this !is AppError.Unimplemented && this !is AppError.PermissionDenied && this !is AppError.Unauthenticated

    private companion object {
        val LINGER: Duration = 5.seconds
        val LONGEST_PAUSE: Duration = 2.minutes
    }
}

internal fun ActivityEvent.toChange(): ActivityChange? {
    val kind =
        when (type) {
            ActivityEventType.ACTIVITY_EVENT_TYPE_ACTIVITY_CREATED -> ActivityChange.Kind.CREATED

            ActivityEventType.ACTIVITY_EVENT_TYPE_ACTIVITY_UPDATED,
            ActivityEventType.ACTIVITY_EVENT_TYPE_SOURCE_ATTACHED,
            ActivityEventType.ACTIVITY_EVENT_TYPE_SOURCE_DETACHED,
            ActivityEventType.ACTIVITY_EVENT_TYPE_REIMPORT_COMPLETED,
            -> ActivityChange.Kind.UPDATED

            ActivityEventType.ACTIVITY_EVENT_TYPE_ACTIVITY_DELETED -> ActivityChange.Kind.DELETED

            ActivityEventType.ACTIVITY_EVENT_TYPE_IMPORT_COMPLETED -> ActivityChange.Kind.IMPORT_FINISHED

            ActivityEventType.ACTIVITY_EVENT_TYPE_NEW_PR,
            ActivityEventType.ACTIVITY_EVENT_TYPE_SEGMENT_PR,
            -> ActivityChange.Kind.RECORD

            // Heartbeats, progress and failures change nothing the screens show.
            else -> return null
        }
    return ActivityChange(kind, activity_id.ifBlank { null })
}
