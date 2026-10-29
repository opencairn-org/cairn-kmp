package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.ui.design.components.SportIcon
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.action_back

/**
 * The header every activity sub-page shares: back, sport icon, title and an optional page name.
 * The bar is compact, sits directly under the status bar and, given a [scrollBehavior], slides
 * away while the content scrolls so the page keeps the room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityPage(
    activity: Activity?,
    onBack: () -> Unit,
    subtitle: StringResource? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
    Scaffold(
        modifier = scroll,
        // The shell pads the navigation bar; the status bar is the top bar's own inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                expandedHeight = BAR_HEIGHT,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        activity?.let { SportIcon(it.type, Modifier.size(22.dp).padding(end = 2.dp)) }
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(
                                activity?.displayTitle.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            subtitle?.let {
                                Text(
                                    stringResource(it),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
        },
        content = content,
    )
}

private val BAR_HEIGHT = 48.dp
