package de.wtzg.domino.feature.online

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.feature.play.rulesSummary
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.Visibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineCreateScreen(
    busy: Boolean,
    /** The server can list public games; otherwise every game is invite only. */
    allowPublic: Boolean,
    onBack: () -> Unit,
    onCreate: (CreateGameRequest) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(GameMode.ALL_FIVES) }
    var players by rememberSaveable { mutableIntStateOf(2) }
    var target by rememberSaveable { mutableIntStateOf(GameMode.ALL_FIVES.defaultTarget) }
    var pace by rememberSaveable { mutableStateOf(Pace.LIVE) }
    var visibility by rememberSaveable { mutableStateOf(Visibility.PRIVATE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New online game") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
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
            GameSettingsFields(
                mode = mode,
                players = players,
                target = target,
                pace = pace,
                onMode = { mode = it; target = it.defaultTarget },
                onPlayers = { players = it },
                onTarget = { target = it },
                onPace = { pace = it },
            )

            if (allowPublic) {
                Label("Who can join")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = visibility == Visibility.PRIVATE,
                        onClick = { visibility = Visibility.PRIVATE },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Invite only") }
                    SegmentedButton(
                        selected = visibility == Visibility.PUBLIC,
                        onClick = { visibility = Visibility.PUBLIC },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Anyone") }
                }
                Text(
                    if (visibility == Visibility.PRIVATE) "Only people you send the code, link or QR code to can join."
                    else "Listed under Open games for everyone on this server until the seats are full. You can still invite friends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    val v = if (allowPublic) visibility else Visibility.PRIVATE
                    onCreate(CreateGameRequest(mode, players, target, pace, v))
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(52.dp),
            ) { Text(if (allowPublic && visibility == Visibility.PUBLIC) "Create open game" else "Create and invite friends") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

/**
 * The game, table size, target and pace, as chosen when creating a game and when the host
 * changes them in the lobby. [minPlayers] keeps the table from shrinking below the people seated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsFields(
    mode: GameMode,
    players: Int,
    target: Int,
    pace: Pace,
    onMode: (GameMode) -> Unit,
    onPlayers: (Int) -> Unit,
    onTarget: (Int) -> Unit,
    onPace: (Pace) -> Unit,
    minPlayers: Int = 2,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("Game")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onMode(m) },
                    label = { Text(m.title) },
                )
            }
        }
        Text(mode.rulesSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Label("Players at the table")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (2..4).forEachIndexed { i, n ->
                SegmentedButton(
                    selected = players == n,
                    onClick = { onPlayers(n) },
                    enabled = n >= minPlayers,
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text("$n") }
            }
        }
        Text(
            if (minPlayers > 2) "$minPlayers people are already seated, so the table can't be smaller. " +
                "Seats nobody takes are filled with computer players when you start."
            else "Seats nobody takes are filled with computer players when you start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Label("Play to")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(mode.defaultTarget) + listOf(60, 100, 150).filter { it != mode.defaultTarget }).forEach { t ->
                FilterChip(
                    selected = target == t,
                    onClick = { onTarget(t) },
                    label = { Text(if (t == mode.defaultTarget) "$t · Red Dead" else "$t") },
                )
            }
        }

        Label("Pace")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = pace == Pace.LIVE, onClick = { onPace(Pace.LIVE) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Live")
            }
            SegmentedButton(
                selected = pace == Pace.TURN_BASED,
                onClick = { onPace(Pace.TURN_BASED) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Turn by turn") }
        }
        Text(
            if (pace == Pace.LIVE) "Everyone plays at the same time, like sitting at one table."
            else "Take your turn whenever you like. The app lets you know when it's your move.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
