package org.opencairn.cairn.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * One bar per effort, oldest left, the best one highlighted; like the web's record history.
 * With [lowerIsBetter] (pace) the bars are drawn so the best is the tallest.
 */
@Composable
fun EffortBars(
    values: List<Double>,
    best: Int,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    val bestColor = MaterialTheme.colorScheme.primary
    val gap = 2.dp
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.max()
        val min = values.min()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val slot = size.width / values.size
        val gapPx = gap.toPx().coerceAtMost(slot / 4)
        values.forEachIndexed { i, v ->
            // Relative height between 15 % and 100 % so the smallest bar stays visible.
            val rel = if (lowerIsBetter) (max - v) / span else (v - min) / span
            val h = size.height * (0.15f + 0.85f * rel.toFloat())
            drawRect(
                color = if (i == best) bestColor else barColor,
                topLeft = Offset(i * slot + gapPx / 2, size.height - h),
                size = Size(slot - gapPx, h),
            )
        }
    }
}
