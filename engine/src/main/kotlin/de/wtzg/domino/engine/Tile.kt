package de.wtzg.domino.engine

import kotlinx.serialization.Serializable

/**
 * A double-six domino. Equality depends on order (6|1 != 1|6), so tiles always come
 * from [SET], high number first.
 */
@Serializable
data class Tile(val a: Int, val b: Int) {
    init {
        require(a in 0..6 && b in 0..6) { "Pips must be 0..6: $a|$b" }
    }

    val pips: Int get() = a + b
    val isDouble: Boolean get() = a == b
    fun has(n: Int): Boolean = a == n || b == n
    fun other(n: Int): Int = if (a == n) b else a
    override fun toString(): String = "$a|$b"

    companion object {
        /** All 28 tiles, high number first. */
        val SET: List<Tile> = (0..6).flatMap { lo -> (lo..6).map { hi -> Tile(hi, lo) } }
    }
}

/** Directions the line can grow from the opening tile. North and south only exist with a spinner. */
@Serializable
enum class Arm { EAST, WEST, NORTH, SOUTH }

@Serializable
data class Move(val tile: Tile, val arm: Arm)

/** A tile on an arm. [inner] touches the tile before it (towards the centre), [outer] is the open side. */
@Serializable
data class Link(val tile: Tile, val inner: Int, val outer: Int)
