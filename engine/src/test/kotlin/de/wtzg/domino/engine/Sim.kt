package de.wtzg.domino.engine

import kotlin.random.Random

/** Plays a hand to the end with the given AI levels, calling [each] after every step. */
fun playOut(
    start: Round,
    random: Random,
    levels: (Int) -> Difficulty = { Difficulty.EASY },
    each: (Round) -> Unit = {},
): Round {
    var r = start
    var steps = 0
    while (!r.isOver) {
        r = when {
            r.canDraw -> r.draw()
            r.mustPass -> r.pass()
            else -> r.play(Strategy.chooseMove(r, levels(r.turn), random)!!)
        }
        each(r)
        check(++steps < 500) { "Hand never ends" }
    }
    return r
}

/** High number first, like the tiles in [Tile.SET], so they compare equal to dealt ones. */
fun t(a: Int, b: Int) = Tile(maxOf(a, b), minOf(a, b))
