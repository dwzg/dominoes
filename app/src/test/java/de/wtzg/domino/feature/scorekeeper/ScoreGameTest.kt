package de.wtzg.domino.feature.scorekeeper

import de.wtzg.domino.data.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreGameTest {
    private fun game(mode: ScoreMode = ScoreMode.FIRST_TO_TARGET, limit: Int = 100, players: Int = 2) =
        ScoreGame(id = 1, players = List(players) { "P$it" }, mode = mode, limit = limit, startedAt = 0)

    @Test
    fun totalsSumRounds() {
        val g = game().addRound(listOf(10, 0)).addRound(listOf(5, 20))
        assertEquals(listOf(15, 20), g.totals)
        assertEquals(1, g.leaderIndex)
    }

    @Test
    fun highestWinsOnceTargetReached() {
        var g = game(limit = 50).addRound(listOf(45, 0))
        assertFalse(g.isFinished)
        g = g.addRound(listOf(10, 0))
        assertEquals(0, g.winnerIndex)
    }

    @Test
    fun tieAtTargetKeepsGameGoing() {
        val g = game(limit = 50).addRound(listOf(50, 50))
        assertNull(g.winnerIndex)
        assertEquals(1, g.addRound(listOf(0, 5)).winnerIndex)
    }

    @Test
    fun lowestWinsAfterRounds() {
        var g = game(mode = ScoreMode.LOWEST_AFTER_ROUNDS, limit = 2, players = 3)
        g = g.addRound(listOf(10, 3, 7))
        assertFalse(g.isFinished)
        g = g.addRound(listOf(0, 9, 1))
        assertEquals(2, g.winnerIndex)
    }

    @Test
    fun editAndDeleteRounds() {
        var g = game().addRound(listOf(10, 0)).addRound(listOf(0, 10))
        g = g.updateRound(0, listOf(30, 0))
        assertEquals(listOf(30, 10), g.totals)
        g = g.deleteRound(1)
        assertEquals(listOf(30, 0), g.totals)
    }

    @Test
    fun missingScoresCountAsZero() {
        val g = game(players = 3).addRound(listOf(5))
        assertEquals(listOf(5, 0, 0), g.rounds.single())
    }

    @Test
    fun loadsGamesSavedByVersion1() {
        // Exactly what version 1 wrote with org.json.
        val old = """{"id":1761249600000,"players":["Ann","Bob"],"mode":"FIRST_TO_TARGET","limit":100,""" +
            """"rounds":[[10,0],[0,25]],"startedAt":1761249600000,"finishedAt":1761250000000}"""
        val g = AppJson.decodeFromString(ScoreGame.serializer(), old)
        assertEquals(listOf("Ann", "Bob"), g.players)
        assertEquals(listOf(10, 25), g.totals)
        assertEquals(1761250000000, g.finishedAt)
        val running = """{"id":2,"players":["A","B","C"],"mode":"LOWEST_AFTER_ROUNDS","limit":13,"rounds":[],"startedAt":2}"""
        assertNull(AppJson.decodeFromString(ScoreGame.serializer(), running).finishedAt)
    }

    @Test
    fun jsonRoundTrip() {
        val g = game(mode = ScoreMode.LOWEST_AFTER_ROUNDS, limit = 13, players = 4).addRound(listOf(1, 2, 3, 4)).copy(finishedAt = 42)
        assertEquals(g, AppJson.decodeFromString(ScoreGame.serializer(), AppJson.encodeToString(ScoreGame.serializer(), g)))
    }

    @Test
    fun roundWinnerStartsTheNextRound() {
        var g = game(players = 3).copy(firstStarter = 2)
        assertEquals(2, g.currentStarter)
        g = g.addRound(listOf(0, 15, 5))
        assertEquals(1, g.currentStarter)
        // A tied round keeps the same starter.
        g = g.addRound(listOf(10, 0, 10))
        assertEquals(1, g.currentStarter)
        assertEquals(listOf(2, 1, 1), (0..2).map(g::starterOf))
    }

    @Test
    fun lowestScoreWinsTheRoundWhenLowWins() {
        val g = game(mode = ScoreMode.LOWEST_AFTER_ROUNDS, limit = 5, players = 3).addRound(listOf(12, 3, 7))
        assertEquals(1, g.currentStarter)
    }

    @Test
    fun rotatingStartMovesOneSeatPerRound() {
        var g = game(players = 3).copy(firstStarter = 1, rotateStarter = true)
        assertEquals(1, g.currentStarter)
        g = g.addRound(listOf(20, 0, 0))
        assertEquals(2, g.currentStarter)
        g = g.addRound(listOf(20, 0, 0))
        assertEquals(0, g.currentStarter)
    }

    @Test
    fun oldGamesStartWithTheFirstPlayer() {
        val old = """{"id":2,"players":["A","B"],"mode":"FIRST_TO_TARGET","limit":100,"rounds":[],"startedAt":2}"""
        val g = AppJson.decodeFromString(ScoreGame.serializer(), old)
        assertEquals(0, g.currentStarter)
        assertFalse(g.rotateStarter)
    }
}
