package org.opencairn.cairn.core.network.connect

import org.opencairn.cairn.core.common.AppError

/** The one place transport failures become [AppError]s. */
fun Throwable.toAppError(): AppError =
    when (this) {
        is AppError -> this
        is ConnectException -> code.toAppError(this)
        else -> AppError.Unknown(message ?: "unexpected error", this)
    }

private fun ConnectCode.toAppError(e: ConnectException): AppError =
    when (this) {
        ConnectCode.UNAUTHENTICATED -> {
            AppError.Unauthenticated(e.statusMessage.ifBlank { "not signed in" })
        }

        ConnectCode.PERMISSION_DENIED -> {
            AppError.PermissionDenied(e.statusMessage.ifBlank { "not allowed" })
        }

        ConnectCode.NOT_FOUND -> {
            AppError.NotFound(e.statusMessage.ifBlank { "not found" })
        }

        ConnectCode.UNAVAILABLE, ConnectCode.DEADLINE_EXCEEDED, ConnectCode.RESOURCE_EXHAUSTED -> {
            val cause = e.cause
            if (cause != null) {
                AppError.Network(cause)
            } else {
                AppError.Unavailable(e.statusMessage.ifBlank { "server unavailable" })
            }
        }

        ConnectCode.UNIMPLEMENTED -> {
            AppError.Unimplemented(e.procedure)
        }

        ConnectCode.INTERNAL, ConnectCode.DATA_LOSS -> {
            AppError.Server(e.statusMessage.ifBlank { "server error" }, e)
        }

        else -> {
            AppError.Unknown("$wire: ${e.statusMessage}", e)
        }
    }

/** `runCatching` that keeps coroutine cancellation and maps everything else to [AppError]. */
@Suppress("TooGenericExceptionCaught")
inline fun <T> apiResult(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toAppError())
    }
