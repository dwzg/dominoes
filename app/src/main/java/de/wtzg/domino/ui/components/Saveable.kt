package de.wtzg.domino.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** An editable list of texts that survives rotation, e.g. what was typed into a form. */
@Composable
fun rememberSaveableTextList(initial: () -> List<String>): SnapshotStateList<String> =
    rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { initial().toMutableStateList() }
