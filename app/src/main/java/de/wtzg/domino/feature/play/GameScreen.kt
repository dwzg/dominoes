package de.wtzg.domino.feature.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import de.wtzg.domino.ui.components.TableTarget
import de.wtzg.domino.ui.components.TableView

/** Narrow rows in portrait so the line grows downwards and tiles stay big. */
private const val PORTRAIT_ROW = 4f
private const val LANDSCAPE_ROW = 7f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    table: TableUi,
    actions: GameActions,
    haptics: Boolean,
    onBack: () -> Unit,
    /** Grey out your tiles while someone else is to move. */
    dimHandWhileWaiting: Boolean = true,
    /** The overlay's second button (e.g. "Change game"). */
    onOverlaySecondary: () -> Unit = {},
    /** Extra items for the top bar, e.g. a menu. */
    topActions: @Composable () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(table.badge?.id) {
        if (haptics && table.badge != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val hint = @Composable {
        if (table.showHint) {
            IconButton(onClick = actions::showHint, enabled = table.yourTurn && table.legal.size > 1) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = "Hint")
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val short = maxHeight < 480.dp
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
            val felt = @Composable { m: Modifier ->
                Table(table, actions, if (landscape) LANDSCAPE_ROW else PORTRAIT_ROW, m, showTitle = !landscape, onSecondary = onOverlaySecondary)
            }
            if (landscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    felt(Modifier.weight(1.5f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                            Text(table.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            hint()
                            topActions()
                        }
                        Scoreboard(table, Modifier.padding(bottom = 4.dp))
                        Opponents(table, compact = short, modifier = Modifier.fillMaxWidth())
                        // Short screens get a two-line message box; tap it for the full text.
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        ) {
                            Messages(table, compact = short)
                        }
                        StatusRow(table, actions, compact = short)
                        Hand(table, actions, compact = short, maxHalf = 44.dp, dimWhileWaiting = dimHandWhileWaiting)
                    }
                }
            } else {
                // Portrait: one slim row on top, the table takes everything in between,
                // and your hand gets big tiles at the bottom.
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        Opponents(table, compact = true, modifier = Modifier.weight(1f))
                        hint()
                        topActions()
                    }
                    Scoreboard(table, Modifier.padding(horizontal = 6.dp))
                    felt(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp))
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Messages(table, compact = true)
                        StatusRow(table, actions, compact = true)
                        Hand(table, actions, compact = true, maxHalf = 44.dp, dimWhileWaiting = dimHandWhileWaiting)
                    }
                }
            }
        }
    }
}

@Composable
private fun Table(
    table: TableUi,
    actions: GameActions,
    rowWidth: Float,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    onSecondary: () -> Unit = {},
) {
    val targets = when {
        !table.yourTurn -> emptyList()
        table.selected != null -> table.legal.filter { it.tile == table.selected }.map { TableTarget(it, isHint = false) }
        table.hint != null -> listOf(TableTarget(table.hint.move, isHint = true))
        else -> emptyList()
    }
    Box(modifier) {
        TableView(
            board = table.board,
            lastPlaced = table.lastPlaced,
            lastPlacedByOpponent = table.lastPlacedByOpponent,
            targets = targets,
            onTarget = actions::playMove,
            badge = table.badge,
            halfWidth = rowWidth,
            caption = if (showTitle) table.title else null,
            modifier = Modifier.fillMaxSize(),
        )
        // A new hand starts on an empty table: shuffle before the tiles are dealt.
        ShuffleAnimation(
            active = table.board.isEmpty && table.overlay == null,
            yourTiles = table.hand.size,
            opponentTiles = table.opponents.map { it.tiles },
        )
        table.overlay?.let { o ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Overlay(o, actions::overlayPrimary, onSecondary)
            }
        }
    }
}

/** What to do now, and Draw or Pass when that's the only option. */
@Composable
private fun StatusRow(table: TableUi, actions: GameActions, compact: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .padding(top = if (compact) 0.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(table.status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (table.yourTurn && table.canDraw) Button(onClick = actions::draw) { Text("Draw") }
        if (table.yourTurn && table.mustPass) Button(onClick = actions::pass) { Text("Pass") }
    }
}
