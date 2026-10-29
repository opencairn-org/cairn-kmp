package org.opencairn.cairn.ui.design.format

import org.opencairn.cairn.core.common.AppError

/** One wording per failure class for the stale/error banners; screens add nothing of their own. */
fun Throwable.userMessage(): String =
    when (this) {
        is AppError.Network, is AppError.Unavailable -> "Offline, showing cached data"
        is AppError.Unimplemented -> "This server does not provide ${procedure.substringAfterLast('/')} yet"
        is AppError.NotFound -> "Not found on the server"
        is AppError.Unauthenticated -> "Signed out by the server"
        else -> message ?: "Could not refresh"
    }
