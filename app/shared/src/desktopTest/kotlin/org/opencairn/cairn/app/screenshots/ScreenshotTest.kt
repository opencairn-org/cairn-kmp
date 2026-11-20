package org.opencairn.cairn.app.screenshots

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** What every screenshot test fixes so a picture depends on the code alone. */
abstract class ScreenshotTest {
    @BeforeTest
    fun fixEnvironment() {
        // View models run on Main; resources and dates follow the JVM defaults.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.ENGLISH)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterTest fun releaseMain() = Dispatchers.resetMain()

    /** Renders [content] at phone and desktop size under the same [name]. */
    protected fun onEveryDevice(
        name: String,
        height: Int? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = Device.entries.forEach { device -> screenshot(name, device, height ?: device.height, content) }
}
