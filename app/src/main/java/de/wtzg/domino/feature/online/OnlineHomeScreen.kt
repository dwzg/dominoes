package de.wtzg.domino.feature.online

import android.Manifest
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.ui.components.SectionHeader
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineHomeScreen(
    state: OnlineHomeState,
    vm: OnlineHomeActions,
    initialCode: String?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    LaunchedEffect(state.open) { state.open?.let { onOpenGame(it); vm.opened() } }
    val reg = state.account?.registration
    // Keep the list fresh while it's on screen.
    LaunchedEffect(reg) {
        while (reg != null) {
            delay(30_000)
            vm.refresh()
        }
    }
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
                    item { Identity(reg.name, vm::rename) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onCreate, modifier = Modifier.weight(1f)) { Text("Create a game") }
                        }
                    }
                    item { JoinWithCode(initialCode.orEmpty(), state.busy, vm::join) }
                    item { NotificationsPermission(state.games.any { it.pace == Pace.TURN_BASED }) }
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

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text("Something went wrong") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
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
                    )
                },
                enabled = !busy,
            ) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR code") }
        }
        scanProblem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** Turn-based games need notifications; ask once there is one (Android 13+). */
@Composable
private fun NotificationsPermission(needed: Boolean) {
    if (!needed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/** A public lobby: who's hosting, what they're playing, and how many seats are left. */
@Composable
private fun OpenGameRow(g: OpenGame, busy: Boolean, onJoin: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (g.pace == Pace.LIVE) Icons.Outlined.Bolt else Icons.Outlined.HourglassEmpty,
                contentDescription = if (g.pace == Pace.LIVE) "Live" else "Turn by turn",
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text("${g.host}'s table", style = MaterialTheme.typography.titleMedium)
                val free = g.seats - g.taken
                Text(
                    "${g.mode.title} · to ${g.target} · $free of ${g.seats} seats free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onJoin, enabled = !busy) { Text("Join") }
        }
    }
}

@Composable
private fun GameRow(g: GameSummary, onClick: () -> Unit) {
    val (label, highlight) = when {
        g.status == GameStatus.LOBBY -> "Lobby · ${g.code}" to false
        g.isYourTurn -> "Your turn" to true
        g.status == GameStatus.PLAYING -> "${g.seats.getOrNull(g.turn) ?: "Someone"}'s turn" to false
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
            Icon(
                if (g.pace == Pace.LIVE) Icons.Outlined.Bolt else Icons.Outlined.HourglassEmpty,
                contentDescription = if (g.pace == Pace.LIVE) "Live" else "Turn by turn",
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(g.mode.title, style = MaterialTheme.typography.titleMedium)
                val open = g.seats.count { it == "Open seat" }
                val names = g.seats.mapIndexedNotNull { i, s -> if (i == g.yourSeat) "You" else s.takeIf { it != "Open seat" } }
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
