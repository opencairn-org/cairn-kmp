package org.opencairn.cairn.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.ui.design.components.StatRow
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.approve_device_title
import org.opencairn.cairn.ui.design.resources.settings_about
import org.opencairn.cairn.ui.design.resources.settings_account
import org.opencairn.cairn.ui.design.resources.settings_app_version
import org.opencairn.cairn.ui.design.resources.settings_devices
import org.opencairn.cairn.ui.design.resources.settings_server
import org.opencairn.cairn.ui.design.resources.settings_title
import org.opencairn.cairn.ui.design.resources.settings_units
import org.opencairn.cairn.ui.design.resources.sign_out

/** Read-only account and server information plus sign-out; preference edits come with H4. */
@Composable
fun SettingsScreen(
    user: User?,
    account: AccountKey?,
    appVersion: String,
    onApproveDevice: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.settings_account), style = MaterialTheme.typography.titleMedium)
        user?.let {
            StatRow("Name", it.displayName)
            StatRow("Username", it.username)
            StatRow("E-mail", it.email)
            StatRow(
                stringResource(Res.string.settings_units),
                it.preferences.units.name
                    .lowercase(),
            )
        }
        Text(stringResource(Res.string.settings_server), style = MaterialTheme.typography.titleMedium)
        account?.let { StatRow("URL", it.serverBaseUrl) }
        Text(stringResource(Res.string.settings_devices), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onApproveDevice) { Text(stringResource(Res.string.approve_device_title)) }
        Text(stringResource(Res.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.settings_app_version, appVersion), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onSignOut) { Text(stringResource(Res.string.sign_out)) }
    }
}
