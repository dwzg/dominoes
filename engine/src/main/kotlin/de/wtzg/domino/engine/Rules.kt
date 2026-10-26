package de.wtzg.domino.engine

import kotlinx.serialization.Serializable

/**
 * The supported games, each with its usual target as the default.
 *
 * @property drawsFromBoneyard players who can't play draw (Draw, Fives, Threes) instead of passing (Block)
 * @property scoringMultiple points are scored during play when the open ends total a multiple of this (0 = never)
 * @property spinner the opening double can be built on from four sides
 */
@Serializable
enum class GameMode(
    val title: String,
    val tagline: String,
    val drawsFromBoneyard: Boolean,
    val scoringMultiple: Int,
    val spinner: Boolean,
    val defaultTarget: Int,
) {
    DRAW("Draw", "The classic. Can't play? Draw from the boneyard.", true, 0, false, 60),
    BLOCK("Block", "No drawing. If you can't play, you pass.", false, 0, false, 60),
    ALL_FIVES("All Fives", "Score when the open ends add up to a multiple of 5.", true, 5, true, 100),
    ALL_THREES("All Threes", "Score when the open ends add up to a multiple of 3.", true, 3, true, 90),
    ;

    val scoresDuringPlay: Boolean get() = scoringMultiple > 0
}

@Serializable
data class Rules(
    val mode: GameMode,
    /** 2 to 4 players. Player 0 is you. */
    val players: Int = 2,
    val target: Int = mode.defaultTarget,
) {
    init {
        require(players in 2..4) { "2 to 4 players" }
        require(target > 0)
    }

    /** 7 tiles each in Block or with two players, otherwise 5, so there is a boneyard to draw from. */
    val handSize: Int get() = if (mode == GameMode.BLOCK || players == 2) 7 else 5

    /** End-of-hand points are rounded to the scoring multiple in Fives and Threes (e.g. 12 → 10). */
    fun roundEndPoints(points: Int): Int {
        val m = mode.scoringMultiple
        return if (m == 0) points else ((points + m / 2) / m) * m
    }

    /** Points scored by a play that leaves [endCount] pips on the open ends. */
    fun playPoints(endCount: Int): Int {
        val m = mode.scoringMultiple
        return if (m > 0 && endCount > 0 && endCount % m == 0) endCount else 0
    }
}
