package org.opencairn.cairn.feature.login

/** Callbacks of the login screen, grouped so the composables stay readable. */
data class LoginActions(
    val onServerInput: (String) -> Unit,
    val onDiscover: () -> Unit,
    val onBrowser: () -> Unit,
    val onCode: () -> Unit,
    val onCancelCode: () -> Unit,
    val onToggleToken: () -> Unit,
    val onTokenInput: (String) -> Unit,
    val onTokenSignIn: () -> Unit,
    val onChangeServer: () -> Unit,
)
