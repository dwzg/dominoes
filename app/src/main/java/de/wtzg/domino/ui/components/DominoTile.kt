package de.wtzg.domino.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.wtzg.domino.data.TileFace
import de.wtzg.domino.ui.theme.LocalTileFace

private val Ivory = Color(0xFFFBF8EF)
private val Pip = Color(0xFF263238)

// The back of a tile: a fixed deep slate that stands out on every felt and on light and dark
// screens alike (the theme colour blends into the green felt), with a brass pin like a real tile.
private val Back = Color(0xFF1F2A36)
private val Brass = Color(0xFFC9A54A)

/** One colour per number for the coloured faces, dark enough to read on ivory. */
private val NUMBER_COLORS = listOf(
    Color(0xFF607D8B), // 0
    Color(0xFFC62828), // 1
    Color(0xFFE65100), // 2
    Color(0xFF2E7D32), // 3
    Color(0xFF1565C0), // 4
    Color(0xFF6A1B9A), // 5
    Color(0xFF4E342E), // 6
)

private fun colorOf(n: Int, face: TileFace): Color =
    if (face == TileFace.DOTS) Pip else NUMBER_COLORS.getOrElse(n) { Pip }

/** Pip positions within one square half of a tile (x, y in 0..1). */
private val PIPS: Map<Int, List<Pair<Float, Float>>> = mapOf(
    0 to emptyList(),
    1 to listOf(.5f to .5f),
    2 to listOf(.27f to .27f, .73f to .73f),
    3 to listOf(.25f to .25f, .5f to .5f, .75f to .75f),
    4 to listOf(.27f to .27f, .73f to .27f, .27f to .73f, .73f to .73f),
    5 to listOf(.25f to .25f, .75f to .25f, .5f to .5f, .25f to .75f, .75f to .75f),
    6 to listOf(.27f to .22f, .27f to .5f, .27f to .78f, .73f to .22f, .73f to .5f, .73f to .78f),
)

/**
 * A domino drawn with pips. [first] is the top (vertical) or left (horizontal) half.
 */
@Composable
fun DominoTile(
    first: Int,
    second: Int,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    half: Dp = 28.dp,
    faceDown: Boolean = false,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    /** Read out whether the tile is picked, for tiles that can be (e.g. in your hand). */
    selected: Boolean? = null,
    /** Read out after the tile's numbers, e.g. "doesn't fit". */
    stateDescription: String? = null,
) {
    val face = LocalTileFace.current
    val shape = RoundedCornerShape(half * 0.18f)
    val ring = if (highlighted) MaterialTheme.colorScheme.tertiary else Color(0xFF9E9E9E)
    Box(
        modifier
            .size(if (vertical) half else half * 2, if (vertical) half * 2 else half)
            .alpha(if (dimmed) 0.4f else 1f)
            .shadow(elevation, shape)
            .clip(shape)
            .background(if (faceDown) Back else Ivory)
            .border(if (highlighted) 3.dp else 1.dp, ring, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = if (faceDown) "Hidden tile" else "$first-$second"
                stateDescription?.let { this.stateDescription = it }
                selected?.let { this.selected = it }
            },
    ) {
        if (faceDown) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brass, minOf(size.width, size.height) * 0.11f)
            }
        } else {
            TileDivider(vertical)
            if (face == TileFace.NUMBERS) {
                Digits(first, second, vertical, half)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val h = if (vertical) size.width else size.height
                    val r = h * 0.085f
                    listOf(first, second).forEachIndexed { i, n ->
                        val ox = if (vertical) 0f else h * i
                        val oy = if (vertical) h * i else 0f
                        PIPS[n]?.forEach { (x, y) ->
                            // Turn the pattern on horizontal tiles so a 6 shows two rows of three.
                            val (px, py) = if (vertical) x to y else y to x
                            drawCircle(colorOf(n, face), r, Offset(ox + px * h, oy + py * h))
                        }
                    }
                }
            }
        }
    }
}

/** The line across the middle of a tile. */
@Composable
private fun TileDivider(vertical: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val h = if (vertical) size.width else size.height
        if (vertical) {
            drawLine(Pip.copy(alpha = .5f), Offset(h * .15f, h), Offset(h * .85f, h), strokeWidth = h * .03f)
        } else {
            drawLine(Pip.copy(alpha = .5f), Offset(h, h * .15f), Offset(h, h * .85f), strokeWidth = h * .03f)
        }
    }
}

/** Both halves as coloured digits, upright whichever way the tile lies. */
@Composable
private fun Digits(first: Int, second: Int, vertical: Boolean, half: Dp) {
    // Sized from the tile, not the user's font scale, so digits always fit their half.
    val size = with(LocalDensity.current) { (half * 0.6f).toSp() }
    val digit = @Composable { n: Int ->
        Box(Modifier.size(half), contentAlignment = Alignment.Center) {
            Text(
                "$n",
                color = NUMBER_COLORS.getOrElse(n) { Pip },
                fontSize = size,
                lineHeight = size,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
    if (vertical) {
        Column { digit(first); digit(second) }
    } else {
        Row { digit(first); digit(second) }
    }
}
