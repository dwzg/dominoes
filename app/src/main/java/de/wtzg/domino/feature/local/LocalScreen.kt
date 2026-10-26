package de.wtzg.domino.feature.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.wtzg.domino.data.local.LocalInvite
import de.wtzg.domino.data.local.localInviteFrom
import de.wtzg.domino.data.local.localServerFrom
import de.wtzg.domino.feature.online.scanQr
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.ui.components.ErrorDialog

/** Host a game on this phone, or join one hosted on another phone on the same Wi-Fi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(
    state: LocalState,
    vm: LocalActions,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    LaunchedEffect(state.create) { if (state.create) { vm.created(); onCreate() } }
    LaunchedEffect(state.open) { state.open?.let { vm.opened(); onOpenGame(it) } }
    val context = LocalContext.current
    var typing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Same Wi-Fi") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Play at one table, each on your own phone. One phone hosts the game and the others join by " +
                    "scanning its QR code. Everyone needs to be on the same Wi-Fi, or on the host's hotspot. " +
                    "No internet or game server needed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val name = state.name
            if (name == null) {
                CircularProgressIndicator()
                return@Column
            }
            OutlinedTextField(
                value = name,
                onValueChange = vm::setName,
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            state.invite?.let { invite ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You're invited to game ${invite.code}", style = MaterialTheme.typography.titleMedium)
                        Text("Hosted at ${invite.server.removePrefix("http://")}", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { vm.join(invite) }, enabled = !state.busy) { Text("Join") }
                    }
                }
            }

            Section("Host a game") {
                val hosting = state.hosting
                if (hosting == null) {
                    Text(
                        "This phone runs the game. Keep the app open while you play: the game ends when you stop " +
                            "hosting or close the app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = vm::host, enabled = !state.busy) {
                        Icon(Icons.Outlined.Wifi, contentDescription = null)
                        Text("  Host a game")
                    }
                } else {
                    Text("Hosting on this phone at ${hosting.removePrefix("http://")}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::host, enabled = !state.busy) { Text("New game") }
                        OutlinedButton(onClick = vm::stopHosting, enabled = !state.busy) { Text("Stop hosting") }
                    }
                }
            }

            Section("Join a game") {
                Text("Scan the QR code in the host's lobby.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            scanQr(
                                context,
                                onText = { text ->
                                    localInviteFrom(text)?.let(vm::join)
                                        ?: vm.showError("That QR code isn't a game on the same Wi-Fi. Scan the code in the host's lobby.")
                                },
                                onUnavailable = { vm.showError("The QR scanner isn't available on this phone. Type the address instead.") },
                            )
                        },
                        enabled = !state.busy,
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("  Scan QR code")
                    }
                    TextButton(onClick = { typing = true }, enabled = !state.busy) { Text("Type it in") }
                }
            }

            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
        }
    }

    if (typing) {
        TypeInviteDialog(onDismiss = { typing = false }) { invite ->
            typing = false
            vm.join(invite)
        }
    }

    state.error?.let { ErrorDialog(it, vm::dismissError) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** For when scanning doesn't work: the host's address (shown in their lobby) and the game code. */
@Composable
private fun TypeInviteDialog(onDismiss: () -> Unit, onJoin: (LocalInvite) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val server = localServerFrom(address)
    val invite = server?.takeIf { code.length == Limits.CODE_LENGTH }?.let { LocalInvite(it, code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join by address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Both are shown in the host's lobby.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text("Address") },
                    placeholder = { Text("192.168.1.5:8077") },
                    isError = address.isNotEmpty() && server == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(Limits.CODE_LENGTH) },
                    label = { Text("Game code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { invite?.let(onJoin) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { invite?.let(onJoin) }, enabled = invite != null) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
