package de.wtzg.domino.server

import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.ServerFeatures
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.TurnLimits
import de.wtzg.domino.protocol.Visibility
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class TurnLimitsAndRematchTest {
    private var now = 1_000_000_000L
    private val store = InMemoryStore()
    private val service = GameService(store, clock = { now }, random = Random(11))

    private fun player(name: String) = service.authenticate(service.register(name).token)!!

    /** Ann and Bob in a started two-player game with [limit] seconds per turn. */
    private fun startedGame(limit: Int): Triple<PlayerRecord, PlayerRecord, GameRecord> = runBlocking {
        val ann = player("Ann")
        val bob = player("Bob")
        val g = service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE, turnLimitSeconds = limit), 1)
        service.join(bob, g.code, 1)
        Triple(ann, bob, service.start(ann, g.id, de.wtzg.domino.engine.Difficulty.EASY))
    }

    private fun expectApiError(status: Int, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected $status")
        } catch (e: ApiException) {
            assertEquals(e.message, status, e.status)
        }
    }

    @Test
    fun runningOutOfTimeEndsTheGameWithNoWinner() = runBlocking {
        val (ann, _, g) = startedGame(limit = 60)
        val toMove = g.match!!.round.turn
        assertEquals(60_000L, service.view(g, ann.id, emptySet(), 1).turnEndsInMs)

        now += 59_000
        assertTrue("still in time", service.expireOverdueTurns().isEmpty())
        assertEquals(1_000L, service.view(store.game(g.id)!!, ann.id, emptySet(), 1).turnEndsInMs)

        now += 2_000
        assertEquals(listOf(g.id), service.expireOverdueTurns())
        val ended = store.game(g.id)!!
        assertEquals(GameStatus.FINISHED, ended.status)
        assertEquals(toMove, ended.timedOut)
        val v = service.view(ended, ann.id, emptySet(), 1)
        assertEquals(toMove, v.timedOut)
        assertNull("nobody wins", v.winner)
        assertNull("no clock on a finished game", v.turnEndsInMs)
        assertEquals(toMove, service.gamesOf(ann, 1).single().timedOut)
        assertTrue("only once", service.expireOverdueTurns().isEmpty())
    }

    @Test
    fun eachMoveStartsTheClockAgain() = runBlocking {
        val (ann, bob, g) = startedGame(limit = 60)
        val mover = if (g.match!!.round.turn == 0) ann else bob
        now += 50_000
        val r = g.match.round
        val action = when {
            r.legalMoves().isNotEmpty() -> de.wtzg.domino.protocol.Action.Play(r.legalMoves().first())
            r.canDraw -> de.wtzg.domino.protocol.Action.Draw
            else -> de.wtzg.domino.protocol.Action.Pass
        }
        service.act(mover, g.id, action, g.version)
        now += 50_000
        assertTrue("the next turn has its own minute", service.expireOverdueTurns().isEmpty())
    }

    @Test
    fun noLimitMeansNoClock() = runBlocking {
        val (ann, _, g) = startedGame(limit = TurnLimits.NONE)
        assertNull(service.view(g, ann.id, emptySet(), 1).turnEndsInMs)
        now += 365L * 24 * 60 * 60 * 1000
        assertTrue(service.expireOverdueTurns().isEmpty())
    }

    @Test
    fun turnLimitsAndTargetsAreChecked() {
        val ann = player("Ann")
        expectApiError(400) { service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE, turnLimitSeconds = 5), 1) }
        expectApiError(400) { service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE, turnLimitSeconds = 31 * TurnLimits.DAY), 1) }
        expectApiError(400) { service.create(ann, CreateGameRequest(GameMode.DRAW, 2, Limits.TARGET_MAX + 1, Pace.LIVE), 1) }
        // Any target in range works, not just the presets.
        val g = service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 137, Pace.TURN_BASED, turnLimitSeconds = 2 * TurnLimits.DAY), 1)
        assertEquals(137, g.rules.target)
        runBlocking {
            val changed = service.updateSettings(ann, g.id, SettingsRequest(GameMode.ALL_FIVES, 2, 245, Pace.LIVE, turnLimitSeconds = 30), 1)
            assertEquals(245, changed.rules.target)
            assertEquals(30, changed.turnLimitSeconds)
        }
        expectApiError(400) { service.updateSettings(ann, g.id, SettingsRequest(GameMode.ALL_FIVES, 2, 100, Pace.LIVE, turnLimitSeconds = 1), 1) }
    }

    @Test
    fun onePlayerCanListOnlyAFewOpenGames() {
        val ann = player("Ann")
        fun open() = service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE, Visibility.PUBLIC), 1)
        val first = open()
        repeat(Limits.OPEN_GAMES_PER_HOST - 1) { open() }
        expectApiError(409) { open() }
        // Private games are never limited; listing one is.
        val private = service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE), 1)
        expectApiError(409) { service.setVisibility(ann, private.id, Visibility.PUBLIC) }
        // Once one fills up, there's room again.
        runBlocking { service.join(player("Bob"), first.code, 1) }
        open()
    }

    @Test
    fun everyoneLandsInTheSameRematch() = runBlocking {
        val (ann, bob, g) = startedGame(limit = 60)
        expectApiError(409) { service.rematch(ann, g.id, 1) }
        now += 61_000
        service.expireOverdueTurns()

        val lobby = service.rematch(bob, g.id, 1)
        assertEquals(GameStatus.LOBBY, lobby.status)
        assertEquals(bob.id, lobby.seats[lobby.hostSeat].playerId)
        assertEquals(g.rules, lobby.rules)
        assertEquals(60, lobby.turnLimitSeconds)
        assertEquals(lobby.id, service.view(store.game(g.id)!!, ann.id, emptySet(), 1).rematchId)

        val joined = service.rematch(ann, g.id, 1)
        assertEquals(lobby.id, joined.id)
        assertEquals(listOf(bob.id, ann.id), joined.playerIds)
        assertEquals("asking again changes nothing", lobby.id, service.rematch(ann, g.id, 1).id)

        expectApiError(403) { service.rematch(player("Cat"), g.id, 1) }
    }

    @Test
    fun aCancelledRematchCanBeSetUpAgain() = runBlocking {
        val (ann, bob, g) = startedGame(limit = 60)
        now += 61_000
        service.expireOverdueTurns()
        val first = service.rematch(ann, g.id, 1)
        service.act(ann, first.id, de.wtzg.domino.protocol.Action.Leave, first.version) // host cancels
        val second = service.rematch(bob, g.id, 1)
        assertTrue(second.id != first.id)
        assertEquals(bob.id, second.seats[0].playerId)
    }

    @Test
    fun sqliteFindsOverdueGamesAfterARestart() {
        val file = kotlin.io.path.createTempFile("domino", ".db").toFile()
        try {
            val sql = GameService(SqliteStore(file.path), clock = { now }, random = Random(12))
            val ann = sql.authenticate(sql.register("Ann").token)!!
            val bob = sql.authenticate(sql.register("Bob").token)!!
            val g = sql.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.TURN_BASED, turnLimitSeconds = TurnLimits.DAY), 1)
            runBlocking { sql.join(bob, g.code, 1); sql.start(ann, g.id, de.wtzg.domino.engine.Difficulty.EASY) }
            assertTrue(SqliteStore(file.path).overdueGames(now).isEmpty())
            now += TurnLimits.DAY * 1000L + 1
            assertEquals(listOf(g.id), SqliteStore(file.path).overdueGames(now))
            val restarted = GameService(SqliteStore(file.path), clock = { now }, random = Random(13))
            assertEquals(listOf(g.id), runBlocking { restarted.expireOverdueTurns() })
            assertTrue("finished games leave the list", SqliteStore(file.path).overdueGames(now).isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun theServerEndsOverdueGamesByItself() = testApplication {
        application { dominoServer(service, LiveHub(), turnCheckMs = 20) }
        val c = createClient { install(ContentNegotiation) { json(ProtocolJson) } }
        assertTrue(ServerFeatures.TURN_LIMITS in c.get("/version").body<ServerInfo>().features)
        suspend fun reg(name: String): Registration =
            c.post("/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest(name)) }.body()
        val ann = reg("Ann")
        val bob = reg("Bob")
        val g = c.post("/games") {
            bearerAuth(ann.token); contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE, turnLimitSeconds = 30))
        }.body<GameView>()
        c.post("/games/join") { bearerAuth(bob.token); contentType(ContentType.Application.Json); setBody(de.wtzg.domino.protocol.JoinRequest(g.code)) }
        c.post("/games/${g.id}/start") { bearerAuth(ann.token); contentType(ContentType.Application.Json); setBody(StartRequest()) }

        now += 31_000
        val ended = withTimeout(5_000) {
            var v: GameView
            do {
                delay(20)
                v = c.get("/games/${g.id}") { bearerAuth(ann.token) }.body()
            } while (v.status != GameStatus.FINISHED)
            v
        }
        assertNotNull(ended.timedOut)

        val rematch = c.post("/games/${g.id}/rematch") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.OK, rematch.status)
        assertEquals(GameStatus.LOBBY, rematch.body<GameView>().status)
    }
}
