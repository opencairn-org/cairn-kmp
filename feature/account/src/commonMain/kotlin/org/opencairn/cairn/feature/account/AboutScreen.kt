package org.opencairn.cairn.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.ui.design.components.StatRow
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.about_license
import org.opencairn.cairn.ui.design.resources.about_releases
import org.opencairn.cairn.ui.design.resources.about_server
import org.opencairn.cairn.ui.design.resources.about_server_version
import org.opencairn.cairn.ui.design.resources.about_source
import org.opencairn.cairn.ui.design.resources.about_tagline
import org.opencairn.cairn.ui.design.resources.about_title
import org.opencairn.cairn.ui.design.resources.about_website
import org.opencairn.cairn.ui.design.resources.app_icon
import org.opencairn.cairn.ui.design.resources.app_name
import org.opencairn.cairn.ui.design.resources.settings_app_version

private const val WEBSITE = "https://opencairn.org"
private const val SOURCE = "https://github.com/opencairn-org/cairn-kmp"
private const val RELEASES = "https://github.com/opencairn-org/cairn-kmp/releases"

/** App and server versions, the licence, and the project links. */
@Composable
fun AboutScreen(
    viewModel: SettingsViewModel,
    appVersion: String,
    account: AccountKey?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uris = LocalUriHandler.current
    SubPage(title = stringResource(Res.string.about_title), onBack = onBack) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
            )
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(Res.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.settings_app_version, appVersion),
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                account?.let { StatRow(stringResource(Res.string.about_server), it.serverBaseUrl) }
                state.server?.version?.let { StatRow("", stringResource(Res.string.about_server_version, it)) }
            }
            Text(
                stringResource(Res.string.about_license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = { uris.openUri(WEBSITE) }) { Text(stringResource(Res.string.about_website)) }
                OutlinedButton(onClick = { uris.openUri(SOURCE) }) { Text(stringResource(Res.string.about_source)) }
                OutlinedButton(onClick = { uris.openUri(RELEASES) }) { Text(stringResource(Res.string.about_releases)) }
            }
        }
    }
}
