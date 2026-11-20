package org.opencairn.cairn.feature.account

/** Where the account pages branch off: notifications, athlete profile, gear, records, connections, … */
data class AccountActions(
    val onNotifications: () -> Unit,
    val onAthlete: () -> Unit,
    val onGear: () -> Unit,
    val onRecords: () -> Unit,
    val onConnections: () -> Unit,
    val onSettings: () -> Unit,
    val onAbout: () -> Unit,
    val onSignOut: () -> Unit,
)
