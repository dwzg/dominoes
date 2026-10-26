package de.wtzg.domino.engine

import kotlin.random.Random

/**
 * A "find the best move" position for player 0, taken from a real game between
 * Hard AIs. There is a clear best move, and the coach can explain why.
 */
data class Puzzle(val round: Round, val ranked: List<Evaluation>) {
    val best: Evaluation get() = ranked.first()

    /** Moves close enough to the best one count as solved. */
    fun isGood(move: Move): Boolean {
        val e = ranked.firstOrNull { it.move == move } ?: return false
        return e.score >= best.score - SOLVED_MARGIN
    }

    companion object {
        private const val SOLVED_MARGIN = 1.0
    }
}

object Puzzles {
    /** Minimum lead of the best move over the next-best different tile. */
    private const val MIN_GAP = 2.0

    /** Hands to play through before giving up; a puzzle nearly always turns up in the first few. */
    private const val MAX_DEALS = 500

    fun generate(mode: GameMode, random: Random = Random, players: Int = 2): Puzzle {
        val rules = Rules(mode, players)
        repeat(MAX_DEALS) {
            var r = Round.deal(rules, random)
            while (!r.isOver) {
                if (r.turn == 0 && r.board.size >= 3 && r.hands[0].size >= 3) {
                    candidate(r)?.let { if (random.nextInt(3) == 0) return it }
                }
                r = when {
                    r.canDraw -> r.draw()
                    r.mustPass -> r.pass()
                    else -> r.play(Strategy.chooseMove(r, Difficulty.HARD, random)!!)
                }
            }
        }
        error("No puzzle found")
    }

    private fun candidate(r: Round): Puzzle? {
        val ranked = Strategy.evaluateAll(r, 0)
        if (ranked.map { it.move.tile }.distinct().size < 3) return null
        val best = ranked.first()
        if (best.pros.isEmpty()) return null
        val runnerUp = ranked.firstOrNull { it.move.tile != best.move.tile } ?: return null
        // Other placements of the best tile must not be just as good, or the answer is ambiguous.
        val sameTileClose = ranked.drop(1).any { it.move.tile == best.move.tile && it.score > best.score - MIN_GAP }
        if (best.score - runnerUp.score < MIN_GAP || sameTileClose) return null
        return Puzzle(r, ranked)
    }
}
