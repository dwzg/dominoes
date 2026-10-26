package de.wtzg.domino.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoundTest {
    private fun round(
        mode: GameMode,
        hands: List<List<Tile>>,
        board: Board,
        boneyard: List<Tile> = emptyList(),
        turn: Int = 0,
    ) = Round(Rules(mode, hands.size), hands, boneyard, turn, opening = null, board = board)

    @Test
    fun dealFollowsRulesForEveryModeAndPlayerCount() {
        for (mode in GameMode.entries) for (players in 2..4) repeat(20) { seed ->
            val rules = Rules(mode, players)
            val r = Round.deal(rules, Random(seed))
            assertEquals(players, r.hands.size)
            r.hands.forEach { assertEquals(rules.handSize, it.size) }
            assertEquals(28 - players * rules.handSize, r.boneyard.size)
            val open = r.opening!!
            assertTrue(open in r.hands[r.turn])
            assertEquals(listOf(Move(open, Arm.EAST)), r.legalMoves())
        }
    }

    @Test
    fun drawingRevealsMissingNumbers() {
        val r = round(
            GameMode.DRAW,
            listOf(listOf(t(1, 0)), listOf(t(2, 2), t(5, 4))),
            Board().play(Move(t(4, 3), Arm.EAST)),
            boneyard = listOf(t(6, 6)),
        )
        assertTrue(r.canDraw)
        val next = r.draw()
        assertEquals(setOf(4, 3), next.lacks[0])
        assertEquals(t(6, 6), next.hands[0].last())
        assertTrue(Strategy.knownLacks(next, 1, 0).containsAll(setOf(3, 4)))
    }

    @Test
    fun blockModeNeverDraws() {
        val r = round(
            GameMode.BLOCK,
            listOf(listOf(t(1, 0)), listOf(t(2, 2))),
            Board().play(Move(t(4, 3), Arm.EAST)),
            boneyard = listOf(t(6, 6)),
        )
        assertFalse(r.canDraw)
        assertTrue(r.mustPass)
    }

    @Test
    fun blockedGameNeedsEveryoneToPassAndGoesToTheLightestHand() {
        var r = round(
            GameMode.DRAW,
            listOf(listOf(t(1, 0)), listOf(t(6, 5)), listOf(t(5, 4))),
            Board().play(Move(t(3, 2), Arm.EAST)),
        )
        r = r.pass().pass()
        assertNull(r.result)
        r = r.pass()
        val res = r.result!!
        assertTrue(res.blocked)
        assertEquals(0, res.winner)
        assertEquals(11 + 9, res.endPoints)
    }

    @Test
    fun goingOutScoresEveryOpponentsPips() {
        val r = round(
            GameMode.DRAW,
            listOf(listOf(t(3, 1)), listOf(t(6, 5)), listOf(t(2, 0))),
            Board().play(Move(t(3, 3), Arm.EAST)),
        ).play(Move(t(3, 1), Arm.EAST))
        assertEquals(HandResult(0, 13, false, listOf(0, 11, 2)), r.result)
    }

    @Test
    fun fivesScoresDuringPlayAndRoundsTheEnd() {
        var r = round(
            GameMode.ALL_FIVES,
            listOf(listOf(t(5, 0), t(2, 0)), listOf(t(2, 1), t(4, 4))),
            Board(spinnerAllowed = true).play(Move(t(5, 5), Arm.EAST)),
        )
        r = r.play(Move(t(5, 0), Arm.EAST)) // 10 (untouched 5|5) + 0 = 10
        assertEquals(listOf(10, 0), r.points)
        assertTrue(r.events.last() is Event.Played)
        assertEquals(10, (r.events.last() as Event.Played).points)
        r = r.pass().play(Move(t(2, 0), Arm.EAST)) // 10 + 2 = 12, no points; player 0 is out
        assertEquals(listOf(10, 0), r.points)
        assertEquals(HandResult(0, 10, false, listOf(0, 11)), r.result) // 11 left, rounded to 10
    }

    @Test
    fun blockedTieHasNoWinner() {
        val r = round(
            GameMode.DRAW,
            listOf(listOf(t(2, 1)), listOf(t(3, 0))),
            Board().play(Move(t(6, 5), Arm.EAST)),
        ).pass().pass()
        assertEquals(HandResult(null, 0, true, listOf(3, 3)), r.result)
    }

    @Test
    fun blockedHandPointsAreRoundedInFives() {
        val r = round(
            GameMode.ALL_FIVES,
            listOf(listOf(t(2, 1)), listOf(t(4, 4), t(3, 1))),
            Board(spinnerAllowed = true).play(Move(t(5, 5), Arm.EAST)),
        ).pass().pass()
        assertEquals(HandResult(0, 10, true, listOf(3, 12)), r.result) // 12 rounds to 10
    }

    @Test
    fun eventsKeepTheirStoredTypeNames() {
        // Saved games and server records hold these names; they must never change.
        val events = listOf(
            Event.Played(0, Move(t(5, 5), Arm.EAST), 10, 10),
            Event.Drew(1, t(6, 6), listOf(5)),
            Event.Passed(1, listOf(5)),
        )
        val json = Json.encodeToString(ListSerializer(Event.serializer()), events)
        for (name in listOf("Played", "Drew", "Passed")) {
            assertTrue(json, json.contains("\"type\":\"de.wtzg.domino.engine.Event.$name\""))
        }
    }

    @Test
    fun handsAlwaysFinishWithAllTilesAccountedFor() {
        val rnd = Random(7)
        for (mode in GameMode.entries) for (players in 2..4) repeat(60) {
            val r = playOut(Round.deal(Rules(mode, players), rnd), rnd, { Difficulty.entries[it % 3] })
            assertEquals(28, r.hands.flatten().size + r.boneyard.size + r.board.size)
            assertTrue(r.result!!.endPoints >= 0)
            if (mode.scoresDuringPlay) r.points.forEach { assertEquals(0, it % mode.scoringMultiple) }
        }
    }
}
