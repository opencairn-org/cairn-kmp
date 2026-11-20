package org.opencairn.cairn.ui.design.format

import org.opencairn.cairn.core.common.AppError

/** One wording per failure class for the stale/error banners; screens add nothing of their own. */
fun Throwable.userMessage(): String =
    when (this) {
        is AppError.Network, is AppError.Unavailable -> "Offline, showing cached data"

        is AppError.Unimplemented -> "This server does not provide ${procedure.substringAfterLast('/')} yet"

        is AppError.NotFound -> "Not found on the server"

        is AppError.Unauthenticated -> "Signed out by the server"

        // An older sign-in was granted fewer permissions than the app asks for now.
        is AppError.PermissionDenied -> if (message?.contains("scope") == true) SCOPE_HINT else message ?: "Not allowed"

        else -> message ?: "Could not refresh"
    }

private const val SCOPE_HINT =
    "This sign-in may not do that yet. Sign out and in again to grant the app the permission."
