package de.wtzg.domino.feature.online

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wtzg.domino.protocol.Friend
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.ui.components.SectionHeader
import kotlinx.coroutines.delay

/**
 * Your friend code, adding friends, requests, and your friends with a button to challenge
 * each. [initialCode] comes from a friend link and is filled in, ready to add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    state: FriendsState,
    vm: FriendsActions,
    initialCode: String?,
    onBack: () -> Unit,
    onChallenge: (Friend) -> Unit,
    /** No name on this server yet: set one up on the online screen first. */
    onSetUp: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }
    // New requests show up without leaving the screen.
    LaunchedEffect(state.available) {
        while (state.available == true) {
            delay(30_000)
            vm.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.available == true) {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val f = state.friends
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                !state.registered -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pick a name for online play first, then come back to add friends.")
                        Button(onClick = onSetUp) { Text("Set up online play") }
                    }
                }
                state.available == false -> item {
                    Text(
                        "This server doesn't have friends yet. Ask whoever runs it to update it. " +
                            "You can still invite people with a game code.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                f == null -> item { CircularProgressIndicator() }
                else -> friendsContent(f, state, vm, initialCode, onChallenge)
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

private fun androidx.compose.foundation.lazy.LazyListScope.friendsContent(
    f: FriendsView,
    state: FriendsState,
    vm: FriendsActions,
    initialCode: String?,
    onChallenge: (Friend) -> Unit,
) {
    item { YourCode(f.friendCode, state.serverUrl) }
    item { AddFriend(initialCode.orEmpty(), state.busy, vm::add) }

    if (f.requests.isNotEmpty()) {
        item { SectionHeader("Friend requests") }
        items(f.requests, key = { "request-${it.playerId}" }) { p ->
            PersonRow(p.name, "Wants to be your friend") {
                TextButton(onClick = { vm.remove(p.playerId) }, enabled = !state.busy) { Text("Decline") }
                Button(onClick = { vm.accept(p.playerId) }, enabled = !state.busy) { Text("Accept") }
            }
        }
    }

    item { SectionHeader("Your friends") }
    if (f.friends.isEmpty()) {
        item {
            Text(
                "No friends yet. Share your friend code, or add someone you've played with.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(f.friends, key = { "friend-${it.playerId}" }) { p ->
        var menu by remember { mutableStateOf(false) }
        var confirm by remember { mutableStateOf(false) }
        PersonRow(p.name, null) {
            FilledTonalButton(onClick = { onChallenge(p) }, enabled = !state.busy) { Text("Challenge") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More for ${p.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove friend") }, onClick = { menu = false; confirm = true })
                }
            }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Remove ${p.name}?") },
                text = { Text("You'll need to add each other again to send invites.") },
                confirmButton = { TextButton(onClick = { confirm = false; vm.remove(p.playerId) }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            )
        }
    }

    if (f.sent.isNotEmpty()) {
        item { SectionHeader("Waiting for an answer") }
        items(f.sent, key = { "sent-${it.playerId}" }) { p ->
            PersonRow(p.name, "Request sent") {
                TextButton(onClick = { vm.remove(p.playerId) }, enabled = !state.busy) { Text("Cancel") }
            }
        }
    }

    if (f.recent.isNotEmpty()) {
        item { SectionHeader("People you've played with") }
        items(f.recent, key = { "recent-${it.playerId}" }) { p ->
            PersonRow(p.name, null) {
                OutlinedButton(onClick = { vm.addPlayer(p.playerId) }, enabled = !state.busy) { Text("Add friend") }
            }
        }
    }
}

/** Your code, as text, a link to share and a QR code for someone next to you. */
@Composable
private fun YourCode(code: String, serverUrl: String) {
    val context = LocalContext.current
    val link = "$serverUrl/friend/$code"
    var showQr by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your friend code", style = MaterialTheme.typography.labelLarge)
            Text(formatFriendCode(code), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            if (showQr) {
                QrCode(text = link, description = "QR code to add you as a friend", modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(
                    onClick = {
                        val text = "Add me as a friend in Dominoes! My friend code is ${formatFriendCode(code)}, or tap: $link"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Share your friend code"))
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("  Share")
                }
                OutlinedButton(onClick = { showQr = !showQr }) { Text(if (showQr) "Hide QR code" else "Show QR code") }
            }
        }
    }
}

@Composable
private fun AddFriend(initial: String, busy: Boolean, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf(initial.let { c -> friendCodeFrom(c)?.let(::formatFriendCode) ?: c }) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = friendCodeFrom(code) != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(9) },
                label = { Text("Friend's code") },
                placeholder = { Text("ABCD-2345") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ready) { onAdd(code); code = "" } }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onAdd(code); code = "" }, enabled = ready && !busy) { Text("Add") }
            IconButton(
                onClick = {
                    problem = null
                    scanQr(
                        context,
                        onText = { text ->
                            val scanned = friendCodeFrom(text)
                            if (scanned == null) problem = "That QR code isn't a friend code." else onAdd(scanned)
                        },
                        onUnavailable = { problem = "The QR scanner isn't available on this phone. Type the code instead." },
                    )
                },
                enabled = !busy,
            ) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan a friend's QR code") }
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PersonRow(name: String, note: String?, actions: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
