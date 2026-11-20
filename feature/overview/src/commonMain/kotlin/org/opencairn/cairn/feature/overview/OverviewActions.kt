package org.opencairn.cairn.feature.overview

import org.opencairn.cairn.core.domain.model.AppRelease

/** Where the overview leads: an activity of the feed, the connections behind the sync line, a new release. */
data class OverviewActions(
    val onOpenActivity: (String) -> Unit,
    val onOpenConnections: () -> Unit,
    val onOpenUpdate: (AppRelease) -> Unit = {},
    val onDismissUpdate: (AppRelease) -> Unit = {},
)
