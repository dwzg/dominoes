package de.wtzg.domino.feature.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.Strategy

/**
 * One compact box: what the coach (or the hint) says about your play, and what the
 * others just did. Long texts are cut short; tap the box to read them in full.
 */
@Composable
internal fun Messages(table: TableUi, compact: Boolean = false) {
    val hint = table.hint
    val coach: CoachNote? = when {
        hint != null -> CoachNote(
            NoteKind.GOOD, "Play ${Strategy.describe(hint.move, table.board)}.",
            hint.pros.take(2).joinToString(" ").ifEmpty { "Any reasonable move works here." } + " Tap the yellow spot to play it.",
        )
        else -> table.coach
    }
    val others = table.log.joinToString(" ").ifEmpty { null }
    if (coach == null && others == null) return

    var showAll by rememberSaveable { mutableStateOf(false) }
    var cut by remember(coach, others) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showAll = true },
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            coach?.let {
                MessageLine(if (hint != null) "Hint" else "Coach", noteColor(it.kind), it.title, it.text, maxLines = if (compact) 2 else 3, small = compact) { o ->
                    cut = cut || o
                }
            }
            others?.let {
                MessageLine("Table", MaterialTheme.colorScheme.onSurfaceVariant, "", it, maxLines = if (compact) 1 else 2, small = compact) { o ->
                    cut = cut || o
                }
            }
            if (cut && !compact) Text("Tap to read more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text("OK") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    coach?.let {
                        Text(it.title, fontWeight = FontWeight.Bold)
                        Text(it.text)
                    }
                    if (table.log.isNotEmpty()) {
                        Text("At the table", fontWeight = FontWeight.Bold)
                        table.log.forEach { Text(it) }
                    }
                }
            },
        )
    }
}

@Composable
private fun noteColor(kind: NoteKind): Color = when (kind) {
    NoteKind.GOOD -> MaterialTheme.colorScheme.primary
    NoteKind.TIP -> MaterialTheme.colorScheme.tertiary
    NoteKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun MessageLine(
    label: String,
    color: Color,
    title: String,
    text: String,
    maxLines: Int,
    small: Boolean = false,
    onOverflow: (Boolean) -> Unit,
) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier
                .widthIn(min = 48.dp)
                .padding(top = 2.dp, end = 8.dp),
        )
        Text(
            buildAnnotatedString {
                if (title.isNotEmpty()) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(title)
                        if (title.last() !in ".!?:") append(".")
                    }
                    append(" ")
                }
                append(text.replace('\n', ' '))
            },
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { onOverflow(it.hasVisualOverflow) },
        )
    }
}
