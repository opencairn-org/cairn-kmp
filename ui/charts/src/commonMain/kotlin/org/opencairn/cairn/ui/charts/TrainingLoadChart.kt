package org.opencairn.cairn.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Fitness, fatigue and form over time on one scale, with a zero line (form swings around it).
 * Tapping or dragging moves a crosshair and reports the day through [onSelect]; the caller shows
 * the values of the [selected] day. The y range is rounded outward to tens and printed at the edge.
 */
@Composable
fun TrainingLoadChart(
    days: List<TrainingLoadDay>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val values = days.flatMap { d -> LoadSeries.entries.map { it.value(d) } }
    val low = floor(((values.minOrNull() ?: 0.0).coerceAtMost(0.0)) / 10) * 10
    val high = ceil(((values.maxOrNull() ?: 10.0).coerceAtLeast(10.0)) / 10) * 10
    val grid = MaterialTheme.colorScheme.outlineVariant
    val cursor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnSelect by rememberUpdatedState(onSelect)
    val count = days.size
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(count) {
                        detectTapGestures { currentOnSelect(indexAt(it.x, size.width, count)) }
                    }.pointerInput(count) {
                        detectDragGestures {
                            change,
                            _,
                            ->
                            currentOnSelect(indexAt(change.position.x, size.width, count))
                        }
                    },
        ) {
            if (count < 2) return@Canvas
            val zeroY = y(0.0, low, high)
            drawLine(grid, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            LoadSeries.entries.forEach { series ->
                val path = Path()
                days.forEachIndexed { i, day ->
                    val px = size.width * i / (count - 1)
                    val py = y(series.value(day), low, high)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path,
                    series.color,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            selected?.takeIf { it in days.indices }?.let { i ->
                val px = size.width * i / (count - 1)
                drawLine(cursor, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.dp.toPx())
                LoadSeries.entries.forEach { series ->
                    drawCircle(series.color, 4.dp.toPx(), Offset(px, y(series.value(days[i]), low, high)))
                }
            }
        }
        // The scale's ends, printed inside the plot so the lines keep the full width.
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            ScaleLabel(high.roundToInt().toString())
            ScaleLabel(low.roundToInt().toString())
        }
    }
}

@Composable
private fun ScaleLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A coloured dot with the series name and, when given, its value: the chart's legend and readout. */
@Composable
fun LoadLegendItem(
    series: LoadSeries,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(series.color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun DrawScope.y(
    value: Double,
    low: Double,
    high: Double,
): Float = (size.height * (1 - (value - low) / (high - low))).toFloat()

/** The sample under horizontal position [x] of a plot [width] pixels wide. */
internal fun indexAt(
    x: Float,
    width: Int,
    count: Int,
): Int? {
    if (count == 0 || width <= 0) return null
    return (x / width * (count - 1)).roundToInt().coerceIn(0, count - 1)
}
