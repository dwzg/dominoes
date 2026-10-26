package de.wtzg.domino.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchTest {
    @Test
    fun matchesEndAtTheTarget() {
        val rnd = Random(3)
        for (mode in GameMode.entries) for (players in 2..4) repeat(5) {
            var m = Match.start(Rules(mode, players), rnd)
            var guard = 0
            while (!m.isOver) {
                if (m.round.isOver) m = m.nextHand(rnd)
                val r = m.round
                m = when {
                    r.canDraw -> m.draw()
                    r.mustPass -> m.pass()
                    else -> m.play(Strategy.chooseMove(r, Difficulty.NORMAL, rnd)!!)
                }
                check(++guard < 20_000)
            }
            val s = m.scores
            assertTrue(s[m.winner!!] >= m.rules.target)
            assertEquals(s.max(), s[m.winner!!])
            // Without play scoring, the match only ends when a hand is over.
            if (!mode.scoresDuringPlay) assertTrue(m.round.isOver)
        }
    }

    @Test
    fun fivesCanEndMidHand() {
        val rules = Rules(GameMode.ALL_FIVES, 2, target = 10)
        val round = Round(
            rules,
            listOf(listOf(t(5, 0), t(1, 1)), listOf(t(2, 2))),
            emptyList(),
            turn = 0,
            opening = null,
            board = Board(spinnerAllowed = true).play(Move(t(5, 5), Arm.EAST)),
        )
        val m = Match(rules, listOf(0, 0), round).play(Move(t(5, 0), Arm.EAST))
        assertEquals(0, m.winner)
        assertTrue(!m.round.isOver)
    }

    @Test
    fun finishedMatchCantBePlayedOn() {
        val rules = Rules(GameMode.ALL_FIVES, 2, target = 10)
        val round = Round(
            rules,
            listOf(listOf(t(5, 0), t(1, 1)), listOf(t(2, 2))),
            emptyList(),
            turn = 0,
            opening = null,
            board = Board(spinnerAllowed = true).play(Move(t(5, 5), Arm.EAST)),
        )
        val m = Match(rules, listOf(0, 0), round).play(Move(t(5, 0), Arm.EAST))
        assertTrue(m.isOver)
        assertTrue(m.round.mustPass) // the hand itself could go on
        assertThrows(IllegalStateException::class.java) { m.pass() }
    }

    @Test
    fun tieAtTheTargetPlaysAnotherHand() {
        // Player 0 goes out for 5 and draws level with player 1 at the target.
        val rules = Rules(GameMode.DRAW, 2, target = 10)
        val round = Round(
            rules,
            listOf(listOf(t(3, 1)), listOf(t(2, 2), t(1, 0))),
            emptyList(),
            turn = 0,
            opening = null,
            board = Board().play(Move(t(3, 3), Arm.EAST)),
        )
        val m = Match(rules, listOf(5, 10), round).play(Move(t(3, 1), Arm.EAST))
        assertTrue(m.round.isOver)
        assertEquals(listOf(10, 10), m.scores)
        assertNull(m.winner)
        val next = m.nextHand(Random(1))
        assertEquals(2, next.handNumber)
        assertEquals(listOf(10, 10), next.banked)
        assertFalse(next.round.isOver)
    }

    @Test
    fun matchSurvivesSaving() {
        val rnd = Random(9)
        var m = Match.start(Rules(GameMode.ALL_THREES, 3), rnd)
        repeat(5) { if (!m.round.isOver) m = m.play(Strategy.chooseMove(m.round, Difficulty.HARD, rnd) ?: return@repeat) }
        val json = Json.encodeToString(Match.serializer(), m)
        assertEquals(m, Json.decodeFromString(Match.serializer(), json))
    }
}
