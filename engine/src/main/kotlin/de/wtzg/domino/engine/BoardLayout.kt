package de.wtzg.domino.engine

import kotlin.math.abs

/**
 * A tile's spot on the table, in units of half a tile (a tile is 2×1).
 * [first] is shown on the top (vertical) or left (horizontal) half.
 */
data class Laid(
    val tile: Tile,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val first: Int,
    val second: Int,
) {
    val vertical: Boolean get() = h > w
}

/**
 * Lays the tiles out like on a real table. Tiles never move once placed, so new
 * ones can be animated in, and nothing overlaps (covered by tests).
 *
 * - Without a spinner the line snakes: the east arm runs right and turns downwards
 *   at the edge, the west arm runs left and turns upwards.
 * - With a spinner the opening double sits in the middle of a cross: north runs up,
 *   south runs down, and east and west run sideways, then turn down at the edges.
 * - Doubles lie crosswise, except right after a corner.
 */
object BoardLayout {
    /** Rows stay within ±this many units of the centre (a corner tile may stick out by one). */
    const val HALF_WIDTH = 5f

    fun layout(board: Board, halfWidth: Float = HALF_WIDTH): List<Laid> {
        val s = board.start ?: return emptyList()
        val out = mutableListOf(
            if (s.isDouble) Laid(s, -0.5f, -1f, 1f, 2f, s.a, s.a)
            else Laid(s, -1f, -0.5f, 2f, 1f, board.startWest, board.startEast)
        )
        val edge = if (s.isDouble) 0.5f else 1f
        if (board.hasSpinner) {
            Walker(edge, 0f, 1, 0, halfWidth).run(board.east, out)
            Walker(-edge, 0f, -1, 0, halfWidth).run(board.west, out)
            Walker(0f, -1f, 0, -1, turnAt = null).run(board.north, out)
            Walker(0f, 1f, 0, 1, turnAt = null).run(board.south, out)
        } else {
            snake(board.east, edge, dir = 1, vdir = 1, halfWidth, out)
            snake(board.west, -edge, dir = -1, vdir = -1, halfWidth, out)
        }
        return out
    }

    /** Where [move] would land if played now. */
    fun slot(board: Board, move: Move, halfWidth: Float = HALF_WIDTH): Laid =
        layout(board.play(move), halfWidth).first { it.tile == move.tile }

    /** East/west arm of a spinner-less line: runs sideways and U-turns one row further out. */
    private fun snake(links: List<Link>, startX: Float, dir: Int, vdir: Int, halfWidth: Float, out: MutableList<Laid>) {
        var cx = startX // x of the open end
        var cy = 0f // centre line of the current row
        var heading = dir
        var afterTurn = false
        for (link in links) {
            val (tile, match, outer) = link
            val crosswise = tile.isDouble && !afterTurn
            val needed = if (crosswise) 1f else 2f
            val fits = if (heading > 0) cx + needed <= halfWidth else cx - needed >= -halfWidth
            if (!fits) {
                // Corner: stand the tile up, matching half level with the row,
                // then continue the other way one row further out.
                val x = if (heading > 0) cx else cx - 1
                val y = if (vdir > 0) cy - 0.5f else cy - 1.5f
                val (top, bottom) = if (vdir > 0) match to outer else outer to match
                out += Laid(tile, x, y, 1f, 2f, top, bottom)
                cx += heading
                cy += 2f * vdir
                heading = -heading
                afterTurn = true
                continue
            }
            if (crosswise) {
                val x = if (heading > 0) cx else cx - 1
                out += Laid(tile, x, cy - 1f, 1f, 2f, match, match)
                cx += heading
            } else {
                val x = if (heading > 0) cx else cx - 2
                val (l, r) = if (heading > 0) match to outer else outer to match
                out += Laid(tile, x, cy - 0.5f, 2f, 1f, l, r)
                cx += 2 * heading
            }
            afterTurn = false
        }
    }

    /**
     * Walks one arm of a spinner cross from (px, py) heading (dx, dy). Sideways arms
     * turn down once they would pass ±[turnAt] and then run straight; north and south
     * never turn (null).
     */
    private class Walker(
        var px: Float,
        var py: Float,
        var dx: Int,
        var dy: Int,
        val turnAt: Float?,
    ) {
        private var afterTurn = false

        fun run(links: List<Link>, out: MutableList<Laid>) {
            for (link in links) out += place(link)
        }

        private fun place(link: Link): Laid {
            val (tile, inner, outer) = link
            val crosswise = tile.isDouble && !afterTurn
            val length = if (crosswise) 1f else 2f
            if (dx != 0 && turnAt != null && abs(px + dx * length) > turnAt) return corner(link)
            afterTurn = false
            return if (crosswise) {
                // Along the heading 1 unit, across it 2 units.
                if (dx != 0) {
                    val x = if (dx > 0) px else px - 1
                    Laid(tile, x, py - 1f, 1f, 2f, inner, inner).also { px += dx }
                } else {
                    val y = if (dy > 0) py else py - 1
                    Laid(tile, px - 1f, y, 2f, 1f, inner, inner).also { py += dy }
                }
            } else if (dx != 0) {
                val x = if (dx > 0) px else px - 2
                val (l, r) = if (dx > 0) inner to outer else outer to inner
                Laid(tile, x, py - 0.5f, 2f, 1f, l, r).also { px += 2 * dx }
            } else {
                val y = if (dy > 0) py else py - 2
                val (top, bottom) = if (dy > 0) inner to outer else outer to inner
                Laid(tile, px - 0.5f, y, 1f, 2f, top, bottom).also { py += 2 * dy }
            }
        }

        /** Stand the tile up at the edge, inner half level with the row, and head down. */
        private fun corner(link: Link): Laid {
            val x = if (dx > 0) px else px - 1
            val laid = Laid(link.tile, x, py - 0.5f, 1f, 2f, link.inner, link.outer)
            px += dx * 0.5f
            py += 1.5f
            dx = 0
            dy = 1
            afterTurn = true
            return laid
        }
    }
}
