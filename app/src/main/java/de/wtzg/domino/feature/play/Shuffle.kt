package de.wtzg.domino.feature.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.wtzg.domino.ui.components.DominoTile
import kotlin.random.Random

/** How long the shuffle at the start of a hand takes. Dealing and the computer wait for it. */
const val SHUFFLE_MS = 1200L

private const val TILES = 14

/** Spots a tile passes through: from the pile, scattered twice, back to the pile. */
private class ShufflePath(val x: FloatArray, val y: FloatArray, val turn: FloatArray)

/**
 * Face-down tiles swirling around the felt, played once each time [active] turns true
 * (a new hand with an empty table). Draws nothing the rest of the time.
 */
@Composable
fun ShuffleAnimation(active: Boolean, modifier: Modifier = Modifier) {
    // 1 = finished. Starts finished so nothing shows until a hand begins.
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SHUFFLE_MS.toInt(), easing = LinearEasing))
        }
    }
    val running by remember { derivedStateOf { progress.value < 1f } }
    if (!running) return

    val paths = remember {
        val rnd = Random(System.nanoTime())
        List(TILES) {
            // Fractions of the table size, centred on the middle.
            fun spot() = (rnd.nextFloat() - 0.5f) * 0.7f
            ShufflePath(
                x = floatArrayOf(0f, spot(), spot(), 0f),
                y = floatArrayOf(0f, spot(), spot(), 0f),
                turn = floatArrayOf(rnd.nextFloat() * 360f, rnd.nextFloat() * 360f, rnd.nextFloat() * 360f, 90f),
            )
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val h = with(LocalDensity.current) { maxHeight.toPx() }
        Box(Modifier.fillMaxSize()) {
            paths.forEach { path ->
                DominoTile(
                    0, 0,
                    half = 14.dp,
                    faceDown = true,
                    elevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            // Three moves: out of the pile, across the table, back together.
                            val t = progress.value * 3f
                            val leg = t.toInt().coerceAtMost(2)
                            val f = FastOutSlowInEasing.transform((t - leg).coerceIn(0f, 1f))
                            fun lerp(a: FloatArray) = a[leg] + (a[leg + 1] - a[leg]) * f
                            translationX = lerp(path.x) * w
                            translationY = lerp(path.y) * h
                            rotationZ = lerp(path.turn)
                            // Fade out once the tiles are back in the pile.
                            alpha = ((1f - progress.value) / 0.15f).coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
}
