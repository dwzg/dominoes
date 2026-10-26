package de.wtzg.domino.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Tile

/** A small heading above a group of items. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** A number with a label underneath, for stats. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Two tiles that hint at what each game is about. */
fun GameMode.illustration(): List<Tile> = when (this) {
    GameMode.DRAW -> listOf(Tile(6, 6), Tile(6, 3))
    GameMode.BLOCK -> listOf(Tile(4, 4), Tile(4, 1))
    GameMode.ALL_FIVES -> listOf(Tile(5, 5), Tile(5, 0))
    GameMode.ALL_THREES -> listOf(Tile(3, 3), Tile(3, 6))
}

/** Decoration only, so screen readers skip it. */
@Composable
fun ModeArt(mode: GameMode, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        mode.illustration().forEach { DominoTile(it.a, it.b, half = 14.dp, elevation = 1.dp) }
    }
}

/** Large collapsing title on tall screens; a regular bar on short (landscape) ones to save space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, scroll: TopAppBarScrollBehavior) {
    if (LocalConfiguration.current.screenHeightDp < 480) {
        TopAppBar(title = { Text(title) })
    } else {
        LargeTopAppBar(title = { Text(title) }, scrollBehavior = scroll)
    }
}
