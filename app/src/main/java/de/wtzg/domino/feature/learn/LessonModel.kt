package de.wtzg.domino.feature.learn

import de.wtzg.domino.engine.Arm
import de.wtzg.domino.engine.Board
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Tile

enum class LessonCategory(val title: String) {
    BASICS("Basics"),
    STRATEGY("Strategy"),
    SCORING("Scoring games"),
    ADVANCED("Advanced"),
}

sealed interface LessonBlock {
    data class Text(val text: String) : LessonBlock

    /** Tiles held in a hand, drawn upright side by side. */
    data class Hand(val tiles: List<Tile>, val caption: String) : LessonBlock

    /**
     * A position on the table, built by playing [plays] in order (the first opens).
     * With [spinner] the opening double can be built on from four sides (Fives/Threes).
     */
    data class Table(val plays: List<Move>, val caption: String, val spinner: Boolean = false) : LessonBlock {
        /** Built once, when first drawn; it isn't part of equality. */
        val board: Board by lazy { plays.fold(Board(spinnerAllowed = spinner)) { b, m -> b.play(m) } }
    }

    data class Tip(val text: String) : LessonBlock
}

data class Lesson(
    /** Stable id, used to remember which lessons were read. */
    val id: String,
    val category: LessonCategory,
    val title: String,
    val summary: String,
    val blocks: List<LessonBlock>,
    /** Game mode to suggest practising after the lesson, if any. */
    val practice: GameMode? = null,
)

/** Shorthands for writing lesson content. */
internal fun t(a: Int, b: Int) = Tile(a, b)
internal fun east(a: Int, b: Int) = Move(Tile(a, b), Arm.EAST)
internal fun west(a: Int, b: Int) = Move(Tile(a, b), Arm.WEST)
internal fun north(a: Int, b: Int) = Move(Tile(a, b), Arm.NORTH)
internal fun south(a: Int, b: Int) = Move(Tile(a, b), Arm.SOUTH)
