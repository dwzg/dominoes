package de.wtzg.domino.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {
    @Test
    fun roundEndPointsRoundToTheMultiple() {
        val fives = Rules(GameMode.ALL_FIVES)
        assertEquals(10, fives.roundEndPoints(12))
        assertEquals(15, fives.roundEndPoints(13))
        val threes = Rules(GameMode.ALL_THREES)
        assertEquals(3, threes.roundEndPoints(4))
        assertEquals(6, threes.roundEndPoints(5))
        assertEquals(17, Rules(GameMode.DRAW).roundEndPoints(17))
    }

    @Test
    fun playPointsOnlyForMultiples() {
        val fives = Rules(GameMode.ALL_FIVES)
        assertEquals(15, fives.playPoints(15))
        assertEquals(0, fives.playPoints(14))
        assertEquals(0, fives.playPoints(0))
        assertEquals(0, Rules(GameMode.DRAW).playPoints(15))
    }

    @Test
    fun defaultTargets() {
        assertEquals(listOf(60, 60, 100, 90), GameMode.entries.map { Rules(it).target })
    }

    @Test
    fun handSizes() {
        assertEquals(7, Rules(GameMode.DRAW, 2).handSize)
        assertEquals(5, Rules(GameMode.DRAW, 4).handSize)
        assertEquals(7, Rules(GameMode.BLOCK, 4).handSize)
    }
}
