package de.wtzg.domino.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.wtzg.domino.protocol.Limits

/** The target in [text], or null if it isn't a whole number from [Limits.TARGET_MIN] to [Limits.TARGET_MAX]. */
fun customTargetFrom(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in Limits.TARGET_MIN..Limits.TARGET_MAX }

/** Asks for any target score from [Limits.TARGET_MIN] to [Limits.TARGET_MAX], for games online and against the computer. */
@Composable
fun CustomTargetDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val value = customTargetFrom(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play to") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(4) },
                label = { Text("Target score") },
                supportingText = { Text("${Limits.TARGET_MIN} to ${Limits.TARGET_MAX}") },
                isError = value == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
