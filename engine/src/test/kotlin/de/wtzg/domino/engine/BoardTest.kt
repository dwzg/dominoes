package de.wtzg.domino.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    private fun fives() = Board(spinnerAllowed = true)

    @Test
    fun setHas28TilesAndEachNumberSevenTimes() {
        assertEquals(28, Tile.SET.toSet().size)
        for (n in 0..6) assertEquals(7, Tile.SET.count { it.has(n) })
    }

    @Test
    fun openingTileCountsItsPips() {
        assertEquals(10, fives().play(Move(t(5, 5), Arm.EAST)).endCount())
        assertEquals(10, fives().play(Move(t(6, 4), Arm.EAST)).endCount())
    }

    @Test
    fun untouchedSideOfSpinnerCountsWholeDouble() {
        val b = fives().play(Move(t(5, 5), Arm.EAST)).play(Move(t(5, 2), Arm.EAST))
        assertEquals(12, b.endCount()) // 10 for the 5|5 + 2
        assertFalse(b.northSouthOpen)
        assertEquals(listOf(Arm.EAST, Arm.WEST), b.openArms)
    }

    @Test
    fun spinnerOpensNorthAndSouthOnceBothSidesArePlayed() {
        var b = fives().play(Move(t(5, 5), Arm.EAST))
        b = b.play(Move(t(5, 2), Arm.EAST)).play(Move(t(5, 3), Arm.WEST))
        assertTrue(b.northSouthOpen)
        assertEquals(5, b.endCount()) // 2 + 3, north/south don't count yet
        b = b.play(Move(t(5, 0), Arm.NORTH))
        assertEquals(5, b.endCount()) // 2 + 3 + 0
        b = b.play(Move(t(6, 5), Arm.SOUTH))
        assertEquals(11, b.endCount())
    }

    @Test
    fun doubleAtAnEndCountsBothHalves() {
        val b = fives().play(Move(t(6, 4), Arm.EAST)).play(Move(t(4, 4), Arm.EAST))
        assertEquals(6 + 8, b.endCount())
    }

    @Test
    fun doubleAtTheEndOfNorthCountsBothHalves() {
        var b = Board(spinnerAllowed = true).play(Move(t(6, 6), Arm.EAST))
        b = b.play(Move(t(6, 2), Arm.EAST)).play(Move(t(6, 3), Arm.WEST))
        b = b.play(Move(t(6, 4), Arm.NORTH)).play(Move(t(4, 4), Arm.NORTH))
        assertEquals(2 + 3 + 8, b.endCount())
        b = b.play(Move(t(6, 1), Arm.SOUTH))
        assertEquals(2 + 3 + 8 + 1, b.endCount())
    }

    @Test
    fun noSpinnerInDraw() {
        var b = Board().play(Move(t(6, 6), Arm.EAST))
        b = b.play(Move(t(6, 1), Arm.EAST)).play(Move(t(6, 2), Arm.WEST))
        assertFalse(b.northSouthOpen)
        assertEquals(listOf(2, 1), listOf(b.endValue(Arm.WEST), b.endValue(Arm.EAST)))
    }

    @Test
    fun tilesOrientTowardsTheirNeighbour() {
        var b = Board().play(Move(t(6, 6), Arm.EAST))
        b = b.play(Move(t(6, 3), Arm.EAST)).play(Move(t(1, 6), Arm.WEST))
        assertEquals(Link(t(1, 6), 6, 1), b.west.single())
        assertEquals(Link(t(6, 3), 6, 3), b.east.single())
    }
}
