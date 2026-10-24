package de.wtzg.domino.feature.scorekeeper

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val NEW_ROUND = -1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScoreGameScreen(
    game: ScoreGame,
    onAddRound: (List<Int>) -> Unit,
    onUpdateRound: (Int, List<Int>) -> Unit,
    onDeleteRound: (Int) -> Unit,
    onUndo: () -> Unit,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onShowHistory: () -> Unit,
) {
    // null = no dialog, NEW_ROUND = adding, otherwise index of the round being edited
    var editing by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmNewGame by remember { mutableStateOf(false) }
    val totals = game.totals
    val winner = game.winnerIndex
    // Who starts the round being played; nobody once the game is over.
    val starter = if (winner == null) game.currentStarter else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (game.mode) {
                            ScoreMode.FIRST_TO_TARGET -> "First to ${game.limit}"
                            ScoreMode.LOWEST_AFTER_ROUNDS ->
                                "Round ${minOf(game.rounds.size + 1, game.limit)} of ${game.limit}"
                        }
                    )
                },
                actions = {
                    IconButton(onClick = onShowHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Past games")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Undo last round") },
                            enabled = game.rounds.isNotEmpty(),
                            onClick = { menuOpen = false; onUndo() },
                        )
                        DropdownMenuItem(
                            text = { Text("Restart with same players") },
                            onClick = { menuOpen = false; onRematch() },
                        )
                        DropdownMenuItem(
                            text = { Text("New game") },
                            onClick = {
                                menuOpen = false
                                if (game.isFinished || game.rounds.isEmpty()) onNewGame() else confirmNewGame = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (winner == null) {
                ExtendedFloatingActionButton(
                    onClick = { editing = NEW_ROUND },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add round") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (winner != null) {
                item { WinnerBanner(game.players[winner], totals[winner], onRematch, onNewGame) }
            }

            // Score cards, two per row
            val rows = game.players.indices.chunked(2)
            items(rows.size) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows[r].forEach { p ->
                        ScoreCard(
                            name = game.players[p],
                            total = totals[p],
                            game = game,
                            isLeader = p == game.leaderIndex,
                            starts = p == starter,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item {
                Text(
                    if (game.rounds.isEmpty()) "No rounds yet. Tap \"Add round\" after each hand."
                    else "Rounds (tap to edit)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (starter != null || game.rounds.isNotEmpty()) {
                // Who starts, plus names and totals, stay pinned under the top bar while
                // the rounds scroll, so the current standing is always visible.
                stickyHeader {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        starter?.let { StarterLine(game, it) }
                        if (game.rounds.isNotEmpty()) {
                            RoundRow(label = "#", values = game.players, header = true)
                            RoundRow(label = "Σ", values = totals.map { it.toString() }, header = true)
                            HorizontalDivider()
                        }
                    }
                }
            }
            if (game.rounds.isNotEmpty()) {
                itemsIndexed(game.rounds) { i, round ->
                    RoundRow(
                        label = "${i + 1}",
                        values = round.map { it.toString() },
                        modifier = Modifier.clickable { editing = i },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { index ->
        RoundDialog(
            players = game.players,
            title = if (index == NEW_ROUND) "Round ${game.rounds.size + 1}" else "Edit round ${index + 1}",
            initial = if (index == NEW_ROUND) null else game.rounds[index],
            onDismiss = { editing = null },
            onSave = { scores ->
                if (index == NEW_ROUND) onAddRound(scores) else onUpdateRound(index, scores)
                editing = null
            },
            onDelete = if (index == NEW_ROUND) null else ({ onDeleteRound(index); editing = null }),
        )
    }

    if (confirmNewGame) {
        AlertDialog(
            onDismissRequest = { confirmNewGame = false },
            title = { Text("Abandon this game?") },
            text = { Text("The current scores will be discarded.") },
            confirmButton = {
                TextButton(onClick = { confirmNewGame = false; onNewGame() }) { Text("Abandon") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewGame = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WinnerBanner(name: String, score: Int, onRematch: () -> Unit, onNewGame: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Full width, so the content is centred in the card rather than at its start.
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Star, contentDescription = null)
            Text("$name wins!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("with $score points", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNewGame) { Text("New game") }
                Button(onClick = onRematch) { Text("Rematch") }
            }
        }
    }
}

/** "Round 4: Anna starts", and how the start moves on. */
@Composable
private fun StarterLine(game: ScoreGame, starter: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column {
            Text(
                "Round ${game.rounds.size + 1}: ${game.players[starter]} starts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (game.rotateStarter) "The start moves to the next player each round."
                else "The winner of each round starts the next one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreCard(name: String, total: Int, game: ScoreGame, isLeader: Boolean, starts: Boolean, modifier: Modifier) {
    val colors = if (isLeader) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = modifier, colors = colors) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isLeader) Icon(Icons.Default.Star, contentDescription = "Leading")
            }
            if (starts) {
                Text(
                    "Starts this round",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                total.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            if (game.mode == ScoreMode.FIRST_TO_TARGET) {
                LinearProgressIndicator(
                    progress = { (total.toFloat() / game.limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val left = game.limit - total
                Text(
                    if (left > 0) "$left to go" else "Target reached",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundRow(label: String, values: List<String>, modifier: Modifier = Modifier, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = style, fontWeight = weight, modifier = Modifier.width(32.dp))
        values.forEach {
            Text(
                it,
                style = style,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoundDialog(
    players: List<String>,
    title: String,
    initial: List<Int>?,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val inputs = remember {
        mutableStateListOf<String>().apply {
            addAll(players.indices.map { i -> initial?.getOrNull(i)?.takeIf { it != 0 }?.toString() ?: "" })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                players.forEachIndexed { i, name ->
                    OutlinedTextField(
                        value = inputs[i],
                        onValueChange = { v -> inputs[i] = v.filter(Char::isDigit).take(5) },
                        label = { Text(name) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (i == players.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete this round", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(inputs.map { it.toIntOrNull() ?: 0 }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
