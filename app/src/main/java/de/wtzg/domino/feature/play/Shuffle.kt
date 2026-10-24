package de.wtzg.domino.feature.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.wtzg.domino.ui.components.DominoTile
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** How long the shuffle and deal at the start of a hand take. The computer waits for it. */
const val SHUFFLE_MS = 2400L

/** When your own tiles have left the table, so your hand can start dealing in. */
const val HAND_DEAL_MS = 1800L

/** A double-six set. */
private const val SET_SIZE = 28

// The phases, in milliseconds: tiles appear in a block, are swirled, settle, then are dealt.
private const val APPEAR_END = 250f
private const val SWIRL_END = 1250f
private const val SETTLE_END = 1550f
private const val DEAL_START = 1600f
private const val DEAL_STEP = 22f
private const val DEAL_MOVE = 380f

/**
 * The "wash": face-down tiles are swirled around the table with both hands, settle into a
 * tidy block in a new order, then slide out: yours to the bottom, each opponent's to the
 * top, and the rest off to the right as the boneyard. Plays once each time [active] turns
 * true (a new hand on an empty table) and draws nothing otherwise.
 *
 * [yourTiles] and [opponentTiles] are how many tiles each player was dealt.
 */
@Composable
fun ShuffleAnimation(active: Boolean, yourTiles: Int, opponentTiles: List<Int>, modifier: Modifier = Modifier) {
    // Time into the animation; SHUFFLE_MS means finished, so nothing shows until a hand begins.
    val clock = remember { Animatable(SHUFFLE_MS.toFloat()) }
    var shuffles by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (active) {
            shuffles++
            clock.snapTo(0f)
            clock.animateTo(SHUFFLE_MS.toFloat(), tween(SHUFFLE_MS.toInt(), easing = LinearEasing))
        }
    }
    val running by remember { derivedStateOf { clock.value < SHUFFLE_MS } }
    if (!running) return

    // Fixed for the whole shuffle, even if a count changes while it plays.
    val plan = remember(shuffles) { WashPlan(Random(System.nanoTime()), yourTiles, opponentTiles) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // Tile width from the table size, so the block of 4 x 7 fits on any screen.
        val tilePx = minOf(wPx, hPx * 0.8f) * 0.068f
        val tileDp = with(density) { tilePx.toDp() }
        Box(Modifier.fillMaxSize()) {
            repeat(SET_SIZE) { i ->
                DominoTile(
                    0, 0,
                    half = tileDp,
                    faceDown = true,
                    elevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            val s = plan.at(i, clock.value, wPx, hPx, tilePx)
                            // Positions are from the table's top left; the tile is drawn centred.
                            translationX = s.x - wPx / 2
                            translationY = s.y - hPx / 2
                            rotationZ = s.turn
                            alpha = s.alpha
                            scaleX = s.scale
                            scaleY = s.scale
                        },
                )
            }
        }
    }
}

private class TileState(val x: Float, val y: Float, val turn: Float, val alpha: Float, val scale: Float)

/** One shuffle's random choices, and where each tile is at a given moment. */
private class WashPlan(rnd: Random, yourTiles: Int, opponentTiles: List<Int>) {
    /** Each tile's place in the block after the shuffle; also the order it's dealt in. */
    private val rank = (0 until SET_SIZE).shuffled(rnd)
    private val orbit = FloatArray(SET_SIZE) { 0.12f + rnd.nextFloat() * 0.26f }
    private val turns = FloatArray(SET_SIZE) { 0.7f + rnd.nextFloat() * 0.6f }
    private val spin = FloatArray(SET_SIZE) { (rnd.nextFloat() - 0.5f) * 900f }

    // Who gets the tile at each rank: you first, then each opponent, then the boneyard.
    private val mine = yourTiles.coerceIn(0, SET_SIZE)
    private val theirs = opponentTiles.map { it.coerceAtLeast(0) }

    fun at(i: Int, t: Float, w: Float, h: Float, tile: Float): TileState {
        val home = slot(i, w, h, tile)
        if (t < APPEAR_END) {
            val u = easeOut(phase(t, 0f, APPEAR_END))
            return TileState(home.first, home.second, 0f, u, 0.9f + 0.1f * u)
        }
        if (t < SWIRL_END) {
            val (x, y, turn) = swirl(i, easeInOut(phase(t, APPEAR_END, SWIRL_END)), w, h, tile)
            return TileState(x, y, turn, 1f, 1f)
        }
        val (ex, ey, eTurn) = swirl(i, 1f, w, h, tile)
        // A face-down tile looks the same either way up, so settle on the nearest half turn.
        val restTurn = (eTurn / 180f).roundToInt() * 180f
        val settled = slot(rank[i], w, h, tile)
        if (t < SETTLE_END) {
            val u = easeInOut(phase(t, SWIRL_END, SETTLE_END))
            return TileState(lerp(ex, settled.first, u), lerp(ey, settled.second, u), lerp(eTurn, restTurn, u), 1f, 1f)
        }
        val r = rank[i]
        val target = dealTarget(r, w, h, tile)
        val start = DEAL_START + (r % 14) * DEAL_STEP + if (target.third) 120f else 0f
        val u = easeInOut(phase(t, start, start + DEAL_MOVE))
        val x = lerp(settled.first, target.first, u)
        val y = lerp(settled.second, target.second, u)
        return TileState(x, y, restTurn, if (target.third) 1f - u else 1f, 1f)
    }

    /** Position in the neat 4 x 7 block in the middle of the table. */
    private fun slot(k: Int, w: Float, h: Float, tile: Float): Pair<Float, Float> {
        val col = k % 7
        val row = k / 7
        return Pair(w / 2 + (col - 3) * tile * 1.25f, h / 2 + (row - 1.5f) * tile * 2.2f)
    }

    /** Along the tile's circle around the middle at [s] (0..1), widening then closing again. */
    private fun swirl(i: Int, s: Float, w: Float, h: Float, tile: Float): Triple<Float, Float, Float> {
        val (hx, hy) = slot(i, w, h, tile)
        val cx = w / 2
        val cy = h / 2
        val a0 = atan2(hy - cy, hx - cx)
        val r0 = hypot(hx - cx, hy - cy)
        val angle = a0 + s * 2f * PI.toFloat() * turns[i]
        val radius = lerp(r0, orbit[i] * minOf(w, h * 1.25f), sin(PI.toFloat() * s))
        return Triple(cx + cos(angle) * radius, cy + sin(angle) * radius, s * spin[i])
    }

    /** Where the tile dealt at rank [r] leaves the table, and whether it fades (boneyard). */
    private fun dealTarget(r: Int, w: Float, h: Float, tile: Float): Triple<Float, Float, Boolean> {
        if (r < mine) return Triple(spread(w / 2, r, mine, w), h + tile * 2.5f, false)
        var first = mine
        theirs.forEachIndexed { j, n ->
            if (r < first + n) {
                // Opponents sit side by side along the top, like their chips above the table.
                val centre = w * (j + 1) / (theirs.size + 1)
                return Triple(spread(centre, r - first, n, w), -tile * 2.5f, false)
            }
            first += n
        }
        return Triple(w + tile * 2f, h * (0.3f + ((r - first) % 7) * 0.065f), true)
    }

    private fun spread(centre: Float, k: Int, n: Int, w: Float) = centre + (k - (n - 1) / 2f) * w * 0.07f
}

private fun phase(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
private fun easeOut(u: Float) = 1f - (1f - u).pow(3)
private fun easeInOut(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
