package org.opencairn.cairn.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.StreamChannel
import org.opencairn.cairn.core.domain.model.Units

/**
 * All channels in one chart. Every series is scaled to its own 0–100 % range so a 250 m climb and
 * a 150 bpm heart rate share the plot; the legend above shows the real values at the cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CombinedStreamChart(
    stream: ActivityStream,
    units: Units,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    hidden: Set<StreamChannel>,
    onToggle: (StreamChannel) -> Unit,
    modifier: Modifier = Modifier,
    channels: List<StreamChannel> = stream.channels,
    height: Dp = 200.dp,
) {
    val producer = remember { CartesianChartModelProducer() }
    val visible = remember(channels, hidden) { channels.filterNot { it in hidden } }
    val normalised = remember(stream, visible) { visible.map { normalise(stream.values(it)) } }
    LaunchedEffect(normalised) {
        if (normalised.isNotEmpty()) {
            producer.runTransaction {
                lineModel { normalised.forEach { ys -> series(x = stream.timeS, y = ys) } }
            }
        }
    }
    val range = remember { CartesianLayerRangeProvider.fixed(minY = -PAD, maxY = 100.0 + PAD) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val listener = remember(stream) { cursorListener(stream) { currentOnSelect(it) } }
    // The marker shows the time only; the per-channel readout lives in the legend.
    val timeOnly =
        remember(stream) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                targets.firstOrNull()?.let { formatSeconds(it.x) }.orEmpty()
            }
        }
    val marker = rememberDefaultCartesianMarker(label = rememberTextComponent(), valueFormatter = timeOnly)
    val lines =
        visible.map { channel ->
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(channel.color())),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
            )
        }
    val timeFormatter = remember { CartesianValueFormatter { _, value, _ -> formatSeconds(value) } }
    val selected = selectedIndex?.takeIf { it in stream.timeS.indices }
    val selectedX = selected?.let { stream.timeS[it] }
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            channels.forEach { channel ->
                Legend(channel, units, selected?.let { stream.values(channel)[it] }, channel !in hidden) {
                    onToggle(channel)
                }
            }
        }
        selectedX?.let {
            Text(
                formatSeconds(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (visible.isEmpty()) return@Column
        CartesianChartHost(
            chart =
                rememberCartesianChart(
                    rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(lines), rangeProvider = range),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = timeFormatter),
                    marker = marker,
                    markerVisibilityListener = listener,
                    persistentMarkers = { if (selectedX != null) marker at selectedX },
                ),
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

/** Tapping a legend entry hides or shows its series; hidden entries are dimmed. */
@Composable
private fun Legend(
    channel: StreamChannel,
    units: Units,
    value: Double?,
    shown: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .alpha(if (shown) 1f else 0.4f)
                .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(channel.color(), CircleShape))
        Text(channel.label(units), style = MaterialTheme.typography.labelSmall, color = channel.color())
        value?.let {
            Text(
                formatValue(channel.display(it, units)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Reports the sample under the marker; the cursor stays put when the finger lifts. */
internal fun cursorListener(
    stream: ActivityStream,
    onIndex: (Int?) -> Unit,
): CartesianMarkerVisibilityListener =
    object : CartesianMarkerVisibilityListener {
        override fun onShown(
            marker: CartesianMarker,
            targets: List<CartesianMarker.Target>,
        ) = report(targets)

        override fun onUpdated(
            marker: CartesianMarker,
            targets: List<CartesianMarker.Target>,
        ) = report(targets)

        override fun onHidden(marker: CartesianMarker) = Unit

        private fun report(targets: List<CartesianMarker.Target>) {
            targets.firstOrNull()?.let { onIndex(stream.indexAt(it.x)) }
        }
    }

/** Scales to 0–100 within the series' own range; a flat series sits in the middle. */
internal fun normalise(values: List<Double>): List<Double> {
    val min = values.minOrNull() ?: return values
    val max = values.maxOrNull() ?: return values
    val span = max - min
    return if (span <= 0.0) values.map { 50.0 } else values.map { (it - min) / span * 100.0 }
}

private const val PAD = 4.0
