package de.wtzg.domino.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Difficulty(val label: String, val description: String) {
    EASY("Easy", "Plays any tile that fits"),
    NORMAL("Normal", "Grabs points and dumps heavy tiles first"),
    HARD("Hard", "Plays like the coach: counts tiles, reads you and blocks"),
}

/** One reason that makes a move better or worse. */
data class Factor(val points: Double, val text: String?)

data class Evaluation(val move: Move, val factors: List<Factor>) {
    val score: Double get() = factors.sumOf { it.points }

    /** The strongest arguments for this move, best first. */
    val pros: List<String>
        get() = factors.filter { it.points > 0 && it.text != null }.sortedByDescending { it.points }.mapNotNull { it.text }

    val cons: List<String>
        get() = factors.filter { it.points < 0 && it.text != null }.sortedBy { it.points }.mapNotNull { it.text }
}

/**
 * Rates moves using only what the player can know: their own hand, the table, and
 * what the others revealed by drawing or passing. Used by the Hard AI and the coach.
 */
object Strategy {

    /** Numbers [of] can't hold, as far as [viewer] can tell. */
    internal fun knownLacks(round: Round, viewer: Int, of: Int): Set<Int> {
        val visible = round.hands[viewer] + round.board.tiles
        val allSeen = (0..6).filter { n -> visible.count { it.has(n) } == 7 }
        return round.lacks[of] + allSeen
    }

    /** Tiles [viewer] hasn't seen: in other hands or the boneyard. */
    internal fun unseenTiles(round: Round, viewer: Int): List<Tile> {
        val visible = (round.hands[viewer] + round.board.tiles).toSet()
        return Tile.SET.filter { it !in visible }
    }

    internal fun unseenCount(round: Round, viewer: Int, n: Int): Int = unseenTiles(round, viewer).count { it.has(n) }

    fun evaluateAll(round: Round, player: Int = round.turn): List<Evaluation> =
        round.legalMoves(player).map { evaluate(round, player, it) }.sortedByDescending { it.score }

    fun evaluate(round: Round, player: Int, move: Move): Evaluation {
        val tile = move.tile
        val rules = round.rules
        val rest = round.hands[player] - tile
        val after = round.board.play(move)
        val scored = rules.playPoints(after.endCount())

        if (rest.isEmpty()) {
            return Evaluation(move, listOf(Factor(1000.0 + scored, "It's your last tile: play it and win the hand.")))
        }
        if (round.board.isEmpty && round.opening != null) {
            // Round.deal falls back to the heaviest tile when nobody has a double.
            val opener = if (tile.isDouble) "The highest double" else "Nobody has a double, so the heaviest tile"
            val text = if (scored > 0) "$opener opens the hand, and it scores $scored." else
                "$opener ${if (tile.isDouble) "always opens" else "opens"} the hand."
            return Evaluation(move, listOf(Factor(scored.toDouble(), text)))
        }

        val f = mutableListOf<Factor>()
        val next = round.next(player)
        val nextLacks = knownLacks(round, player, next)
        val nextTiles = round.hands[next].size
        val ends = after.openValues.distinct()
        val scoring = rules.mode.scoresDuringPlay

        if (scored > 0) {
            f += Factor(scored * 1.0, "It scores $scored: the open ends add up to ${after.endCount()}.")
        }

        // Heavy tiles cost you at the end of the hand. Matters less when points come from play.
        f += Factor(
            tile.pips * (if (scoring) 0.35 else 0.6),
            if (tile.pips >= 8 && !scoring) "It sheds ${tile.pips} pips. Heavy tiles cost you points if you lose the hand." else null,
        )
        if (tile.isDouble) {
            f += Factor(3.0, "Doubles fit only one number, so they get stuck easily. Play them while you can.")
        }

        val stuck = if (rules.mode.drawsFromBoneyard) "draw or pass" else "pass" // no drawing in Block
        val followUps = rest.count { t -> ends.any { t.has(it) } }
        f += if (followUps == 0) {
            Factor(-6.0, "It leaves you no tile for any end, so you'll likely have to $stuck next turn.")
        } else {
            Factor(minOf(followUps, 4) * 1.2, if (followUps >= 2) "You keep $followUps tiles that fit the new ends." else null)
        }

        // Blocking the next player matters more the closer they are to going out.
        val urgency = if (nextTiles <= 2) 2.0 else 1.0
        val who = if (round.players == 2) "Your opponent" else "The next player"
        val lacking = ends.filter { it in nextLacks }
        when {
            lacking.size == ends.size -> f += Factor(
                8.0 * urgency,
                "Every open end (${ends.joinToString(", ")}) is a number ${who.lowercase()} doesn't have, so they have to $stuck.",
            )
            lacking.isNotEmpty() -> f += Factor(
                2.5 * urgency,
                "Afterwards an end shows ${lacking.first()}, and ${who.lowercase()} has shown they have no ${lacking.first()}s.",
            )
        }

        // Owning a number: if you hold most of the unseen tiles of it, an end of it is yours.
        for (e in ends) {
            val mine = rest.count { it.has(e) }
            if (mine >= 2) {
                val others = unseenCount(round, player, e)
                f += Factor(
                    mine * 1.0 + if (others <= 1) 1.5 else 0.0,
                    "You hold $mine more ${e}s, so the $e end is likely to stay yours.",
                )
            }
        }

        if (nextTiles <= 2 && !scoring) {
            val risky = ends.filter { it !in nextLacks && unseenCount(round, player, it) >= 3 }
            if (risky.isNotEmpty()) {
                f += Factor(-2.0, "$who is close to going out, and a ${risky.first()} end may give them a play.")
            }
        }

        if (scoring) gift(round, player, after, next, nextLacks)?.let { f += it }
        return Evaluation(move, f)
    }

    /**
     * Fives/Threes: how many points the next player could score after this move,
     * weighted by the chance they hold the tile that does it.
     */
    private fun gift(round: Round, player: Int, after: Board, next: Int, nextLacks: Set<Int>): Factor? {
        // Tiles the next player could hold: unseen, and without a number they've shown they lack.
        val candidates = unseenTiles(round, player).filter { t -> t.a !in nextLacks && t.b !in nextLacks }
        if (candidates.isEmpty()) return null
        val chance = (round.hands[next].size.toDouble() / candidates.size).coerceAtMost(1.0)
        var expected = 0.0
        var worst: Pair<Tile, Int>? = null
        for (t in candidates) {
            val best = after.openArms.filter { after.canPlay(t, it) }
                .maxOfOrNull { round.rules.playPoints(after.play(Move(t, it)).endCount()) } ?: 0
            if (best > 0) {
                expected += best * chance
                if (worst == null || best > worst.second) worst = t to best
            }
        }
        if (expected < 0.5) return null
        val w = worst
        val text = if (w != null && w.second >= 10) {
            "It sets up points for the next player: ${w.first} would score ${w.second}."
        } else null
        return Factor(-0.8 * expected, text)
    }

    fun chooseMove(round: Round, difficulty: Difficulty, random: Random = Random): Move? {
        val moves = round.legalMoves()
        if (moves.isEmpty()) return null
        return when (difficulty) {
            Difficulty.EASY -> moves.random(random)
            Difficulty.NORMAL -> moves.maxBy {
                round.pointsFor(it) * 100 + it.tile.pips * 10 + (if (it.tile.isDouble) 5 else 0) + random.nextInt(3)
            }
            Difficulty.HARD -> moves.maxBy { evaluate(round, round.turn, it).score + random.nextDouble() * 0.01 }
        }
    }

    /** Names a move by the number it goes on; the line snakes, so "left" can sit anywhere on screen. */
    fun describe(move: Move, board: Board): String {
        if (board.isEmpty) return "${move.tile}"
        val n = board.endValue(move.arm)
        val same = board.openArms.count { board.endValue(it) == n }
        return if (same > 1) "${move.tile} on a $n end" else "${move.tile} on the $n end"
    }
}
