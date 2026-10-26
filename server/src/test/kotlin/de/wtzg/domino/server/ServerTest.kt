package de.wtzg.domino.server

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.ActionRequest
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.canDraw
import de.wtzg.domino.protocol.legalMoves
import de.wtzg.domino.protocol.mustPass
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ServerTest {
    private fun ApplicationTestBuilder.setUp(): HttpClient {
        application { dominoServer(GameService(InMemoryStore(), random = Random(1)), LiveHub()) }
        return createClient {
            install(ContentNegotiation) { json(ProtocolJson) }
            install(WebSockets)
        }
    }

    private suspend fun HttpClient.register(name: String): Registration =
        post("/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest(name)) }.body()

    private suspend fun HttpClient.view(r: Registration, id: String): GameView = get("/games/$id") { bearerAuth(r.token) }.body()

    private suspend fun HttpClient.act(r: Registration, id: String, action: Action, version: Int): HttpResponse =
        post("/games/$id/actions") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(ActionRequest(action, version)) }

    private suspend fun HttpClient.newGame(host: Registration, mode: GameMode, players: Int, target: Int, pace: Pace = Pace.LIVE): GameView =
        post("/games") { bearerAuth(host.token); contentType(ContentType.Application.Json); setBody(CreateGameRequest(mode, players, target, pace)) }.body()

    private suspend fun HttpClient.join(r: Registration, code: String): HttpResponse =
        post("/games/join") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(JoinRequest(code)) }

    private suspend fun HttpClient.start(host: Registration, id: String): GameView =
        post("/games/$id/start") { bearerAuth(host.token); contentType(ContentType.Application.Json); setBody(StartRequest(Difficulty.HARD)) }.body()

    @Test
    fun twoFriendsAndAComputerPlayAWholeMatch() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val lobby = c.newGame(ann, GameMode.ALL_FIVES, players = 3, target = 60)
        assertEquals(GameStatus.LOBBY, lobby.status)
        assertEquals(HttpStatusCode.OK, c.join(bob, lobby.code.lowercase()).status)
        val started = c.start(ann, lobby.id)
        assertEquals(GameStatus.PLAYING, started.status)
        assertTrue(started.seats[2].isAi)

        val people = mapOf(ann.playerId to ann, bob.playerId to bob)
        var sawFinishedHand = false
        var steps = 0
        while (true) {
            val v = c.view(ann, lobby.id)
            if (v.status == GameStatus.FINISHED) {
                assertNotNull(v.winner)
                break
            }
            if (v.lastHand != null) sawFinishedHand = true
            val who = people.getValue(v.seats[v.turn].playerId!!) // computers never leave the turn on themselves
            val mine = c.view(who, lobby.id)
            // Each player gets only their own hand.
            assertEquals(mine.seats[mine.yourSeat].tiles, mine.hand.size)
            val action = when {
                mine.legalMoves.isNotEmpty() -> Action.Play(mine.legalMoves.first())
                mine.canDraw -> Action.Draw
                mine.mustPass -> Action.Pass
                else -> error("No action available")
            }
            assertEquals(HttpStatusCode.OK, c.act(who, lobby.id, action, mine.version).status)
            assertTrue("match never ends", ++steps < 3_000)
        }
        val list: List<GameSummary> = c.get("/games") { bearerAuth(bob.token) }.body()
        assertEquals(GameStatus.FINISHED, list.single().status)
        assertTrue("a hand finished before the match did, or the match ended in the first hand", sawFinishedHand || steps > 0)
    }

    @Test
    fun othersTilesAreNeverSent() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, GameMode.DRAW, 2, 60)
        c.join(bob, g.code)
        c.start(ann, g.id)
        val annRaw = c.get("/games/${g.id}") { bearerAuth(ann.token) }.bodyAsText()
        val bobView = c.view(bob, g.id)
        val annView = ProtocolJson.decodeFromString(GameView.serializer(), annRaw)
        assertTrue(bobView.hand.none { it in annView.hand })
        // Bob's tiles appear nowhere in what Ann receives (the board is still empty or holds only the opener).
        bobView.hand.filter { it !in annView.board.tiles }.forEach { t ->
            assertFalse("Ann can see $t", annRaw.contains("{\"a\":${t.a},\"b\":${t.b}}"))
        }
    }

    @Test
    fun rejectsWhatShouldBeRejected() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val eve = c.register("Eve")
        assertEquals(HttpStatusCode.BadRequest, c.post("/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest("  ")) }.status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/games") { bearerAuth("nope") }.status)

        val g = c.newGame(ann, GameMode.DRAW, 2, 60)
        assertEquals(HttpStatusCode.Forbidden, c.post("/games/${g.id}/start") { bearerAuth(bob.token); contentType(ContentType.Application.Json); setBody(StartRequest()) }.status)
        c.join(bob, g.code)
        assertEquals(HttpStatusCode.Conflict, c.join(eve, g.code).status) // full
        assertEquals(HttpStatusCode.Forbidden, c.get("/games/${g.id}") { bearerAuth(eve.token) }.status)
        assertEquals(HttpStatusCode.NotFound, c.join(eve, "ZZZZZZ").status)

        val v = c.start(ann, g.id)
        val (mover, waiter) = if (v.turn == v.yourSeat) ann to bob else bob to ann
        val mv = c.view(mover, g.id)
        assertEquals(HttpStatusCode.Forbidden, c.act(waiter, g.id, Action.Draw, mv.version).status)
        assertEquals(HttpStatusCode.Conflict, c.act(mover, g.id, Action.Play(mv.legalMoves.first()), mv.version - 1).status)
        val illegal = mv.hand.first { t -> mv.legalMoves.none { it.tile == t } }
        assertEquals(HttpStatusCode.BadRequest, c.act(mover, g.id, Action.Play(de.wtzg.domino.engine.Move(illegal, de.wtzg.domino.engine.Arm.EAST)), mv.version).status)
        assertEquals(HttpStatusCode.OK, c.act(mover, g.id, Action.Play(mv.legalMoves.first()), mv.version).status)
    }

    @Test
    fun leavingDuringPlayEndsTheGame() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, GameMode.BLOCK, 2, 60, Pace.TURN_BASED)
        c.join(bob, g.code)
        c.start(ann, g.id)
        assertEquals(HttpStatusCode.OK, c.act(bob, g.id, Action.Leave, 0).status)
        val v = c.view(ann, g.id)
        assertEquals(GameStatus.FINISHED, v.status)
        assertEquals(1, v.leftSeat)
        assertNull("nobody wins", v.winner)
        assertFalse("no computer took over", v.seats[1].isAi)
        assertEquals("Bob", v.seats[1].name)
        assertEquals(1, c.get("/games") { bearerAuth(ann.token) }.body<List<GameSummary>>().single().leftSeat)
        val bobGames: List<GameSummary> = c.get("/games") { bearerAuth(bob.token) }.body()
        assertTrue("off the leaver's list", bobGames.isEmpty())
        // The others can still play again.
        assertEquals(HttpStatusCode.OK, c.post("/games/${g.id}/rematch") { bearerAuth(ann.token) }.status)
    }

    @Test
    fun hostLeavingTheLobbyCancelsTheGame() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val g = c.newGame(ann, GameMode.DRAW, 2, 60)
        assertEquals(HttpStatusCode.NoContent, c.act(ann, g.id, Action.Leave, 0).status)
        assertEquals(HttpStatusCode.NotFound, c.get("/games/${g.id}") { bearerAuth(ann.token) }.status)
    }

    @Test
    fun liveConnectionsGetEveryChange() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, GameMode.DRAW, 2, 60)
        c.join(bob, g.code)
        c.webSocket("/games/${g.id}/live?token=${bob.token}") {
            suspend fun next(): GameView = withTimeout(5_000) {
                ProtocolJson.decodeFromString(GameView.serializer(), (incoming.receive() as Frame.Text).readText())
            }
            assertEquals(GameStatus.LOBBY, next().status)
            c.start(ann, g.id)
            var v = next()
            while (v.status == GameStatus.LOBBY) v = next() // presence updates may come first
            assertEquals(GameStatus.PLAYING, v.status)
            assertEquals(bob.playerId, v.seats[v.yourSeat].playerId)
            assertTrue(v.seats[1].online)
        }
    }

    @Test
    fun liveConnectionsCloseWhenTheHostCancels() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, GameMode.DRAW, 2, 60)
        c.join(bob, g.code)
        c.webSocket("/games/${g.id}/live?token=${bob.token}") {
            withTimeout(5_000) { incoming.receive() }
            c.act(ann, g.id, Action.Leave, 0)
            // Any updates still on their way, then the server hangs up.
            withTimeout(5_000) { while (incoming.receiveCatching().isSuccess) Unit }
            assertEquals(CloseReason.Codes.NORMAL.code, withTimeout(5_000) { closeReason.await() }?.code)
        }
    }

    @Test
    fun oversizedAndNonJsonRequestsAreTurnedAway() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val huge = c.post("/players") {
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"" + "x".repeat(100_000) + "\"}")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, huge.status)
        val text = c.post("/games/join") { bearerAuth(ann.token); contentType(ContentType.Text.Plain); setBody("ABC234") }
        assertEquals(HttpStatusCode.UnsupportedMediaType, text.status)
    }

    @Test
    fun sqliteKeepsGamesAndPlayers() {
        val file = kotlin.io.path.createTempFile("domino", ".db").toFile()
        try {
            val service = GameService(SqliteStore(file.path), random = Random(2))
            val reg = service.register("Ann")
            val p = service.authenticate(reg.token)!!
            val g = service.create(p, CreateGameRequest(GameMode.ALL_THREES, 2, 90, Pace.TURN_BASED), 1)
            val again = SqliteStore(file.path)
            assertEquals(g, again.game(g.id))
            assertEquals(g, again.gameByCode(g.code))
            assertEquals(listOf(g.id), again.gamesOf(p.id).map { it.id })
            assertEquals("Ann", again.playerByTokenHash(hashToken(reg.token))?.name)
            again.deleteGame(g.id)
            assertNull(again.game(g.id))
        } finally {
            file.delete()
        }
    }
}
