package de.wtzg.domino.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

// The serializable engine types (Board, Rules, Move, Tile, Link, HandResult, Match, Round,
// Event…) are stored by the server, sent over the network and kept in the app's saved games.
// Renaming or removing a property, or adding one without a default, breaks the protocol and old saves.

/**
 * How a hand ended. [winner] (null on a tied block) collects [endPoints], already
 * rounded for Fives/Threes.
 */
@Serializable
data class HandResult(
    val winner: Int?,
    val endPoints: Int,
    val blocked: Boolean,
    val pipsLeft: List<Int>,
)

/**
 * Something that happened in a hand, for messages and the coach. The serial names are
 * pinned to what's already stored, so renaming or moving a class can't break saved games.
 */
@Serializable
sealed interface Event {
    val player: Int

    @Serializable
    @SerialName("de.wtzg.domino.engine.Event.Played")
    data class Played(override val player: Int, val move: Move, val points: Int, val endCount: Int) : Event

    @Serializable
    @SerialName("de.wtzg.domino.engine.Event.Drew")
    data class Drew(override val player: Int, val tile: Tile, val ends: List<Int>) : Event

    @Serializable
    @SerialName("de.wtzg.domino.engine.Event.Passed")
    data class Passed(override val player: Int, val ends: List<Int>) : Event
}

/**
 * One hand. The highest double opens (or the heaviest tile if nobody has a double).
 * Players take turns clockwise: 0 (you), 1, 2, 3.
 */
@Serializable
data class Round(
    val rules: Rules,
    val hands: List<List<Tile>>,
    val boneyard: List<Tile>,
    val turn: Int,
    /** The tile that must open the hand. */
    val opening: Tile?,
    val board: Board = Board(spinnerAllowed = rules.mode.spinner),
    /** Passes in a row; when everybody has passed the game is blocked. */
    val passes: Int = 0,
    /** Numbers each player has shown they don't hold (by drawing or passing). */
    val lacks: List<Set<Int>> = List(hands.size) { emptySet() },
    /** Points scored during this hand (Fives/Threes). */
    val points: List<Int> = List(hands.size) { 0 },
    val events: List<Event> = emptyList(),
    val result: HandResult? = null,
) {
    val players: Int get() = hands.size
    val isOver: Boolean get() = result != null
    fun next(player: Int): Int = (player + 1) % players

    fun legalMoves(player: Int = turn): List<Move> {
        if (isOver || player != turn) return emptyList()
        val hand = hands[player]
        if (board.isEmpty) return (opening?.let { listOf(it) } ?: hand).map { Move(it, Arm.EAST) }
        // With only the opening tile down, east and west are mirror images: offer one.
        val untouched = board.east.isEmpty() && board.west.isEmpty() && board.startEast == board.startWest
        val arms = if (untouched) listOf(Arm.EAST) else board.openArms
        return hand.flatMap { t -> arms.filter { board.canPlay(t, it) }.map { Move(t, it) } }
    }

    val canDraw: Boolean
        get() = !isOver && rules.mode.drawsFromBoneyard && boneyard.isNotEmpty() && legalMoves().isEmpty()

    val mustPass: Boolean
        get() = !isOver && legalMoves().isEmpty() && !canDraw

    /** Points a move would score right now (Fives/Threes). */
    fun pointsFor(move: Move): Int = rules.playPoints(board.play(move).endCount())

    fun play(move: Move): Round {
        require(move in legalMoves()) { "Illegal move $move for player $turn" }
        val p = turn
        val newBoard = board.play(move)
        val count = newBoard.endCount()
        val scored = rules.playPoints(count)
        val newHands = hands.toMutableList().also { it[p] = it[p] - move.tile }
        val newPoints = points.toMutableList().also { it[p] += scored }
        val played = copy(
            hands = newHands,
            board = newBoard,
            opening = null,
            passes = 0,
            points = newPoints,
            events = events + Event.Played(p, move, scored, count),
        )
        return if (newHands[p].isEmpty()) played.finish(winner = p, blocked = false) else played.copy(turn = next(p))
    }

    /** Draws one tile for the player to move. */
    fun draw(): Round {
        check(canDraw) { "Can't draw now" }
        val p = turn
        val tile = boneyard.first()
        val ends = board.openValues.distinct()
        // Drawing proves the player had none of the open numbers; older knowledge is
        // void because the new tile is hidden.
        return copy(
            hands = hands.toMutableList().also { it[p] = it[p] + tile },
            boneyard = boneyard.drop(1),
            lacks = lacks.toMutableList().also { it[p] = ends.toSet() },
            events = events + Event.Drew(p, tile, ends),
        )
    }

    fun pass(): Round {
        check(mustPass) { "Can't pass now" }
        val p = turn
        val ends = board.openValues.distinct()
        val passed = copy(
            passes = passes + 1,
            lacks = lacks.toMutableList().also { it[p] = it[p] + ends },
            events = events + Event.Passed(p, ends),
        )
        return if (passed.passes >= players) passed.finishBlocked() else passed.copy(turn = next(p))
    }

    private fun finishBlocked(): Round {
        val pips = hands.map { h -> h.sumOf { it.pips } }
        val low = pips.min()
        val winner = pips.indexOf(low).takeIf { pips.count { it == low } == 1 }
        return finish(winner, blocked = true)
    }

    private fun finish(winner: Int?, blocked: Boolean): Round {
        val pips = hands.map { h -> h.sumOf { it.pips } }
        val raw = winner?.let { w -> pips.filterIndexed { i, _ -> i != w }.sum() } ?: 0
        return copy(result = HandResult(winner, rules.roundEndPoints(raw), blocked, pips))
    }

    companion object {
        fun deal(rules: Rules, random: Random = Random): Round {
            val tiles = Tile.SET.shuffled(random)
            val n = rules.players
            val size = rules.handSize
            val hands = List(n) { i -> tiles.subList(i * size, (i + 1) * size) }
            val boneyard = tiles.drop(n * size)
            val inPlay = hands.flatten()
            val open = inPlay.filter { it.isDouble }.maxByOrNull { it.a }
                ?: inPlay.maxWith(compareBy<Tile> { it.pips }.thenBy { maxOf(it.a, it.b) })
            val starter = hands.indexOfFirst { open in it }
            return Round(rules, hands, boneyard, turn = starter, opening = open)
        }
    }
}
