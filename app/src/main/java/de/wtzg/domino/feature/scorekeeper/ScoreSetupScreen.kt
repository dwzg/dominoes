package de.wtzg.domino.feature.scorekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.wtzg.domino.ui.components.SwitchRow
import de.wtzg.domino.ui.components.rememberSaveableTextList

private const val MIN_PLAYERS = 2
private const val MAX_PLAYERS = 6
private val TARGET_PRESETS = listOf(60, 90, 100, 150, 250)
private val ROUND_PRESETS = listOf(7, 10, 13)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreSetupScreen(
    initialPlayers: List<String>,
    onStart: (players: List<String>, mode: ScoreMode, limit: Int, firstStarter: Int, rotateStarter: Boolean) -> Unit,
    onShowHistory: () -> Unit,
) {
    val names = rememberSaveableTextList {
        val start = initialPlayers.ifEmpty { listOf("", "") }.take(MAX_PLAYERS)
        start + List((MIN_PLAYERS - start.size).coerceAtLeast(0)) { "" }
    }
    var mode by rememberSaveable { mutableStateOf(ScoreMode.FIRST_TO_TARGET) }
    var targetText by rememberSaveable { mutableStateOf("100") }
    var roundsText by rememberSaveable { mutableStateOf("13") }
    var starter by rememberSaveable { mutableIntStateOf(0) }
    var rotate by rememberSaveable { mutableStateOf(false) }
    val playerNames = names.mapIndexed { i, n -> n.trim().ifEmpty { "Player ${i + 1}" } }

    val limitText = if (mode == ScoreMode.FIRST_TO_TARGET) targetText else roundsText
    val limit = limitText.toIntOrNull()?.takeIf { it > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Score keeper") },
                actions = {
                    IconButton(onClick = onShowHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Past games")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Players or teams", style = MaterialTheme.typography.titleMedium)
            names.forEachIndexed { i, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { names[i] = it },
                        label = { Text("Player ${i + 1}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (names.size > MIN_PLAYERS) {
                        IconButton(onClick = {
                            names.removeAt(i)
                            // Keep the same person as starter, or fall back to the first player.
                            starter = when {
                                starter == i -> 0
                                starter > i -> starter - 1
                                else -> starter
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove player")
                        }
                    }
                }
            }
            if (names.size < MAX_PLAYERS) {
                TextButton(onClick = { names.add("") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add player")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("How to win", style = MaterialTheme.typography.titleMedium)
            ModeOption(
                selected = mode == ScoreMode.FIRST_TO_TARGET,
                title = "First to reach a target score",
                subtitle = "Highest score wins (Block, Draw, All Fives, …)",
                onClick = { mode = ScoreMode.FIRST_TO_TARGET },
            )
            ModeOption(
                selected = mode == ScoreMode.LOWEST_AFTER_ROUNDS,
                title = "Lowest score after a number of rounds",
                subtitle = "Pips left in hand count against you (Mexican Train, …)",
                onClick = { mode = ScoreMode.LOWEST_AFTER_ROUNDS },
            )

            val presets = if (mode == ScoreMode.FIRST_TO_TARGET) TARGET_PRESETS else ROUND_PRESETS
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                presets.forEach { p ->
                    FilterChip(
                        selected = limitText == p.toString(),
                        onClick = {
                            if (mode == ScoreMode.FIRST_TO_TARGET) targetText = p.toString()
                            else roundsText = p.toString()
                        },
                        label = { Text(p.toString()) },
                    )
                }
            }
            OutlinedTextField(
                value = limitText,
                onValueChange = { v ->
                    val digits = v.filter(Char::isDigit).take(5)
                    if (mode == ScoreMode.FIRST_TO_TARGET) targetText = digits else roundsText = digits
                },
                label = { Text(if (mode == ScoreMode.FIRST_TO_TARGET) "Target score" else "Number of rounds") },
                singleLine = true,
                isError = limit == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text("Who starts", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                playerNames.forEachIndexed { i, n ->
                    FilterChip(selected = starter == i, onClick = { starter = i }, label = { Text(n) })
                }
            }
            SwitchRow(
                title = "Rotate every round",
                subtitle = if (rotate) "The start moves to the next player each round." else "The winner of each round starts the next one.",
                checked = rotate,
                onCheckedChange = { rotate = it },
                titleStyle = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onStart(playerNames, mode, limit ?: return@Button, starter.coerceIn(0, playerNames.lastIndex), rotate)
                },
                enabled = limit != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Start game")
            }
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
