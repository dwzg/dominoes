package de.wtzg.domino.feature.scorekeeper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A row in the table of rounds: a label and one value per player. */
@Composable
internal fun RoundRow(label: String, values: List<String>, modifier: Modifier = Modifier, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = style, fontWeight = weight, modifier = Modifier.width(32.dp))
        values.forEach {
            Text(
                it,
                style = style,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
