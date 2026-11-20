package org.opencairn.cairn.feature.activities

/** Where the detail page leads: back, the full-screen map and streams, another activity of the same route. */
data class ActivityDetailActions(
    val onBack: () -> Unit,
    val onOpenMap: () -> Unit,
    val onOpenStreams: () -> Unit,
    val onOpenActivity: (String) -> Unit,
)
