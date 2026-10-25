package de.wtzg.domino.server

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.Match
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.HandSummary
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.Visibility
import kotlinx.serialization.Serializable

/** A registered player. Only a hash of their token is stored. */
data class PlayerRecord(val id: String, val name: String, val tokenHash: String, val createdAt: Long)

/** A seat: a person ([playerId]), a computer ([ai]), or still free (both null). */
@Serializable
data class SeatRecord(val playerId: String? = null, val name: String = "", val ai: Difficulty? = null) {
    val isFree: Boolean get() = playerId == null && ai == null
}

/** Everything the server knows about a game, including every hand. Never sent as is. */
@Serializable
data class GameRecord(
    val id: String,
    val code: String,
    val pace: Pace,
    val rules: Rules,
    val seats: List<SeatRecord>,
    val status: GameStatus,
    val match: Match? = null,
    val lastHand: HandSummary? = null,
    val version: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val visibility: Visibility = Visibility.PRIVATE,
    /** Seconds per turn; 0 is no limit. */
    val turnLimitSeconds: Int = 0,
    /** The seat that ran out of time, which ended the game. */
    val timedOut: Int? = null,
    /** The seat whose player left during play, which ended the game. */
    val leftSeat: Int? = null,
    /** The lobby set up as a rematch of this game, once someone asked for one. */
    val rematchId: String? = null,
) {
    val hostSeat: Int get() = 0

    /**
     * When the person to move runs out of time, or null when no clock is running: no limit,
     * not playing, or a computer to move (they play at once). Every move saves the game, so
     * the clock starts again from [updatedAt] for each turn.
     */
    val turnDeadline: Long?
        get() {
            if (status != GameStatus.PLAYING || turnLimitSeconds <= 0) return null
            val round = match?.round ?: return null
            if (round.isOver || seats.getOrNull(round.turn)?.playerId == null) return null
            return updatedAt + turnLimitSeconds * 1000L
        }

    /** Listed in the open games: public, still in the lobby, with a seat free. */
    val isOpen: Boolean
        get() = visibility == Visibility.PUBLIC && status == GameStatus.LOBBY && seats.any { it.isFree }

    fun seatOf(playerId: String): Int = seats.indexOfFirst { it.playerId == playerId }
    val playerIds: List<String> get() = seats.mapNotNull { it.playerId }
}
