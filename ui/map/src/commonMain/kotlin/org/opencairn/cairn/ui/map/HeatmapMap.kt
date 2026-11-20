package org.opencairn.cairn.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import org.opencairn.cairn.core.domain.model.GeoPoint
import org.opencairn.cairn.core.domain.model.HeatmapTrack
import org.opencairn.cairn.ui.design.theme.CairnColors

private val NO_PROPERTIES: JsonObject = JsonObject(emptyMap())

/**
 * Every track on one map as thin translucent lines, so roads ridden often glow brighter than
 * roads ridden once. Fits all tracks when they change.
 */
@Composable
@UiComposable
fun HeatmapMap(
    tracks: List<HeatmapTrack>,
    modifier: Modifier = Modifier,
) {
    val lines =
        remember(tracks) {
            FeatureCollection(
                tracks.map { t ->
                    Feature(LineString(t.points.map { Position(it.longitude, it.latitude) }), NO_PROPERTIES)
                },
            )
        }
    val bounds = remember(tracks) { boundsOf(tracks.flatMap { it.points }) }
    val state =
        rememberMapState(baseStyle = BaseStyle.Uri(MAP_STYLE_URL)) {
            if (tracks.isNotEmpty()) {
                LineLayer(
                    id = "heat",
                    source = rememberGeoJsonSource(GeoJsonData.Features(lines)),
                    color = const(CairnColors.teal500),
                    width = const(2.dp),
                    opacity = const(0.45f),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
        }
    LaunchedEffect(bounds) {
        bounds?.let {
            state.fitCameraToBounds(
                it.toBox(),
                CameraFit(fitPadding = DpPadding(32.dp, 32.dp, 32.dp, 32.dp)),
            )
        }
    }
    MaplibreMap(modifier = modifier.fillMaxSize(), state = state)
}

/** South-west and north-east corners of a set of points. */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/** The smallest box around [points]; null without points. */
fun boundsOf(points: List<GeoPoint>): GeoBounds? {
    if (points.isEmpty()) return null
    return GeoBounds(
        south = points.minOf { it.latitude },
        west = points.minOf { it.longitude },
        north = points.maxOf { it.latitude },
        east = points.maxOf { it.longitude },
    )
}

private fun GeoBounds.toBox() = BoundingBox(west = west, south = south, east = east, north = north)
