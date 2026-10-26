package de.wtzg.domino.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StrategyTest {
    @Test
    fun coachPrefersBlockingWhenOpponentIsStuck() {
        // Ends are 5 and 1; the opponent has shown it has no 5s or 2s.
        // Playing 2|1 on the 1 makes the ends 5 and 2, so the opponent is stuck.
        val board = Board().play(Move(t(5, 1), Arm.EAST))
        val r = Round(
            Rules(GameMode.DRAW),
            hands = listOf(listOf(t(2, 1), t(6, 1), t(0, 0)), List(4) { t(4, 4) }),
            boneyard = List(5) { t(0, 0) },
            turn = 0,
            opening = null,
            board = board,
            lacks = listOf(emptySet(), setOf(5, 2)),
        )
        val best = Strategy.evaluateAll(r).first()
        assertEquals(t(2, 1), best.move.tile)
        assertTrue(best.pros.first().contains("they have to draw or pass"))

        // In Block nobody draws.
        val block = Strategy.evaluateAll(r.copy(rules = Rules(GameMode.BLOCK), boneyard = emptyList())).first()
        assertTrue(block.pros.first().endsWith("so they have to pass."))
    }

    @Test
    fun coachExplainsTheOpeningTile() {
        fun opener(tile: Tile) = Strategy.evaluateAll(
            Round(Rules(GameMode.DRAW), listOf(listOf(tile, t(1, 0)), listOf(t(2, 1))), emptyList(), 0, opening = tile),
        ).single().factors.single().text!!
        assertTrue(opener(t(6, 6)).startsWith("The highest double"))
        assertTrue(opener(t(6, 5)).startsWith("Nobody has a double, so the heaviest tile"))
    }

    @Test
    fun coachTakesPointsInFives() {
        // Spinner 5|5 alone counts 10. 5|0 east → 10 + 0 = 10 points; 5|3 east → 13, no points.
        val r = Round(
            Rules(GameMode.ALL_FIVES),
            hands = listOf(listOf(t(5, 0), t(5, 3), t(2, 1)), listOf(t(4, 4), t(6, 2))),
            boneyard = List(5) { t(0, 0) },
            turn = 0,
            opening = null,
            board = Board(spinnerAllowed = true).play(Move(t(5, 5), Arm.EAST)),
        )
        val best = Strategy.evaluateAll(r).first()
        assertEquals(t(5, 0), best.move.tile)
        assertTrue(best.pros.first().startsWith("It scores 10"))
    }

    @Test
    fun coachPlaysLastTile() {
        val r = Round(
            Rules(GameMode.DRAW),
            hands = listOf(listOf(t(0, 1)), listOf(t(4, 4))),
            boneyard = emptyList(),
            turn = 0,
            opening = null,
            board = Board().play(Move(t(1, 1), Arm.EAST)),
        )
        assertEquals(t(0, 1), Strategy.chooseMove(r, Difficulty.HARD)!!.tile)
    }

    private fun points(mode: GameMode, hands: Int, p0: Difficulty, p1: Difficulty, seed: Int): Pair<Int, Int> {
        val rnd = Random(seed)
        val pts = intArrayOf(0, 0)
        repeat(hands) {
            val r = playOut(Round.deal(Rules(mode), rnd), rnd, { if (it == 0) p0 else p1 })
            for (i in 0..1) pts[i] += r.points[i]
            r.result!!.winner?.let { pts[it] += r.result!!.endPoints }
        }
        return pts[0] to pts[1]
    }

    @Test
    fun hardBeatsEasyInEveryMode() {
        for (mode in GameMode.entries) {
            val (hard, easy) = points(mode, 300, Difficulty.HARD, Difficulty.EASY, mode.ordinal)
            println("$mode: Hard $hard vs Easy $easy")
            assertTrue("$mode: $hard vs $easy", hard > easy)
        }
    }

    @Test
    fun hardBeatsNormalInEveryMode() {
        for (mode in GameMode.entries) {
            val (hard, normal) = points(mode, 600, Difficulty.HARD, Difficulty.NORMAL, 10 + mode.ordinal)
            println("$mode: Hard $hard vs Normal $normal")
            assertTrue("$mode: $hard vs $normal", hard > normal)
        }
    }

    @Test
    fun describeNamesTheNumber() {
        val b = Board().play(Move(t(6, 3), Arm.EAST))
        assertEquals("3|1 on the 3 end", Strategy.describe(Move(t(3, 1), Arm.EAST), b))
    }
}
