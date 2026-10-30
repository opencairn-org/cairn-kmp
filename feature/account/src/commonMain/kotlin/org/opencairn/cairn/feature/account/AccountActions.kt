package org.opencairn.cairn.feature.account

/** Where the account pages branch off: athlete profile, gear, records, settings, about, sign out. */
data class AccountActions(
    val onAthlete: () -> Unit,
    val onGear: () -> Unit,
    val onRecords: () -> Unit,
    val onSettings: () -> Unit,
    val onAbout: () -> Unit,
    val onSignOut: () -> Unit,
)
