package org.opencairn.cairn.core.network.connect

/** Connect / gRPC status codes, as the Connect protocol spells them in error bodies. */
enum class ConnectCode(
    val wire: String,
    val httpFallback: Int,
) {
    CANCELED("canceled", 499),
    UNKNOWN("unknown", 500),
    INVALID_ARGUMENT("invalid_argument", 400),
    DEADLINE_EXCEEDED("deadline_exceeded", 504),
    NOT_FOUND("not_found", 404),
    ALREADY_EXISTS("already_exists", 409),
    PERMISSION_DENIED("permission_denied", 403),
    RESOURCE_EXHAUSTED("resource_exhausted", 429),
    FAILED_PRECONDITION("failed_precondition", 400),
    ABORTED("aborted", 409),
    OUT_OF_RANGE("out_of_range", 400),
    UNIMPLEMENTED("unimplemented", 501),
    INTERNAL("internal", 500),
    UNAVAILABLE("unavailable", 503),
    DATA_LOSS("data_loss", 500),
    UNAUTHENTICATED("unauthenticated", 401),
    ;

    companion object {
        fun fromWire(s: String): ConnectCode? = entries.firstOrNull { it.wire == s }

        /** The protocol's mapping for a response whose body is missing or malformed. */
        fun fromHttpStatus(status: Int): ConnectCode =
            when (status) {
                400 -> INTERNAL
                401 -> UNAUTHENTICATED
                403 -> PERMISSION_DENIED
                404 -> UNIMPLEMENTED
                429, 502, 503, 504 -> UNAVAILABLE
                else -> UNKNOWN
            }
    }
}

/** An RPC failed with a Connect status; `details` are the raw encoded error details. */
class ConnectException(
    val code: ConnectCode,
    message: String,
    val procedure: String,
    val details: List<ConnectErrorDetail> = emptyList(),
    cause: Throwable? = null,
) : Exception("$procedure: ${code.wire}: $message", cause) {
    val statusMessage: String = message
}

data class ConnectErrorDetail(
    val type: String,
    val value: ByteArray,
)
