package de.wtzg.domino.feature.play

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import de.wtzg.domino.data.GameOptions
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.ui.components.CustomTargetDialog
import de.wtzg.domino.ui.components.ModeArt

/** Rules in a few lines, shown before you start. */
fun GameMode.rulesSummary(): String = when (this) {
    GameMode.DRAW -> "Match one of the open ends. If nothing fits, draw from the boneyard until something does. " +
        "Go out first to score the pips left in the other hands."
    GameMode.BLOCK -> "Like Draw, but there's no drawing: if nothing fits, you pass. " +
        "When nobody can play, the lightest hand wins."
    GameMode.ALL_FIVES -> "After each play, add up the open ends. A multiple of 5 scores that many points. " +
        "The opening double is a spinner: once both its sides are played, you can build on all four sides. " +
        "Going out scores the others' pips, rounded to the nearest 5."
    GameMode.ALL_THREES -> "Like All Fives, but every multiple of 3 scores. " +
        "Going out scores the others' pips, rounded to the nearest 3."
}

private fun GameMode.targets(): List<Int> = when (this) {
    GameMode.DRAW, GameMode.BLOCK -> listOf(60, 100, 150)
    GameMode.ALL_FIVES -> listOf(61, 100, 150, 250)
    GameMode.ALL_THREES -> listOf(60, 90, 150)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    mode: GameMode,
    defaultCoach: Boolean,
    hasGameInProgress: Boolean,
    onBack: () -> Unit,
    onStart: (Rules, GameOptions) -> Unit,
) {
    var opponents by rememberSaveable { mutableIntStateOf(1) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.NORMAL) }
    var target by rememberSaveable { mutableIntStateOf(mode.defaultTarget) }
    var coach by rememberSaveable { mutableStateOf(defaultCoach) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var customTarget by rememberSaveable { mutableStateOf(false) }

    val start = { onStart(Rules(mode, opponents + 1, target), GameOptions(difficulty, coach)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mode.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeArt(mode)
                    Text(mode.rulesSummary(), modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Label("Opponents")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { n ->
                    SegmentedButton(
                        selected = opponents == n,
                        onClick = { opponents = n },
                        shape = SegmentedButtonDefaults.itemShape(n - 1, 3),
                    ) { Text("$n") }
                }
            }
            Text(
                SEAT_NAMES.drop(1).take(opponents).joinToString(", ") + if (opponents > 1 && mode != GameMode.BLOCK) ". 5 tiles each." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Label("Difficulty")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Difficulty.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = difficulty == d,
                        onClick = { difficulty = d },
                        shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                    ) { Text(d.label) }
                }
            }
            Text(difficulty.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Label("Play to")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mode.targets().forEach { t ->
                    FilterChip(
                        selected = target == t,
                        onClick = { target = t },
                        label = { Text(if (t == mode.defaultTarget) "$t · Red Dead" else "$t") },
                    )
                }
                FilterChip(
                    selected = target !in mode.targets(),
                    onClick = { customTarget = true },
                    label = { Text(if (target in mode.targets()) "Custom" else "Custom: $target") },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Coach", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Rates every move you make and explains a better one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = coach, onCheckedChange = { coach = it })
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (hasGameInProgress) confirm = true else start() },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .height(52.dp),
            ) { Text("Start game") }
        }
    }

    if (customTarget) {
        CustomTargetDialog(target, onDismiss = { customTarget = false }) { t ->
            customTarget = false
            target = t
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start a new game?") },
            text = { Text("Your game in progress will be lost.") },
            confirmButton = { TextButton(onClick = { confirm = false; start() }) { Text("Start new") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
