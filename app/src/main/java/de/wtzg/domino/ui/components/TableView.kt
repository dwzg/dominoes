package de.wtzg.domino.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.Arm
import de.wtzg.domino.engine.Board
import de.wtzg.domino.engine.BoardLayout
import de.wtzg.domino.engine.Laid
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Tile
import de.wtzg.domino.ui.theme.LocalFelt
import kotlin.math.min
import kotlin.math.roundToInt

/** Size of half a tile before the table zooms to fit. */
private val UNIT = 24.dp

/** Largest zoom, so a near-empty table doesn't show giant tiles. */
private const val MAX_ZOOM = 2.2f

/** A spot on the table the player can tap to play a tile there. */
data class TableTarget(val move: Move, val isHint: Boolean)

/** A short-lived badge over the table, e.g. "+15" when a play scores. [id] restarts the animation. */
data class TableBadge(val id: Long, val text: String)

/** How long a [TableBadge] shows. Clear it after this so it doesn't show again when the table is redrawn. */
const val BADGE_MS = 1600L

/**
 * The domino table. Tiles keep their place as the line grows; the camera pans and
 * zooms smoothly to keep everything in view, and the newest tile slides in.
 */
@Composable
fun TableView(
    board: Board,
    modifier: Modifier = Modifier,
    lastPlaced: Tile? = null,
    lastPlacedByOpponent: Boolean = false,
    targets: List<TableTarget> = emptyList(),
    onTarget: (Move) -> Unit = {},
    badge: TableBadge? = null,
    /** Row width in half-tiles; narrower rows suit tall (portrait) tables. */
    halfWidth: Float = BoardLayout.HALF_WIDTH,
    showEnds: Boolean = true,
    /** A line above the open ends, e.g. the game and target. */
    caption: String? = null,
) {
    val laid = remember(board, halfWidth) { BoardLayout.layout(board, halfWidth) }
    val slots = remember(board, targets, halfWidth) { targets.map { it to BoardLayout.slot(board, it.move, halfWidth) } }
    val felt = LocalFelt.current
    // Tiles already down when the table appears (e.g. after rotating or coming back to a game)
    // don't fly in again. Once those tiles leave the table (a new hand), every tile is new.
    var settled by remember { mutableStateOf(board.tiles.toSet()) }
    val stillDown = board.tiles.containsAll(settled)
    LaunchedEffect(stillDown) { if (!stillDown) settled = emptySet() }

    BoxWithConstraints(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.radialGradient(listOf(felt.center, felt.edge))),
    ) {
        val unitPx = with(LocalDensity.current) { UNIT.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Fit all tiles plus the tap targets, with a margin, keeping room for the label at the top.
        val all = laid + slots.map { it.second }
        val labelRoom = if (showEnds || caption != null) (if (caption != null) 2.2f else 1.2f) else 0f
        val minX = all.minOfOrNull { it.x } ?: -1f
        val maxX = all.maxOfOrNull { it.x + it.w } ?: 1f
        val minY = (all.minOfOrNull { it.y } ?: -1f) - labelRoom
        val maxY = all.maxOfOrNull { it.y + it.h } ?: 1f
        val margin = 0.6f
        val fit = min(
            widthPx / ((maxX - minX + 2 * margin) * unitPx),
            heightPx / ((maxY - minY + 2 * margin) * unitPx),
        ).coerceAtMost(MAX_ZOOM)
        val camSpring = spring<Float>(stiffness = Spring.StiffnessVeryLow)
        val zoom by animateFloatAsState(fit, camSpring, label = "zoom")
        val camX by animateFloatAsState((minX + maxX) / 2, camSpring, label = "camX")
        val camY by animateFloatAsState((minY + maxY) / 2, camSpring, label = "camY")

        // All tiles are positioned in table units around the box centre; one layer moves the camera.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = -camX * unitPx * zoom
                    translationY = -camY * unitPx * zoom
                },
        ) {
            val origin = IntOffset((widthPx / 2).roundToInt(), (heightPx / 2).roundToInt())
            laid.forEach { t ->
                key(t.tile) {
                    val last = t.tile == lastPlaced
                    TableTile(t, origin, unitPx, animateIn = last && !(stillDown && t.tile in settled), highlighted = last, fromTop = lastPlacedByOpponent)
                }
            }
            slots.forEach { (target, slot) ->
                key(target) { TargetSpot(target, slot, board, origin, unitPx, onTarget) }
            }
        }

        if (board.isEmpty) {
            Text(
                "The table is empty",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (caption != null || (showEnds && !board.isEmpty)) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                caption?.let {
                    Text(it, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                if (showEnds && !board.isEmpty) {
                    Text(endsLabel(board), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        badge?.let { b -> key(b.id) { Badge(b.text, Modifier.align(Alignment.Center)) } }
    }
}

/** "Open ends: 3 · 5", naming the spinner's free sides separately so counts in captions add up. */
private fun endsLabel(board: Board): String {
    val arms = listOf(Arm.WEST, Arm.EAST) + listOf(Arm.NORTH, Arm.SOUTH).filter { board.arm(it).isNotEmpty() }
    val main = "Open ends: " + arms.joinToString(" · ") { board.endValue(it).toString() }
    val freeSides = board.northSouthOpen && (board.north.isEmpty() || board.south.isEmpty())
    return when {
        freeSides -> "$main  ·  spinner sides open"
        board.hasSpinner && board.east.isEmpty() != board.west.isEmpty() -> "$main  ·  spinner"
        else -> main
    }
}

private fun Modifier.at(l: Laid, origin: IntOffset, unitPx: Float) = offset {
    IntOffset(origin.x + (l.x * unitPx).roundToInt(), origin.y + (l.y * unitPx).roundToInt())
}

@Composable
private fun TableTile(l: Laid, origin: IntOffset, unitPx: Float, animateIn: Boolean, highlighted: Boolean, fromTop: Boolean) {
    val progress = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val half: Dp = UNIT
    DominoTile(
        l.first, l.second,
        vertical = l.vertical,
        half = half,
        highlighted = highlighted,
        elevation = 3.dp,
        modifier = Modifier
            .at(l, origin, unitPx)
            .graphicsLayer {
                val p = progress.value
                val rest = 1f - p
                alpha = p.coerceIn(0f, 1f)
                translationY = rest * unitPx * 9f * (if (fromTop) -1f else 1f)
                rotationZ = rest * (if (fromTop) -30f else 30f)
                scaleX = 1f + rest * 0.5f
                scaleY = 1f + rest * 0.5f
            },
    )
}

@Composable
private fun TargetSpot(target: TableTarget, l: Laid, board: Board, origin: IntOffset, unitPx: Float, onTap: (Move) -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val color = if (target.isHint) Color(0xFFFFD54F) else Color.White
    val shape = RoundedCornerShape(UNIT * 0.18f)
    val n = board.endValue(target.move.arm)
    Box(
        Modifier
            .at(l, origin, unitPx)
            .size(UNIT * l.w, UNIT * l.h)
            .graphicsLayer { alpha = pulse }
            .clip(shape)
            .background(color.copy(alpha = 0.3f))
            .border(2.dp, color, shape)
            .clickable { onTap(target.move) }
            .semantics { contentDescription = "Play ${target.move.tile} on the $n end" },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = color, style = MaterialTheme.typography.titleLarge)
    }
}

/** Pops in, floats up and fades out. */
@Composable
private fun Badge(text: String, modifier: Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(BADGE_MS.toInt())) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = modifier.graphicsLayer {
            val v = p.value
            val s = if (v < 0.15f) 0.6f + v / 0.15f * 0.4f else 1f
            scaleX = s
            scaleY = s
            translationY = -v * 120f
            alpha = if (v < 0.7f) 1f else (1f - (v - 0.7f) / 0.3f)
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
