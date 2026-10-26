package de.wtzg.domino.feature.online

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.wtzg.domino.protocol.GameInvite
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.ui.components.ErrorDialog
import de.wtzg.domino.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineHomeScreen(
    state: OnlineHomeState,
    vm: OnlineHomeActions,
    initialCode: String?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenGame: (String) -> Unit,
    onFriends: () -> Unit = {},
) {
    LaunchedEffect(state.open) { state.open?.let { onOpenGame(it); vm.opened() } }
    val reg = state.account?.registration
    RefreshWhileVisible(enabled = reg != null, refresh = vm::refresh)
    // Turn-based games and friends' invites both arrive as notifications.
    val hasFriends = state.friends?.let { it.friends.isNotEmpty() || it.requests.isNotEmpty() } == true
    NotificationsPermission(reg != null && (state.games.any { it.pace == Pace.TURN_BASED } || hasFriends))
    // Invite links: join as soon as we know who you are.
    var joinedFromLink by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialCode, reg) {
        if (initialCode != null && reg != null && !joinedFromLink) {
            joinedFromLink = true
            vm.join(initialCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Play with friends") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (reg != null) {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    }
                },
            )
        },
    ) { padding ->
        val account = state.account
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                account == null -> item { CircularProgressIndicator() }
                account.serverUrl.isBlank() -> item { ServerSetup(state.busy, vm::setServer) }
                reg == null -> item { ChooseName(account.serverUrl, state.busy, vm::register, vm::setServer) }
                else -> {
                    state.compatibility?.let { msg ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(msg, modifier = Modifier.padding(16.dp)) }
                        }
                    }
                    state.offline?.let { msg ->
                        item { Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    }
                    item { Identity(reg.name, vm::rename) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onCreate, modifier = Modifier.weight(1f)) { Text("Create a game") }
                            if (state.friendsAvailable) {
                                val requests = state.friends?.requests?.size ?: 0
                                OutlinedButton(
                                    onClick = onFriends,
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics(mergeDescendants = true) {
                                            if (requests > 0) contentDescription = "Friends, $requests ${if (requests == 1) "request" else "requests"}"
                                        },
                                ) {
                                    BadgedBox(badge = { if (requests > 0) Badge { Text("$requests") } }) {
                                        Icon(Icons.Outlined.People, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing + if (requests > 0) 4.dp else 0.dp))
                                    Text("Friends")
                                }
                            }
                        }
                    }
                    if (state.invites.isNotEmpty()) {
                        item { SectionHeader("Invites") }
                        items(state.invites, key = { "invite-${it.gameId}" }) { i ->
                            InviteRow(i, state.busy, onJoin = { vm.join(i.code) }, onDecline = { vm.declineInvite(i.gameId) })
                        }
                    }
                    item { JoinWithCode(initialCode.orEmpty(), state.busy, vm::join) }
                    if (state.publicGames) {
                        item { SectionHeader("Open games") }
                        if (state.openGames.isEmpty()) {
                            item {
                                Text(
                                    "No open games right now. Create one open to anyone, and other players can join it from here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.openGames, key = { "open-${it.id}" }) { g -> OpenGameRow(g, state.busy) { vm.join(g.code) } }
                    }
                    item { SectionHeader("Your games") }
                    if (state.games.isEmpty()) {
                        item {
                            Text(
                                if (state.loading) "Loading…" else "No games yet. Create one and share the code with friends.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.games, key = { it.id }) { g -> GameRow(g) { onOpenGame(g.id) } }
                }
            }
        }
    }

    state.error?.let { ErrorDialog(it, vm::dismissError) }
}

@Composable
private fun ServerSetup(busy: Boolean, onSave: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connect to a game server", style = MaterialTheme.typography.titleMedium)
            Text(
                "Online games run on a small server that you or a friend hosts (see the project's README). " +
                    "Enter its address to start.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address") },
                placeholder = { Text("dominoes.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onSave(url) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSave(url) }, enabled = url.isNotBlank() && !busy) { Text("Save") }
        }
    }
}

@Composable
private fun ChooseName(server: String, busy: Boolean, onRegister: (String) -> Unit, onServer: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What should friends call you?", style = MaterialTheme.typography.titleMedium)
            Text("No account or password needed. Your name is shown at the table.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Limits.NAME_MAX) },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onRegister(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onRegister(name) }, enabled = name.isNotBlank() && !busy) { Text("Continue") }
                Spacer(Modifier.width(8.dp))
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text("Server: $server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onServer("") }) { Text("Use a different server") }
        }
    }
}

@Composable
private fun Identity(name: String, onRename: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Playing as ", style = MaterialTheme.typography.bodyLarge)
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = { editing = true }) { Text("Change name") }
    }
    if (editing) {
        var value by rememberSaveable { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(value = value, onValueChange = { value = it.take(Limits.NAME_MAX) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { editing = false; onRename(value) }, enabled = value.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun JoinWithCode(initial: String, busy: Boolean, onJoin: (String) -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf(initial) }
    var scanProblem by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(Limits.CODE_LENGTH) },
                label = { Text("Game code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (code.length == Limits.CODE_LENGTH) onJoin(code) }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onJoin(code) }, enabled = code.length == Limits.CODE_LENGTH && !busy) { Text("Join") }
            IconButton(
                onClick = {
                    scanProblem = null
                    scanInviteQr(
                        context,
                        onCode = { scanned -> code = scanned; onJoin(scanned) },
                        onNotAnInvite = { scanProblem = "That QR code isn't a dominoes invite." },
                        onUnavailable = { scanProblem = "The QR scanner isn't available on this phone. Type the code instead." },
                        onLocalGame = { scanProblem = "That game is hosted on a phone on the same Wi-Fi. Join it from Play, under \"Same Wi-Fi\"." },
                    )
                },
                enabled = !busy,
            ) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR code") }
        }
        scanProblem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Turn-based games and invites need notifications; ask once they matter (Android 13+), and
 * only if not allowed yet. Asked at most once per visit; Android stops showing the prompt
 * by itself after it's been turned down twice.
 */
@Composable
private fun NotificationsPermission(needed: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(needed) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (needed && !asked && !granted) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** A friend asked you to their table. */
@Composable
private fun InviteRow(i: GameInvite, busy: Boolean, onJoin: () -> Unit, onDecline: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Text("${i.from} invites you", style = MaterialTheme.typography.titleMedium)
            val free = i.seats - i.taken
            Text(
                "${i.mode.title} · to ${i.target} · ${if (i.pace == Pace.LIVE) "live" else "turn by turn"} · " +
                    "$free of ${i.seats} seats free",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDecline, enabled = !busy) { Text("Decline") }
                Button(onClick = onJoin, enabled = !busy) { Text("Join") }
            }
        }
    }
}

/** A public lobby: who's hosting, what they're playing, and how many seats are left. */
@Composable
private fun OpenGameRow(g: OpenGame, busy: Boolean, onJoin: () -> Unit) {
    ListCard(
        title = "${g.host}'s table",
        note = "${g.mode.title} · to ${g.target} · ${g.seats - g.taken} of ${g.seats} seats free",
        leading = { PaceIcon(g.pace) },
    ) { Button(onClick = onJoin, enabled = !busy) { Text("Join") } }
}

@Composable
private fun PaceIcon(pace: Pace) = Icon(
    if (pace == Pace.LIVE) Icons.Outlined.Bolt else Icons.Outlined.HourglassEmpty,
    contentDescription = if (pace == Pace.LIVE) "Live" else "Turn by turn",
)

/** What the server calls a seat nobody has taken yet in "your games". */
private const val OPEN_SEAT = "Open seat"

@Composable
private fun GameRow(g: GameSummary, onClick: () -> Unit) {
    val (label, highlight) = when {
        g.status == GameStatus.LOBBY -> "Lobby · ${g.code}" to false
        g.isYourTurn -> "Your turn" to true
        g.status == GameStatus.PLAYING -> "${g.seats.getOrNull(g.turn) ?: "Someone"}'s turn" to false
        g.timedOut != null -> (if (g.timedOut == g.yourSeat) "You ran out of time" else "${g.seats.getOrNull(g.timedOut!!) ?: "Someone"} ran out of time") to false
        g.leftSeat != null -> "${g.seats.getOrNull(g.leftSeat!!) ?: "Someone"} left" to false
        else -> (g.winner?.let { if (it == g.yourSeat) "You won" else "${g.seats[it]} won" } ?: "Finished") to false
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PaceIcon(g.pace)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(g.mode.title, style = MaterialTheme.typography.titleMedium)
                val open = g.seats.count { it == OPEN_SEAT }
                val names = g.seats.mapIndexedNotNull { i, s -> if (i == g.yourSeat) "You" else s.takeIf { it != OPEN_SEAT } }
                Text(
                    names.joinToString(", ") + if (open > 0) " + $open open seat${if (open > 1) "s" else ""}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
