package de.wtzg.domino.feature.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.wtzg.domino.ui.components.DominoTile

/**
 * Everyone's score side by side, you first, with a bar towards the target so you can
 * see at a glance who's ahead and by how much. The leader's score is highlighted.
 */
@Composable
internal fun Scoreboard(table: TableUi, modifier: Modifier = Modifier) {
    val scores = listOf("You" to table.yourScore) + table.opponents.map { it.name to it.score }
    val best = scores.maxOf { it.second }
    // Only one leader gets highlighted; a tie (or 0-0) highlights nobody.
    val leader = scores.indices.singleOrNull { scores[it].second == best }?.takeIf { best > 0 }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scores.forEachIndexed { i, (name, score) ->
                val leading = leader == i
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(bottom = 3.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$score",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (leading) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            maxLines = 1,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (score.toFloat() / table.target).coerceIn(0f, 1f) },
                        color = if (leading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    )
                }
            }
            Text(
                "to ${table.target}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Each opponent with their tiles left; whoever is to move is highlighted. */
@Composable
internal fun Opponents(table: TableUi, compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        table.opponents.forEach { seat ->
            Surface(
                color = if (seat.active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = if (compact) 3.dp else 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            seat.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (seat.away) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                            modifier = Modifier.weight(1f),
                        )
                        if (seat.thinking) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        val revealed = seat.revealed
                        if (revealed != null) {
                            // End of the hand: show what they held.
                            revealed.take(6).forEach { t -> DominoTile(t.a, t.b, half = 7.dp) }
                            if (revealed.size > 6) Text("+${revealed.size - 6}", style = MaterialTheme.typography.labelSmall)
                        } else if (compact) {
                            DominoTile(0, 0, half = 6.dp, faceDown = true)
                            Text(" ${seat.tiles}", style = MaterialTheme.typography.labelMedium)
                        } else {
                            val max = if (table.opponents.size > 1) 5 else 9
                            repeat(minOf(seat.tiles, max)) { DominoTile(0, 0, half = 8.dp, faceDown = true) }
                            if (seat.tiles > max) Text("+${seat.tiles - max}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        table.boneyard?.let { n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 4.dp)) {
                DominoTile(0, 0, half = 9.dp, faceDown = true)
                Text("$n", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
