package de.wtzg.domino.feature.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import de.wtzg.domino.data.SavedGame
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.ui.components.ModeArt
import de.wtzg.domino.ui.components.SectionHeader
import de.wtzg.domino.ui.components.TabTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayHomeScreen(
    saved: SavedGame?,
    onContinue: () -> Unit,
    onPickMode: (GameMode) -> Unit,
    onLearn: () -> Unit,
    onOnline: () -> Unit,
    onLocal: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { TabTopBar("Dominoes", scroll) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (saved != null && !saved.match.isOver) {
                item { ContinueCard(saved, onContinue) }
            }
            item {
                Card(
                    onClick = onOnline,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, contentDescription = null)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("Play with friends", style = MaterialTheme.typography.titleMedium)
                            Text("Online, live or turn by turn. Invite with a code.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item {
                Card(
                    onClick = onLocal,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Wifi, contentDescription = null)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("Same Wi-Fi", style = MaterialTheme.typography.titleMedium)
                            Text("At one table, each on your own phone. No internet needed.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item { SectionHeader("Play against the computer") }
            items(GameMode.entries) { mode -> ModeCard(mode) { onPickMode(mode) } }
            item {
                Card(
                    onClick = onLearn,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("New to dominoes?", style = MaterialTheme.typography.titleMedium)
                        Text("Short lessons, puzzles and a coach that explains every move. Open the Learn tab.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(saved: SavedGame, onContinue: () -> Unit) {
    val m = saved.match
    Card(
        onClick = onContinue,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Continue your game", style = MaterialTheme.typography.titleMedium)
                Text("${m.rules.mode.title} · hand ${m.handNumber} · " + m.scores.mapIndexed { i, s -> "${SEAT_NAMES[i]} $s" }.joinToString(" · "))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onContinue) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Resume")
            }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeArt(mode, Modifier.widthIn(min = 64.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(mode.title, style = MaterialTheme.typography.titleMedium)
                Text(mode.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "First to ${mode.defaultTarget}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
