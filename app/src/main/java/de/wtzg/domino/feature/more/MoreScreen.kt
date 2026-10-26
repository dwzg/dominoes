package de.wtzg.domino.feature.more

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.wtzg.domino.BuildConfig
import de.wtzg.domino.data.AiSpeed
import de.wtzg.domino.data.Felt
import de.wtzg.domino.data.Settings
import de.wtzg.domino.data.Stats
import de.wtzg.domino.data.ThemeMode
import de.wtzg.domino.data.TileFace
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.ui.components.DominoTile
import de.wtzg.domino.ui.components.SectionHeader
import de.wtzg.domino.ui.components.TabTopBar
import de.wtzg.domino.ui.components.StatTile
import de.wtzg.domino.ui.theme.colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    stats: Stats,
    settings: Settings,
    onSettings: ((Settings) -> Settings) -> Unit,
    onResetStats: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { TabTopBar("More", scroll) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                StatsSection(stats) { confirmReset = true }
                SettingsSection(settings, onSettings)
                AboutSection()
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset your stats?") },
            text = { Text("Wins, coach accuracy and puzzle results are cleared. Score keeper games and lessons read are kept.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onResetStats() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatsSection(stats: Stats, onReset: () -> Unit) {
    val total = stats.total
    SectionHeader("Your stats") { TextButton(onClick = onReset) { Text("Reset") } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${total.matchesWon}", "matches won", Modifier.weight(1f))
        StatTile(total.winRate?.let { "$it%" } ?: "–", "win rate", Modifier.weight(1f))
        StatTile(total.accuracy?.let { "$it%" } ?: "–", "coach match", Modifier.weight(1f))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatRow("Game", "Wins", "Coach", bold = true)
            HorizontalDivider()
            GameMode.entries.forEach { m ->
                val s = stats.mode(m)
                StatRow(m.title, "${s.matchesWon} of ${s.matches}", s.accuracy?.let { "$it%" } ?: "–")
            }
            HorizontalDivider()
            val p = stats.puzzles
            StatRow("Puzzles", "${p.solved} of ${p.attempted}", "streak ${p.bestStreak}")
        }
    }
}

@Composable
private fun StatRow(a: String, b: String, c: String, bold: Boolean = false) {
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth()) {
        Text(a, fontWeight = w, modifier = Modifier.weight(1.2f))
        Text(b, fontWeight = w, modifier = Modifier.weight(1f))
        Text(c, fontWeight = w, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsSection(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    SectionHeader("Settings")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Setting("Theme") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = s.theme == t,
                            onClick = { update { it.copy(theme = t) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(t.label) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchSetting("Wallpaper colours", "Use Material You colours instead of the table green", s.dynamicColor) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }
            Setting("Table") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Felt.entries.forEach { f -> FeltSwatch(f, s.felt == f) { update { it.copy(felt = f) } } }
                }
            }
            Setting("Tiles") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TileFace.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = s.tileFace == f,
                            onClick = { update { it.copy(tileFace = f) } },
                            shape = SegmentedButtonDefaults.itemShape(i, TileFace.entries.size),
                        ) { Text(f.label) }
                    }
                }
                // A sample, so you can see the style before playing.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6 to 1, 5 to 2, 4 to 3, 0 to 0).forEach { (a, b) ->
                        DominoTile(a, b, vertical = false, half = 22.dp, elevation = 1.dp)
                    }
                }
            }
            SwitchSetting("Grey out your tiles", "While someone else is playing", s.dimHandWhileWaiting) { v ->
                update { it.copy(dimHandWhileWaiting = v) }
            }
            Setting("Computer speed") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AiSpeed.entries.forEachIndexed { i, a ->
                        SegmentedButton(
                            selected = s.aiSpeed == a,
                            onClick = { update { it.copy(aiSpeed = a) } },
                            shape = SegmentedButtonDefaults.itemShape(i, AiSpeed.entries.size),
                        ) { Text(a.label) }
                    }
                }
            }
            SwitchSetting("Coach on by default", "New games start with move-by-move feedback", s.coach) { v ->
                update { it.copy(coach = v) }
            }
            SwitchSetting("Vibration", "A short buzz when points are scored", s.haptics) { v -> update { it.copy(haptics = v) } }
            ServerSetting(s.serverUrl) { url -> update { it.copy(serverUrl = url.trim()) } }
        }
    }
}

/** The online game server. Empty uses the one built into the app, if any. */
@Composable
private fun ServerSetting(current: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val shown = current.ifBlank { BuildConfig.SERVER_URL.ifBlank { "Not set" } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Online server", style = MaterialTheme.typography.titleSmall)
            Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { editing = true }) { Text("Change") }
    }
    if (editing) {
        var value by remember { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Online server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The address of the game server you and your friends use.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, placeholder = { Text("dominoes.example.com") })
                }
            },
            confirmButton = { TextButton(onClick = { editing = false; onSave(value) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Setting(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FeltSwatch(felt: Felt, selected: Boolean, onClick: () -> Unit) {
    val c = felt.colors()
    Box(
        Modifier
            .size(44.dp)
            // Clip so the press ripple is round like the swatch, not square.
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(c.center, c.edge)), CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${felt.label} table" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun AboutSection() {
    SectionHeader("About")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dominoes ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Draw, Block, All Fives and All Threes with a double-six set, for 2 to 4 players. " +
                    "Default targets: Draw and Block to 60, All Fives to 100, All Threes to 90.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
