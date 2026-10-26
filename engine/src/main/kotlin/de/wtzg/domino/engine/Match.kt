package de.wtzg.domino.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A match: hands are played until someone reaches the target. In Fives and Threes
 * the match can end mid-hand, the moment a play takes someone to the target.
 */
@Serializable
data class Match(
    val rules: Rules,
    /** Points banked from finished hands. */
    val banked: List<Int>,
    val round: Round,
    val handNumber: Int = 1,
    val winner: Int? = null,
) {
    val isOver: Boolean get() = winner != null

    /** Everyone's score including points from the current hand. */
    val scores: List<Int>
        get() = banked.indices.map { i ->
            banked[i] + round.points[i] + (round.result?.takeIf { it.winner == i }?.endPoints ?: 0)
        }

    // Once someone has won, more plays could change the scores and the winner.
    fun play(move: Move): Match {
        check(!isOver) { "Match over" }
        return after(round.play(move))
    }

    fun draw(): Match {
        check(!isOver) { "Match over" }
        return after(round.draw())
    }

    fun pass(): Match {
        check(!isOver) { "Match over" }
        return after(round.pass())
    }

    private fun after(r: Round): Match {
        val m = copy(round = r)
        val s = m.scores
        val top = s.max()
        // A tie at the top isn't a win: play goes on, into the next hand if need be.
        val reached = top >= rules.target && s.count { it == top } == 1
        // Mid-hand wins only happen in scoring games; otherwise wait for the hand to end.
        return if (reached && (r.isOver || rules.mode.scoresDuringPlay)) m.copy(winner = s.indexOf(top)) else m
    }

    /** Banks this hand's points and deals the next one. */
    fun nextHand(random: Random = Random): Match {
        check(round.isOver && !isOver) { "Hand still running or match over" }
        return copy(banked = scores, round = Round.deal(rules, random), handNumber = handNumber + 1)
    }

    companion object {
        fun start(rules: Rules, random: Random = Random) =
            Match(rules, List(rules.players) { 0 }, Round.deal(rules, random))
    }
}
