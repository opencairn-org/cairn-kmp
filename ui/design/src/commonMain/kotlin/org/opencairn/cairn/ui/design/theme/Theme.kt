package org.opencairn.cairn.ui.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme =
    darkColorScheme(
        primary = CairnColors.teal400,
        onPrimary = CairnColors.zinc950,
        primaryContainer = CairnColors.teal900,
        onPrimaryContainer = CairnColors.teal300,
        secondary = CairnColors.zinc400,
        onSecondary = CairnColors.zinc950,
        background = CairnColors.zinc950,
        onBackground = CairnColors.zinc100,
        surface = CairnColors.zinc950,
        onSurface = CairnColors.zinc100,
        surfaceVariant = CairnColors.zinc900,
        onSurfaceVariant = CairnColors.zinc400,
        surfaceContainer = CairnColors.zinc900,
        surfaceContainerHigh = CairnColors.zinc800,
        outline = CairnColors.zinc700,
        outlineVariant = CairnColors.zinc800,
        error = CairnColors.red400,
        onError = CairnColors.zinc950,
    )

@Composable
fun CairnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = CairnTypography, content = content)
}
