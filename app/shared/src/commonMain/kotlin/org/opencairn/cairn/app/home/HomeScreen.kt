package org.opencairn.cairn.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.sign_out
import org.opencairn.cairn.ui.design.resources.signed_in_as

/** Placeholder until the overview feature lands in Phase 2. */
@Composable
fun HomeScreen(
    user: User?,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(Res.string.signed_in_as, user?.displayName ?: ""),
            style = MaterialTheme.typography.titleLarge,
        )
        user?.let { Text("${it.username} · ${it.email}", style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = onSignOut) { Text(stringResource(Res.string.sign_out)) }
    }
}
