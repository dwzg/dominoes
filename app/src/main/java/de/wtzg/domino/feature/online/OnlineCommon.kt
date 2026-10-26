package de.wtzg.domino.feature.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** How often lists of games and friends are updated while you look at them. */
const val LIST_REFRESH_MS = 30_000L

/**
 * Calls [refresh] every [LIST_REFRESH_MS] while the screen is visible, and again whenever
 * you come back to it, but never while the app is in the background.
 */
@Composable
fun RefreshWhileVisible(enabled: Boolean, refresh: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(refresh)
    LaunchedEffect(lifecycle, enabled) {
        if (!enabled) return@LaunchedEffect
        var first = true
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // The screen loads by itself when it opens; after that, catch up on every return.
            if (!first) current()
            first = false
            while (true) {
                delay(LIST_REFRESH_MS)
                current()
            }
        }
    }
}

/** A row in a list of people or games: what it is on the left, what you can do on the right. */
@Composable
fun ListCard(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.let {
                it()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
