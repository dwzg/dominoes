package de.wtzg.domino.feature.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.Tile
import de.wtzg.domino.ui.components.DominoTile
import de.wtzg.domino.ui.components.HandRow
import kotlinx.coroutines.delay

/** Your tiles in one row; they shrink a little when you hold many, and only wrap as a last resort. */
@Composable
internal fun Hand(
    table: TableUi,
    actions: GameActions,
    compact: Boolean = false,
    maxHalf: Dp = 28.dp,
    dimWhileWaiting: Boolean = true,
) {
    // A fresh deal waits for the shuffle, and gets new tiles even if one is the
    // same as last hand's, so every tile deals in again.
    val newHand = table.board.isEmpty
    var deal by remember { mutableIntStateOf(0) }
    LaunchedEffect(newHand) { if (newHand) deal++ }
    HandRow(table.hand, Modifier.padding(vertical = if (compact) 4.dp else 10.dp), maxHalf = maxHalf) { i, t, half ->
        key(deal, t) {
            val fits = table.legal.any { it.tile == t }
            val hinted = t == table.hint?.move?.tile
            HandTile(
                tile = t,
                index = i,
                dealDelay = if (newHand) HAND_DEAL_MS else 0L,
                half = half,
                selected = t == table.selected,
                hinted = hinted,
                stateDescription = when {
                    table.yourTurn && !fits -> "Doesn't fit"
                    hinted -> "Hint"
                    else -> null
                },
                dimmed = if (table.yourTurn) {
                    !fits
                } else {
                    // Someone else is to move; not at the end of a hand.
                    dimWhileWaiting && table.overlay == null
                },
                onClick = { actions.tapTile(t) },
            )
        }
    }
}

/** A tile in your hand: deals in with a little stagger and lifts when selected. */
@Composable
private fun HandTile(
    tile: Tile,
    index: Int,
    dealDelay: Long,
    half: Dp,
    selected: Boolean,
    hinted: Boolean,
    stateDescription: String?,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(dealDelay + index * 60L)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    val lift by animateDpAsState(if (selected) (-10).dp else 0.dp, label = "lift")
    val density = LocalDensity.current
    DominoTile(
        tile.a, tile.b,
        half = half,
        highlighted = selected || hinted,
        dimmed = dimmed,
        elevation = if (selected) 8.dp else 2.dp,
        // Ignore taps until the tile has dealt in, so nothing is played unseen.
        onClick = { if (appear.value > 0.5f) onClick() },
        selected = selected,
        stateDescription = stateDescription,
        modifier = Modifier.graphicsLayer {
            val p = appear.value
            alpha = p.coerceIn(0f, 1f)
            translationY = with(density) { lift.toPx() } + (1f - p) * 60f
            scaleX = 0.6f + 0.4f * p
            scaleY = 0.6f + 0.4f * p
        },
    )
}

/** End of a hand or of the match, on top of the table. */
@Composable
internal fun Overlay(o: OverlayUi, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .padding(16.dp)
            .widthIn(max = 420.dp),
    ) {
        Column(
            Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(o.title, style = MaterialTheme.typography.titleLarge)
            o.lines.forEach { Text(it) }
            o.scores.forEach { l ->
                Row(Modifier.fillMaxWidth()) {
                    Text(l.name, modifier = Modifier.weight(1f), fontWeight = if (l.isYou) FontWeight.Bold else FontWeight.Normal)
                    l.pips?.let { Text("$it pips  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("${l.score}", fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                o.secondary?.let { OutlinedButton(onClick = onSecondary) { Text(it) } }
                o.primary?.let { Button(onClick = onPrimary) { Text(it) } }
            }
        }
    }
}
