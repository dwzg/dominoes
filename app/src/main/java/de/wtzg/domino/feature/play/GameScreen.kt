package de.wtzg.domino.feature.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.Strategy
import de.wtzg.domino.engine.Tile
import de.wtzg.domino.ui.components.DominoTile
import de.wtzg.domino.ui.components.TableTarget
import de.wtzg.domino.ui.components.TableView
import kotlinx.coroutines.delay

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
                Table(table, actions, onOverlaySecondary, if (landscape) LANDSCAPE_ROW else PORTRAIT_ROW, showTitle = !landscape, m)
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
                        Opponents(table, compact = short)
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

/**
 * Everyone's score side by side, you first, with a bar towards the target so you can
 * see at a glance who's ahead and by how much. The leader's score is highlighted.
 */
@Composable
private fun Scoreboard(table: TableUi, modifier: Modifier = Modifier) {
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
private fun Opponents(table: TableUi, compact: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun Table(
    table: TableUi,
    actions: GameActions,
    onSecondary: () -> Unit,
    rowWidth: Float,
    showTitle: Boolean,
    modifier: Modifier,
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
        ShuffleAnimation(active = table.board.isEmpty && table.overlay == null)
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

/**
 * One compact box: what the coach (or the hint) says about your play, and what the
 * others just did. Long texts are cut short; tap the box to read them in full.
 */
@Composable
private fun Messages(table: TableUi, compact: Boolean = false) {
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

    var showAll by remember { mutableStateOf(false) }
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

/** Your tiles in one row; they shrink a little when you hold many, and only wrap as a last resort. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hand(
    table: TableUi,
    actions: GameActions,
    compact: Boolean = false,
    maxHalf: Dp = 28.dp,
    dimWhileWaiting: Boolean = true,
) {
    val hand = table.hand
    val gap = if (hand.size > 9) 4.dp else 6.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 10.dp),
    ) {
        // A fresh deal waits for the shuffle, and gets new tiles even if one is the
        // same as last hand's, so every tile deals in again.
        val newHand = table.board.isEmpty
        var deal by remember { mutableIntStateOf(0) }
        LaunchedEffect(newHand) { if (newHand) deal++ }
        val n = hand.size.coerceAtLeast(1)
        // 1dp of slack so rounding never pushes the last tile onto a second row.
        val half: Dp = ((maxWidth - 1.dp - gap * (n - 1)) / n).coerceIn(18.dp, maxHalf)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            hand.forEachIndexed { i, t ->
                key(deal, t) {
                    HandTile(
                        tile = t,
                        index = i,
                        dealDelay = if (newHand) SHUFFLE_MS else 0L,
                        half = half,
                        selected = t == table.selected,
                        hinted = t == table.hint?.move?.tile,
                        dimmed = if (table.yourTurn) {
                            table.legal.none { it.tile == t }
                        } else {
                            // Someone else is to move; not at the end of a hand.
                            dimWhileWaiting && table.overlay == null
                        },
                        onClick = { actions.tapTile(t) },
                    )
                }
            }
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
private fun Overlay(o: OverlayUi, onPrimary: () -> Unit, onSecondary: () -> Unit) {
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
