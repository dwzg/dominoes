package de.wtzg.domino.feature.online

import de.wtzg.domino.engine.Board
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.SeatView
import de.wtzg.domino.protocol.ServerFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnClockTest {
    @Test
    fun timeLeftReadsNaturally() {
        assertEquals("0:42", timeLeft(41_200))
        assertEquals("2:00", timeLeft(120_000))
        assertEquals("0:00", timeLeft(0))
        assertEquals("25 min", timeLeft(25 * 60_000L))
        assertEquals("5 h", timeLeft(5 * 3_600_000L))
        assertEquals("3 days", timeLeft(3 * 86_400_000L))
    }

    private fun view(status: GameStatus, turnEndsInMs: Long? = null, timedOut: Int? = null) = GameView(
        id = "g", code = "ABC234", version = 3, status = status, pace = Pace.LIVE,
        rules = Rules(GameMode.DRAW, 2, 60), hostSeat = 0, yourSeat = 0,
        seats = listOf(SeatView("Ann", "a", false, 7, 10, true), SeatView("Bob", "b", false, 7, 5, true)),
        handNumber = 1, board = Board(spinnerAllowed = false), boneyard = 14, turn = 1, hand = emptyList(),
        opening = null, lacks = listOf(emptySet(), emptySet()), events = emptyList(), lastHand = null,
        winner = null, updatedAt = 0, turnLimitSeconds = 60, turnEndsInMs = turnEndsInMs, timedOut = timedOut,
    )

    @Test
    fun countsDownFromWhenTheViewArrived() {
        val s = OnlineGameState(view = view(GameStatus.PLAYING, turnEndsInMs = 60_000), viewAt = 1_000)
        assertEquals(60_000L, s.turnLeftMs(1_000))
        assertEquals(15_000L, s.turnLeftMs(46_000))
        assertEquals(0L, s.turnLeftMs(100_000))
        assertTrue(s.toTable(46_000)!!.status.endsWith("0:15 left."))
        assertNull(OnlineGameState(view = view(GameStatus.PLAYING)).turnLeftMs(5_000))
    }

    @Test
    fun runningOutOfTimeEndsWithARematch() {
        val bobOut = OnlineGameState(view = view(GameStatus.FINISHED, timedOut = 1), features = listOf(ServerFeatures.REMATCH))
        val overlay = bobOut.toTable()!!.overlay!!
        assertEquals("Bob ran out of time.", overlay.title)
        assertEquals("Rematch", overlay.primary)
        val older = OnlineGameState(view = view(GameStatus.FINISHED, timedOut = 0))
        assertEquals("You ran out of time.", older.toTable()!!.overlay!!.title)
        assertNull("no rematch on servers without it", older.toTable()!!.overlay!!.primary)
    }
}
