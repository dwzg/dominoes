package de.wtzg.domino.protocol

import de.wtzg.domino.engine.Board
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.HandResult
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.engine.Tile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings used on both sides of the wire. */
val ProtocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

/** Live: everyone plays at the same time. Turn-based: take your turn whenever you like. */
@Serializable
enum class Pace { LIVE, TURN_BASED }

@Serializable
enum class GameStatus { LOBBY, PLAYING, FINISHED }

/** Who can find a game in its lobby: only people with the code, or anyone in the open games list. */
@Serializable
enum class Visibility { PRIVATE, PUBLIC }

// --- Players ---------------------------------------------------------------------------------

@Serializable
data class RegisterRequest(val name: String)

/** Who you are online. Keep [token] secret: it's your only key to your games. */
@Serializable
data class Registration(val playerId: String, val token: String, val name: String)

@Serializable
data class RenameRequest(val name: String)

// --- Games -----------------------------------------------------------------------------------

@Serializable
data class CreateGameRequest(
    val mode: GameMode,
    val players: Int,
    val target: Int,
    val pace: Pace,
    /** Apps from before public games don't send this; their games stay private. */
    val visibility: Visibility = Visibility.PRIVATE,
)

/** The host lists the lobby in the open games, or takes it off again. */
@Serializable
data class VisibilityRequest(val visibility: Visibility)

@Serializable
data class JoinRequest(val code: String)

/** Starts the game; empty seats are filled with computer players of [difficulty]. */
@Serializable
data class StartRequest(val difficulty: Difficulty = Difficulty.NORMAL)

@Serializable
sealed interface Action {
    @Serializable @SerialName("play")
    data class Play(val move: Move) : Action

    @Serializable @SerialName("draw")
    data object Draw : Action

    @Serializable @SerialName("pass")
    data object Pass : Action

    /** Leave the game. In the lobby your seat is freed; during play a computer takes over. */
    @Serializable @SerialName("leave")
    data object Leave : Action
}

/** [version] must match the game's current version, so stale actions are rejected. */
@Serializable
data class ActionRequest(val action: Action, val version: Int)

@Serializable
data class ApiError(val message: String)

/** One seat at the table as everybody may see it. */
@Serializable
data class SeatView(
    val name: String,
    /** Null while the seat is still free in the lobby. */
    val playerId: String?,
    val isAi: Boolean,
    val tiles: Int,
    val score: Int,
    /** Has the app open on this game right now. */
    val online: Boolean,
)

/** Public record of what happened. Drawn tiles stay secret; only the count is shown. */
@Serializable
sealed interface PublicEvent {
    val seat: Int

    @Serializable @SerialName("played")
    data class Played(override val seat: Int, val move: Move, val points: Int) : PublicEvent

    @Serializable @SerialName("drew")
    data class Drew(override val seat: Int, val ends: List<Int>) : PublicEvent

    @Serializable @SerialName("passed")
    data class Passed(override val seat: Int, val ends: List<Int>) : PublicEvent
}

/** A finished hand with every hand revealed, for the end-of-hand summary. */
@Serializable
data class HandSummary(
    val handNumber: Int,
    val result: HandResult,
    val hands: List<List<Tile>>,
    /** The table as the hand ended. */
    val board: Board,
    /** Scores after this hand. */
    val scores: List<Int>,
)

/**
 * The game as one player sees it: everything public, plus their own hand.
 * Other players' tiles are never sent.
 */
@Serializable
data class GameView(
    val id: String,
    val code: String,
    val version: Int,
    val status: GameStatus,
    val pace: Pace,
    val rules: Rules,
    val hostSeat: Int,
    /** Your seat, or -1 if you're not at this table. */
    val yourSeat: Int,
    val seats: List<SeatView>,
    val handNumber: Int,
    val board: Board,
    val boneyard: Int,
    val turn: Int,
    val hand: List<Tile>,
    /** The tile that must open the hand, if it's yours to play. */
    val opening: Tile?,
    /** Numbers each seat has shown it doesn't hold. */
    val lacks: List<Set<Int>>,
    /** What happened so far in this hand. */
    val events: List<PublicEvent>,
    val lastHand: HandSummary?,
    val winner: Int?,
    val updatedAt: Long,
    val visibility: Visibility = Visibility.PRIVATE,
) {
    val isYourTurn: Boolean get() = status == GameStatus.PLAYING && turn == yourSeat
}

/** A public game waiting for players, as anyone may see it in the open games list. */
@Serializable
data class OpenGame(
    val id: String,
    /** Join with this code, like any invite. */
    val code: String,
    val mode: GameMode,
    val target: Int,
    val pace: Pace,
    val host: String,
    val seats: Int,
    /** Seats already taken by people. */
    val taken: Int,
    val updatedAt: Long,
)

/** A line in "your games". */
@Serializable
data class GameSummary(
    val id: String,
    val code: String,
    val status: GameStatus,
    val pace: Pace,
    val mode: GameMode,
    val seats: List<String>,
    val scores: List<Int>,
    val yourSeat: Int,
    val turn: Int,
    val winner: Int?,
    val version: Int,
    val updatedAt: Long,
) {
    val isYourTurn: Boolean get() = status == GameStatus.PLAYING && turn == yourSeat
}

object Limits {
    const val NAME_MAX = 20
    const val CODE_LENGTH = 6
}
