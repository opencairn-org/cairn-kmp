package org.opencairn.cairn.feature.overview

/** Where the overview leads: an activity of the feed, the connections behind the sync line. */
data class OverviewActions(
    val onOpenActivity: (String) -> Unit,
    val onOpenConnections: () -> Unit,
)
