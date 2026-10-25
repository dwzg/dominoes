package de.wtzg.domino.feature.online

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.feature.play.GameScreen
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.Visibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(
    state: OnlineGameState,
    vm: OnlineGameActions,
    haptics: Boolean,
    onBack: () -> Unit,
    dimHandWhileWaiting: Boolean = true,
) {
    // Live updates only while the screen is visible.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.stayConnected() } }
    LaunchedEffect(state.gone) { if (state.gone) onBack() }

    var confirmLeave by remember { mutableStateOf(false) }
    val v = state.view
    val table = state.toTable()

    when {
        v == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(state.error ?: "Connecting…")
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        v.status == GameStatus.LOBBY -> Lobby(v, state, vm, onBack, onLeave = { confirmLeave = true })
        table != null -> GameScreen(
            table = table,
            actions = vm,
            haptics = haptics,
            onBack = onBack,
            dimHandWhileWaiting = dimHandWhileWaiting,
            onOverlaySecondary = onBack,
            topActions = {
                var menu by remember { mutableStateOf(false) }
                // The Box anchors the menu to the button instead of the whole top row.
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Leave game") },
                            enabled = v.status == GameStatus.PLAYING,
                            onClick = { menu = false; confirmLeave = true },
                        )
                    }
                }
            },
        )
    }

    if (confirmLeave && v != null) {
        val lobby = v.status == GameStatus.LOBBY
        val host = v.yourSeat == v.hostSeat
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (lobby && host) "Cancel this game?" else "Leave this game?") },
            text = {
                Text(
                    when {
                        lobby && host -> "Everyone who joined will be sent back."
                        lobby -> "Your seat opens up for someone else."
                        else -> "A computer player takes over your seat and the others play on."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }

    state.error?.takeIf { v != null }?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lobby(v: GameView, state: OnlineGameState, vm: OnlineGameActions, onBack: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val host = v.yourSeat == v.hostSeat
    val link = "${state.serverUrl}/join/${v.code}"
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.NORMAL) }
    val free = v.seats.count { it.playerId == null && !it.isAi }
    var editingSettings by rememberSaveable { mutableStateOf(false) }
    if (editingSettings) {
        LobbySettingsDialog(v, onDismiss = { editingSettings = false }) { req ->
            editingSettings = false
            vm.updateSettings(req)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game lobby") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Game code", style = MaterialTheme.typography.labelLarge)
                    Text(v.code, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                    // Friends nearby can scan this instead of typing the code.
                    QrCode(
                        text = link,
                        description = "QR code to join game ${v.code}",
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text("Scan with the Dominoes app or your camera", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${v.rules.mode.title} · to ${v.rules.target} · ${if (v.pace == Pace.LIVE) "live" else "turn by turn"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (v.visibility == Visibility.PUBLIC) "Open to anyone · listed under Open games" else "Invite only",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (host && state.lobbySettings) {
                        TextButton(onClick = { editingSettings = true }, enabled = !state.busy) { Text("Change settings") }
                    }
                    Button(
                        onClick = {
                            val text = "Join my dominoes game! Open the Dominoes app and enter code ${v.code}, or tap: $link"
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, "Invite friends"))
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("  Invite friends")
                    }
                }
            }

            Column(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("At the table", style = MaterialTheme.typography.titleMedium)
                v.seats.forEachIndexed { i, s ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        if (s.online) Color(0xFF43A047) else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    ),
                            )
                            Text(
                                when {
                                    s.playerId == null -> "Open seat"
                                    i == v.yourSeat -> "${s.name} (you)"
                                    else -> s.name
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (s.playerId == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            if (i == v.hostSeat) Text("Host", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (host && state.publicGames) {
                // Off the list once the seats are full or the game starts, so only offer it while it matters.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .toggleable(
                            value = v.visibility == Visibility.PUBLIC,
                            enabled = !state.busy,
                            role = Role.Switch,
                            onValueChange = { on -> vm.setVisibility(if (on) Visibility.PUBLIC else Visibility.PRIVATE) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Open to anyone", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "List this game under Open games so players on this server can join without a code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = v.visibility == Visibility.PUBLIC, onCheckedChange = null, enabled = !state.busy)
                }
            }

            Column(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (host) {
                    if (free > 0) {
                        Text("Computer players for open seats", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Difficulty.entries.forEachIndexed { i, d ->
                                SegmentedButton(
                                    selected = difficulty == d,
                                    onClick = { difficulty = d },
                                    shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                                ) { Text(d.label) }
                            }
                        }
                    }
                    Button(
                        onClick = { vm.start(difficulty) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            when (free) {
                                0 -> "Start game"
                                1 -> "Start with 1 computer player"
                                else -> "Start with $free computer players"
                            },
                        )
                    }
                } else {
                    Text(
                        "Waiting for ${v.seats[v.hostSeat].name} to start the game…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                    Text(if (host) "Cancel game" else "Leave")
                }
            }
        }
    }
}

/** The host changes the game before it starts; starts from the current settings. */
@Composable
private fun LobbySettingsDialog(v: GameView, onDismiss: () -> Unit, onSave: (SettingsRequest) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(v.rules.mode) }
    var players by rememberSaveable { mutableIntStateOf(v.rules.players) }
    var target by rememberSaveable { mutableIntStateOf(v.rules.target) }
    var pace by rememberSaveable { mutableStateOf(v.pace) }
    val seated = v.seats.count { it.playerId != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GameSettingsFields(
                    mode = mode,
                    players = players,
                    target = target,
                    pace = pace,
                    onMode = { mode = it; target = it.defaultTarget },
                    onPlayers = { players = it },
                    onTarget = { target = it },
                    onPace = { pace = it },
                    minPlayers = seated.coerceAtLeast(2),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(SettingsRequest(mode, players, target, pace)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

