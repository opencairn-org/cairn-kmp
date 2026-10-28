package org.opencairn.cairn.core.common

/** Errors surfaced to the UI. Transport details are mapped once, in the network layer. */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(
        cause: Throwable? = null,
    ) : AppError("network unavailable", cause)

    class Unauthenticated(
        message: String = "not signed in",
    ) : AppError(message)

    class PermissionDenied(
        message: String = "not allowed",
    ) : AppError(message)

    class NotFound(
        message: String = "not found",
    ) : AppError(message)

    class Unavailable(
        message: String = "server unavailable",
    ) : AppError(message)

    class Unimplemented(
        val procedure: String,
    ) : AppError("server does not implement $procedure")

    class Server(
        message: String,
        cause: Throwable? = null,
    ) : AppError(message, cause)

    class Unknown(
        message: String,
        cause: Throwable? = null,
    ) : AppError(message, cause)
}

/** Whether a cached value should still be shown instead of this error. */
val AppError.isTransient: Boolean
    get() = this is AppError.Network || this is AppError.Unavailable
