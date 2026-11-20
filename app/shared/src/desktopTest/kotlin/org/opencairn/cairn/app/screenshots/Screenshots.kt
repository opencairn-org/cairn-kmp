package org.opencairn.cairn.app.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import org.opencairn.cairn.ui.design.theme.CairnTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.fail

/** The two shapes every screen is rendered at: a phone in portrait and a desktop window. */
enum class Device(
    val width: Int,
    val height: Int,
) {
    PHONE(412, 892),
    DESKTOP(1280, 800),
}

/**
 * Renders [content] off-screen in the app theme at [device] size ([height] stretches the window
 * for pages that scroll, so the whole page is in the picture) and compares it with the
 * reference image `src/desktopTest/screenshots/<name>_<device>.png`. With `-Pcairn.screenshots=record`
 * (`make screenshots`) the reference is rewritten instead. A missing reference fails the test:
 * new screens are recorded on purpose and reviewed like code.
 */
@OptIn(ExperimentalTestApi::class)
fun screenshot(
    name: String,
    device: Device,
    height: Int = device.height,
    content: @Composable () -> Unit,
) {
    runDesktopComposeUiTest(width = device.width, height = height) {
        setContent { Frame(content) }
        waitForIdle()
        References.check("${name}_${device.name.lowercase()}", captureToImage().toAwtImage())
    }
}

/** The system font differs between machines; the bundled one makes text render the same everywhere. */
private val testFont = FontFamily(Font(resource = "fonts/OpenSans-Regular.ttf"))

@Composable
private fun Frame(content: @Composable () -> Unit) {
    CairnTheme {
        MaterialTheme(typography = MaterialTheme.typography.with(testFont)) {
            Surface(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

private fun Typography.with(family: FontFamily) =
    Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )

/** Reference images on disk and the comparison against them. */
internal object References {
    private val directory = File("src/desktopTest/screenshots")
    private val failures = File("build/reports/screenshots")
    private val record = System.getProperty("cairn.screenshots") == "record"

    /** A channel may be off by this much before a pixel counts as different (anti-aliasing noise). */
    private const val CHANNEL_TOLERANCE = 12

    /** Share of pixels that may differ before the image counts as changed. */
    private const val PIXEL_TOLERANCE = 0.001

    fun check(
        name: String,
        actual: BufferedImage,
    ) {
        val reference = File(directory, "$name.png")
        if (record) {
            directory.mkdirs()
            ImageIO.write(actual, "png", reference)
            return
        }
        if (!reference.exists()) {
            fail("no reference image ${reference.path}; record it with `make screenshots` and review the result")
        }
        val expected = ImageIO.read(reference)
        val differing = differingPixels(expected, actual)
        if (differing == null || differing > PIXEL_TOLERANCE * actual.width * actual.height) {
            failures.mkdirs()
            val written = File(failures, "$name.actual.png")
            ImageIO.write(actual, "png", written)
            val what = differing?.let { "$it pixels differ" } ?: "the size changed"
            fail("$name does not match its reference ($what); see ${written.path}, or `make screenshots` if intended")
        }
    }

    /** How many pixels differ beyond the tolerance; null when the sizes do not match. */
    private fun differingPixels(
        a: BufferedImage,
        b: BufferedImage,
    ): Int? {
        if (a.width != b.width || a.height != b.height) return null
        var count = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (!close(a.getRGB(x, y), b.getRGB(x, y))) count++
            }
        }
        return count
    }

    private fun close(
        p: Int,
        q: Int,
    ): Boolean =
        listOf(16, 8, 0).all { shift -> abs(((p shr shift) and 0xFF) - ((q shr shift) and 0xFF)) <= CHANNEL_TOLERANCE }
}
