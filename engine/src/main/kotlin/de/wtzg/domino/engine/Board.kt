package de.wtzg.domino.engine

import kotlinx.serialization.Serializable

/**
 * The tiles on the table: an opening tile with arms growing east and west, and
 * (when spinners are allowed and the opener is a double) north and south too.
 */
@Serializable
data class Board(
    val spinnerAllowed: Boolean = false,
    val start: Tile? = null,
    /** Number on the west side of the opening tile. */
    val startWest: Int = 0,
    /** Number on the east side of the opening tile. */
    val startEast: Int = 0,
    val east: List<Link> = emptyList(),
    val west: List<Link> = emptyList(),
    val north: List<Link> = emptyList(),
    val south: List<Link> = emptyList(),
) {
    val isEmpty: Boolean get() = start == null

    /** The opening double can be built on from all four sides. */
    val hasSpinner: Boolean get() = spinnerAllowed && start?.isDouble == true

    /** North and south open once both sides of the spinner have a tile. */
    val northSouthOpen: Boolean get() = hasSpinner && east.isNotEmpty() && west.isNotEmpty()

    val size: Int get() = if (isEmpty) 0 else 1 + east.size + west.size + north.size + south.size

    val tiles: List<Tile>
        get() = listOfNotNull(start) + (east + west + north + south).map { it.tile }

    fun arm(arm: Arm): List<Link> = when (arm) {
        Arm.EAST -> east
        Arm.WEST -> west
        Arm.NORTH -> north
        Arm.SOUTH -> south
    }

    /** Arms a tile can be played on right now. */
    val openArms: List<Arm>
        get() = when {
            isEmpty -> emptyList()
            northSouthOpen -> Arm.entries
            else -> listOf(Arm.EAST, Arm.WEST)
        }

    /** The number showing at the end of [arm]. */
    fun endValue(arm: Arm): Int {
        arm(arm).lastOrNull()?.let { return it.outer }
        val s = checkNotNull(start) { "Empty board" }
        return when (arm) {
            Arm.EAST -> startEast
            Arm.WEST -> startWest
            Arm.NORTH, Arm.SOUTH -> s.a
        }
    }

    /** The numbers you can play on, one per open arm. */
    val openValues: List<Int> get() = openArms.map(::endValue)

    /**
     * Pip total of the open ends, as counted in All Fives and All Threes.
     * A double at an end counts both halves. The spinner's north and south sides
     * only count once a tile is on them.
     */
    fun endCount(): Int {
        val s = start ?: return 0
        if (east.isEmpty() && west.isEmpty()) return s.pips
        var total = 0
        for (arm in listOf(Arm.EAST, Arm.WEST)) {
            val last = arm(arm).lastOrNull()
            total += when {
                last != null -> last.endPips()
                s.isDouble -> s.pips // an untouched side of the opening double: the whole double is an end
                else -> endValue(arm)
            }
        }
        if (northSouthOpen) {
            for (arm in listOf(Arm.NORTH, Arm.SOUTH)) {
                val last = arm(arm).lastOrNull() ?: continue
                total += last.endPips()
            }
        }
        return total
    }

    /** A double at an end counts both halves. */
    private fun Link.endPips(): Int = if (tile.isDouble) outer * 2 else outer

    fun canPlay(tile: Tile, arm: Arm): Boolean =
        isEmpty || (arm in openArms && tile.has(endValue(arm)))

    fun play(move: Move): Board {
        val t = move.tile
        if (isEmpty) return copy(start = t, startWest = t.a, startEast = t.b)
        require(canPlay(t, move.arm)) { "$t doesn't fit the ${move.arm} end" }
        val v = endValue(move.arm)
        val link = Link(t, v, t.other(v))
        return when (move.arm) {
            Arm.EAST -> copy(east = east + link)
            Arm.WEST -> copy(west = west + link)
            Arm.NORTH -> copy(north = north + link)
            Arm.SOUTH -> copy(south = south + link)
        }
    }
}
