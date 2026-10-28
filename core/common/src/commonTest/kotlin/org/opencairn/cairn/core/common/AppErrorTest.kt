package org.opencairn.cairn.core.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppErrorTest {
    @Test
    fun transientErrorsKeepCachedData() {
        assertTrue(AppError.Network().isTransient)
        assertTrue(AppError.Unavailable().isTransient)
        assertFalse(AppError.Unauthenticated().isTransient)
        assertFalse(AppError.Unimplemented("cairn.v1.X/Y").isTransient)
    }
}
