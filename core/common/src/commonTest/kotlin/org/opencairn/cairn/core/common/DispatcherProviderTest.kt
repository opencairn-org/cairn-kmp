package org.opencairn.cairn.core.common

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DispatcherProviderTest {
    @Test
    fun defaultProviderUsesStandardDispatchers() {
        val p = DefaultDispatcherProvider()
        assertSame(Dispatchers.Default, p.default)
        assertSame(Dispatchers.Main, p.main)
        assertEquals("cairn", logger("cairn").tag)
    }
}
