package org.opencairn.cairn.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
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
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.ui.design.theme.CairnColors

/** The web UI's basemap: OpenFreeMap "liberty", no key. */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private val NO_PROPERTIES: JsonObject = JsonObject(emptyMap())

/**
 * The activity's track on a MapLibre map: teal line with a dark casing, green start and red
 * finish dots, and a marker at [selectedIndex] (the chart cursor). Fits the track on first show.
 */
@Composable
@UiComposable
fun RouteMap(
    stream: ActivityStream,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val positions =
        remember(stream) { stream.latitude.indices.map { Position(stream.longitude[it], stream.latitude[it]) } }
    val bounds = remember(positions) { positions.bounds() }
    val state =
        rememberMapState(baseStyle = BaseStyle.Uri(MAP_STYLE_URL)) {
            if (positions.size >= 2) {
                val track = rememberGeoJsonSource(GeoJsonData.Features(Feature(LineString(positions), NO_PROPERTIES)))
                LineLayer(
                    id = "track-casing",
                    source = track,
                    color = const(CairnColors.zinc950),
                    width = const(7.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = "track",
                    source = track,
                    color = const(CairnColors.teal400),
                    width = const(4.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                val ends =
                    rememberGeoJsonSource(
                        GeoJsonData.Features(
                            FeatureCollection(
                                listOf(
                                    Feature(Point(positions.first()), buildJsonObject { put("kind", "start") }),
                                    Feature(Point(positions.last()), buildJsonObject { put("kind", "finish") }),
                                ),
                            ),
                        ),
                    )
                CircleLayer(
                    id = "finish",
                    source = ends,
                    color = const(CairnColors.red400),
                    radius = const(6.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
                CircleLayer(
                    id = "start",
                    source = ends,
                    color = const(CairnColors.green400),
                    radius = const(6.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
                val selected = selectedIndex?.coerceIn(positions.indices)
                if (selected != null) {
                    val cursor =
                        rememberGeoJsonSource(GeoJsonData.Features(Feature(Point(positions[selected]), NO_PROPERTIES)))
                    CircleLayer(
                        id = "cursor",
                        source = cursor,
                        color = const(Color.White),
                        radius = const(7.dp),
                        strokeColor = const(CairnColors.teal600),
                        strokeWidth = const(3.dp),
                    )
                }
            }
        }
    LaunchedEffect(bounds) {
        bounds?.let { state.fitCameraToBounds(it, CameraFit(fitPadding = DpPadding(24.dp, 24.dp, 24.dp, 24.dp))) }
    }
    MaplibreMap(modifier = modifier.fillMaxSize(), state = state)
}

private fun List<Position>.bounds(): BoundingBox? {
    if (isEmpty()) return null
    var west = Double.MAX_VALUE
    var south = Double.MAX_VALUE
    var east = -Double.MAX_VALUE
    var north = -Double.MAX_VALUE
    for (p in this) {
        if (p.longitude < west) west = p.longitude
        if (p.longitude > east) east = p.longitude
        if (p.latitude < south) south = p.latitude
        if (p.latitude > north) north = p.latitude
    }
    return BoundingBox(west = west, south = south, east = east, north = north)
}
