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
) {
    val hostSeat: Int get() = 0

    /** Listed in the open games: public, still in the lobby, with a seat free. */
    val isOpen: Boolean
        get() = visibility == Visibility.PUBLIC && status == GameStatus.LOBBY && seats.any { it.isFree }

    fun seatOf(playerId: String): Int = seats.indexOfFirst { it.playerId == playerId }
    val playerIds: List<String> get() = seats.mapNotNull { it.playerId }
}
