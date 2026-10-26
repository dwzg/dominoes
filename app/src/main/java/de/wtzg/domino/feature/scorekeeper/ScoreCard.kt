package de.wtzg.domino.feature.scorekeeper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One player's total, with how far they are from the target. */
@Composable
internal fun ScoreCard(
    name: String,
    total: Int,
    game: ScoreGame,
    isLeader: Boolean,
    starts: Boolean,
    /** Someone starts this round; every card keeps the line so the scores line up. */
    showStarter: Boolean,
    modifier: Modifier,
) {
    val colors = if (isLeader) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = modifier, colors = colors) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isLeader) Icon(Icons.Default.Star, contentDescription = "Leading")
            }
            if (showStarter) {
                Text(
                    "Starts this round",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    // Invisible and silent on the other cards; it only keeps their layout in step.
                    modifier = if (starts) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
                )
            }
            Text(
                total.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            if (game.mode == ScoreMode.FIRST_TO_TARGET) {
                LinearProgressIndicator(
                    progress = { (total.toFloat() / game.limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val left = game.limit - total
                Text(
                    if (left > 0) "$left to go" else "Target reached",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
