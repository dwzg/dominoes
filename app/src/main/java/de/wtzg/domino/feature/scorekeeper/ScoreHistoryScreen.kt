package de.wtzg.domino.feature.scorekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreHistoryScreen(
    games: List<ScoreGame>,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Deleted games stay hidden here until the Undo snackbar is gone; only then are they
    // really deleted. Leaving the screen early deletes them too.
    var pending by remember { mutableStateOf(emptySet<Long>()) }
    val shown = games.filterNot { it.id in pending }
    val delete = { id: Long ->
        pending = pending + id
        // One snackbar at a time: dismissing the previous one deletes its game.
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            var undone = false
            try {
                undone = snackbar.showSnackbar("Game deleted", actionLabel = "Undo", duration = SnackbarDuration.Short) ==
                    SnackbarResult.ActionPerformed
            } finally {
                if (undone) pending = pending - id else onDelete(id)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Past games") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (shown.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                    }
                },
            )
        },
    ) { padding ->
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Finished games will show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { WinsSummary(shown) }
                items(shown, key = { it.id }) { game -> HistoryCard(game, dateFormat, onDelete = { delete(game.id) }) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all past games?") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WinsSummary(games: List<ScoreGame>) {
    val wins = games.mapNotNull { g -> g.winnerIndex?.let { g.players[it] } }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
    if (wins.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Wins", style = MaterialTheme.typography.titleMedium)
            wins.forEach { (name, count) ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text("$count", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(game: ScoreGame, dateFormat: DateFormat, onDelete: () -> Unit) {
    val totals = game.totals
    val winner = game.winnerIndex
    val date = dateFormat.format(Date(game.finishedAt ?: game.startedAt))
    val rule = when (game.mode) {
        ScoreMode.FIRST_TO_TARGET -> "First to ${game.limit}"
        ScoreMode.LOWEST_AFTER_ROUNDS -> "Lowest after ${game.limit} rounds"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$date · $rule · ${game.rounds.size} rounds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                game.players.forEachIndexed { i, name ->
                    val isWinner = i == winner
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            if (isWinner) "★ $name" else name,
                            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            totals[i].toString(),
                            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete game")
            }
        }
    }
}
