package org.opencairn.cairn.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.StreamChannel
import org.opencairn.cairn.core.domain.model.Units
import org.opencairn.cairn.ui.design.theme.CairnColors

/** Series colors as on the web (HR red, power purple, cadence amber, speed blue, elevation grey). */
fun StreamChannel.color(): Color =
    when (this) {
        StreamChannel.HEART_RATE -> CairnColors.red400
        StreamChannel.POWER -> Color(0xFFA78BFA)
        StreamChannel.CADENCE -> CairnColors.amber400
        StreamChannel.SPEED -> Color(0xFF60A5FA)
        StreamChannel.ELEVATION -> CairnColors.zinc400
        StreamChannel.TEMPERATURE -> Color(0xFFF472B6)
    }

fun StreamChannel.label(units: Units): String =
    when (this) {
        StreamChannel.HEART_RATE -> "Heart rate (bpm)"
        StreamChannel.POWER -> "Power (W)"
        StreamChannel.CADENCE -> "Cadence (rpm)"
        StreamChannel.SPEED -> if (units == Units.IMPERIAL) "Speed (mph)" else "Speed (km/h)"
        StreamChannel.ELEVATION -> if (units == Units.IMPERIAL) "Elevation (ft)" else "Elevation (m)"
        StreamChannel.TEMPERATURE -> if (units == Units.IMPERIAL) "Temperature (°F)" else "Temperature (°C)"
    }

/** Display conversion per channel; x stays in seconds. */
fun StreamChannel.display(
    value: Double,
    units: Units,
): Double =
    when (this) {
        StreamChannel.SPEED -> if (units == Units.IMPERIAL) value * 2.2369362921 else value * 3.6
        StreamChannel.ELEVATION -> if (units == Units.IMPERIAL) value * 3.280839895 else value
        StreamChannel.TEMPERATURE -> if (units == Units.IMPERIAL) value * 9 / 5 + 32 else value
        else -> value
    }

/**
 * One channel of a stream as a line chart over elapsed seconds. Dragging shows a marker and
 * reports the sample index through [onSelect]; [selectedIndex] from elsewhere (the map) is drawn
 * as a persistent marker, which is how the chart and the map stay in sync.
 */
@Composable
fun StreamChart(
    stream: ActivityStream,
    channel: StreamChannel,
    units: Units,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val producer = remember { CartesianChartModelProducer() }
    val ys = remember(stream, channel, units) { stream.values(channel).map { channel.display(it, units) } }
    LaunchedEffect(ys) {
        producer.runTransaction { lineModel { series(x = stream.timeS, y = ys) } }
    }
    // Each channel gets its own y range around its data (a 240–400 m climb must not start at 0).
    val range =
        remember(ys) {
            val min = ys.minOrNull() ?: 0.0
            val max = ys.maxOrNull() ?: 1.0
            val pad = ((max - min) * 0.08).coerceAtLeast(1.0)
            CartesianLayerRangeProvider.fixed(minY = min - pad, maxY = max + pad)
        }
    val color = channel.color()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val listener =
        remember(stream) {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>,
                ) = report(targets)

                override fun onUpdated(
                    marker: CartesianMarker,
                    targets: List<CartesianMarker.Target>,
                ) = report(targets)

                // The cursor stays where the finger lifted so the map marker and the readout remain visible.
                override fun onHidden(marker: CartesianMarker) = Unit

                private fun report(targets: List<CartesianMarker.Target>) {
                    targets.firstOrNull()?.let { currentOnSelect(stream.indexAt(it.x)) }
                }
            }
        }
    val marker = rememberDefaultCartesianMarker(label = rememberTextComponent())
    val line =
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.18f))),
        )
    val timeFormatter = remember { CartesianValueFormatter { _, value, _ -> formatSeconds(value) } }
    val selectedX = selectedIndex?.takeIf { it in stream.timeS.indices }?.let { stream.timeS[it] }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(channel.label(units), style = MaterialTheme.typography.labelMedium, color = color)
            selectedIndex?.takeIf { it in stream.timeS.indices }?.let { i ->
                val v = channel.display(stream.values(channel)[i], units)
                Text(
                    "  ${formatValue(v)} @ ${formatSeconds(stream.timeS[i])}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CartesianChartHost(
            chart =
                rememberCartesianChart(
                    rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(line), rangeProvider = range),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = timeFormatter),
                    marker = marker,
                    markerVisibilityListener = listener,
                    persistentMarkers = { if (selectedX != null) marker at selectedX },
                ),
            modelProducer = producer,
            // The whole activity fits the width; no scrolling or pinch zoom (the web's charts behave the same).
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

/** Every channel of the stream, stacked, sharing one cursor. */
@Composable
fun StreamCharts(
    stream: ActivityStream,
    units: Units,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    channels: List<StreamChannel> = stream.channels,
    height: Dp = 140.dp,
) {
    Column(modifier = modifier) {
        channels.forEach { channel ->
            StreamChart(stream, channel, units, selectedIndex, onSelect, height = height)
        }
    }
}

internal fun formatSeconds(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >
        0
    ) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}

internal fun formatValue(v: Double): String =
    if (v >=
        100
    ) {
        v.toLong().toString()
    } else {
        ((v * 10).toLong() / 10.0).toString()
    }
