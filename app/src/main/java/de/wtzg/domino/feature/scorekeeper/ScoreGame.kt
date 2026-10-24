package de.wtzg.domino.feature.scorekeeper

import kotlinx.serialization.Serializable

/** How a real-life game tracked in the score keeper is won. */
@Serializable
enum class ScoreMode {
    /** Highest score wins once someone reaches [ScoreGame.limit] points (Block, Draw, All Fives, ...). */
    FIRST_TO_TARGET,

    /** Lowest total wins after [ScoreGame.limit] rounds (Mexican Train, ...). */
    LOWEST_AFTER_ROUNDS,
}

/**
 * A real-life game in the score keeper. Field names match the version 1 JSON so saved
 * games keep loading.
 */
@Serializable
data class ScoreGame(
    val id: Long,
    val players: List<String>,
    val mode: ScoreMode,
    val limit: Int,
    val rounds: List<List<Int>> = emptyList(),
    val startedAt: Long,
    val finishedAt: Long? = null,
    /** Who starts the first round. */
    val firstStarter: Int = 0,
    /** The start moves to the next player every round; otherwise the round's winner starts the next one. */
    val rotateStarter: Boolean = false,
) {
    val totals: List<Int>
        get() = players.indices.map { p -> rounds.sumOf { it.getOrElse(p) { 0 } } }

    /** Index of the player currently in the lead, or null when tied for the lead. */
    val leaderIndex: Int?
        get() {
            if (rounds.isEmpty()) return null
            val t = totals
            val best = if (mode == ScoreMode.FIRST_TO_TARGET) t.max() else t.min()
            return t.indexOf(best).takeIf { t.count { it == best } == 1 }
        }

    /**
     * Index of the winner, or null while the game is still running.
     * A tie for first place means another round must be played.
     */
    val winnerIndex: Int?
        get() {
            val over = when (mode) {
                ScoreMode.FIRST_TO_TARGET -> totals.any { it >= limit }
                ScoreMode.LOWEST_AFTER_ROUNDS -> rounds.size >= limit
            }
            return if (over) leaderIndex else null
        }

    val isFinished: Boolean get() = winnerIndex != null

    /** Who starts the round being played now. */
    val currentStarter: Int get() = starterOf(rounds.size)

    /**
     * Who starts round [index] (0-based). With rotation it moves one seat per round.
     * Otherwise the previous round's winner starts; after a tied round the same player
     * starts again.
     */
    fun starterOf(index: Int): Int {
        val n = players.size
        if (rotateStarter) return (firstStarter + index) % n
        var starter = firstStarter.coerceIn(0, n - 1)
        for (i in 0 until minOf(index, rounds.size)) {
            starter = roundWinner(rounds[i]) ?: starter
        }
        return starter
    }

    /** The best score of a round (highest, or lowest when low wins), or null if tied. */
    private fun roundWinner(round: List<Int>): Int? {
        val best = if (mode == ScoreMode.FIRST_TO_TARGET) round.max() else round.min()
        return round.indexOf(best).takeIf { round.count { it == best } == 1 }
    }

    fun addRound(scores: List<Int>): ScoreGame = copy(rounds = rounds + listOf(normalize(scores)))

    fun updateRound(index: Int, scores: List<Int>): ScoreGame =
        copy(rounds = rounds.toMutableList().also { it[index] = normalize(scores) })

    fun deleteRound(index: Int): ScoreGame =
        copy(rounds = rounds.toMutableList().also { it.removeAt(index) })

    private fun normalize(scores: List<Int>): List<Int> =
        players.indices.map { scores.getOrElse(it) { 0 } }
}
