package de.wtzg.domino.feature.scorekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.wtzg.domino.ui.components.rememberSaveableTextList

/** Enter or edit everyone's points for one round. */
@Composable
internal fun RoundDialog(
    players: List<String>,
    title: String,
    initial: List<Int>?,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val inputs = rememberSaveableTextList {
        players.indices.map { i -> initial?.getOrNull(i)?.takeIf { it != 0 }?.toString() ?: "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                players.forEachIndexed { i, name ->
                    OutlinedTextField(
                        value = inputs[i],
                        onValueChange = { v -> inputs[i] = v.filter(Char::isDigit).take(5) },
                        label = { Text(name) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (i == players.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete this round", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(inputs.map { it.toIntOrNull() ?: 0 }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
