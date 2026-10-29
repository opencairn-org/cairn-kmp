package org.opencairn.cairn.ui.design.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.Skateboarding
import androidx.compose.material.icons.filled.Snowboarding
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.opencairn.cairn.core.domain.model.ActivityType

// One branch per sport: a lookup table, not logic.
@Suppress("CyclomaticComplexMethod")
fun ActivityType.icon(): ImageVector =
    when (this) {
        ActivityType.RIDE -> {
            Icons.AutoMirrored.Filled.DirectionsBike
        }

        ActivityType.RUN -> {
            Icons.AutoMirrored.Filled.DirectionsRun
        }

        ActivityType.SWIM -> {
            Icons.Default.Pool
        }

        ActivityType.HIKE -> {
            Icons.Default.Hiking
        }

        ActivityType.WALK -> {
            Icons.AutoMirrored.Filled.DirectionsWalk
        }

        ActivityType.ROW -> {
            Icons.Default.Rowing
        }

        ActivityType.SKI -> {
            Icons.Default.DownhillSkiing
        }

        ActivityType.SNOWBOARD -> {
            Icons.Default.Snowboarding
        }

        ActivityType.SKATE -> {
            Icons.Default.Skateboarding
        }

        ActivityType.KAYAK, ActivityType.SUP -> {
            Icons.Default.Kayaking
        }

        ActivityType.SURF -> {
            Icons.Default.Surfing
        }

        ActivityType.GOLF -> {
            Icons.Default.GolfCourse
        }

        ActivityType.CLIMB -> {
            Icons.Default.Terrain
        }

        ActivityType.TENNIS -> {
            Icons.Default.SportsTennis
        }

        ActivityType.WORKOUT, ActivityType.ELLIPTICAL, ActivityType.WHEELCHAIR, ActivityType.UNSPECIFIED -> {
            Icons.Default.FitnessCenter
        }
    }

fun ActivityType.label(): String = wire.replaceFirstChar { it.uppercase() }

@Composable
fun SportIcon(
    type: ActivityType,
    modifier: Modifier = Modifier,
) {
    Icon(type.icon(), contentDescription = type.label(), modifier = modifier)
}
