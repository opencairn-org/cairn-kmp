package org.opencairn.cairn.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.overview_distance
import org.opencairn.cairn.ui.design.resources.overview_elevation
import org.opencairn.cairn.ui.design.resources.overview_moving_time
import org.opencairn.cairn.ui.design.resources.overview_total_activities

/** Count, distance, moving time and elevation as two rows of tiles: the overview's and the statistics' head. */
@Composable
fun TotalsGrid(
    totals: Totals,
    formatters: Formatters,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.overview_total_activities), totals.count.toString(), Modifier.weight(1f))
            StatTile(
                stringResource(Res.string.overview_distance),
                formatters.distance(totals.distance),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                stringResource(Res.string.overview_moving_time),
                formatters.duration(totals.moving),
                Modifier.weight(1f),
            )
            StatTile(
                stringResource(Res.string.overview_elevation),
                formatters.elevation(totals.elevationGain),
                Modifier.weight(1f),
            )
        }
    }
}
