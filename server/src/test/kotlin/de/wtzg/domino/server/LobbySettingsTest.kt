package de.wtzg.domino.server

import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.ServerFeatures
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LobbySettingsTest {
    private fun ApplicationTestBuilder.setUp(): HttpClient {
        application { dominoServer(GameService(InMemoryStore(), random = Random(7)), LiveHub()) }
        return createClient { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    private suspend fun HttpClient.register(name: String): Registration =
        post("/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest(name)) }.body()

    private suspend fun HttpClient.newGame(host: Registration, players: Int, visibility: Visibility = Visibility.PRIVATE): GameView =
        post("/games") {
            bearerAuth(host.token); contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(GameMode.ALL_FIVES, players, 100, Pace.LIVE, visibility))
        }.body()

    private suspend fun HttpClient.join(r: Registration, code: String): GameView =
        post("/games/join") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(JoinRequest(code)) }.body()

    private suspend fun HttpClient.settings(r: Registration, id: String, req: SettingsRequest): HttpResponse =
        post("/games/$id/settings") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(req) }

    private suspend fun HttpClient.view(r: Registration, id: String): GameView = get("/games/$id") { bearerAuth(r.token) }.body()

    @Test
    fun hostChangesTheGameAndEveryoneSeesIt() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, players = 2)
        c.join(bob, g.code)

        val res = c.settings(ann, g.id, SettingsRequest(GameMode.ALL_THREES, players = 4, target = 90, pace = Pace.TURN_BASED))
        assertEquals(HttpStatusCode.OK, res.status)
        val v = c.view(bob, g.id)
        assertEquals(GameMode.ALL_THREES, v.rules.mode)
        assertEquals(90, v.rules.target)
        assertEquals(4, v.rules.players)
        assertEquals(Pace.TURN_BASED, v.pace)
        assertEquals(listOf("Ann", "Bob", "", ""), v.seats.map { it.name })
        assertEquals(1, v.yourSeat)
        assertEquals(g.code, v.code)
    }

    @Test
    fun shrinkingTheTableKeepsEveryoneSeated() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val cat = c.register("Cat")
        val g = c.newGame(ann, players = 4)
        c.join(bob, g.code)
        c.join(cat, g.code)
        // Bob leaves seat 1 free, so Cat sits in seat 2.
        c.post("/games/${g.id}/actions") {
            bearerAuth(bob.token); contentType(ContentType.Application.Json)
            setBody(de.wtzg.domino.protocol.ActionRequest(de.wtzg.domino.protocol.Action.Leave, 0))
        }
        assertEquals(listOf("Ann", "", "Cat", ""), c.view(ann, g.id).seats.map { it.name })

        assertEquals(HttpStatusCode.OK, c.settings(ann, g.id, SettingsRequest(GameMode.ALL_FIVES, 2, 100, Pace.LIVE)).status)
        val v = c.view(cat, g.id)
        assertEquals(listOf("Ann", "Cat"), v.seats.map { it.name })
        assertEquals(1, v.yourSeat)
    }

    @Test
    fun rejectsWhatShouldBeRejected() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val cat = c.register("Cat")
        val g = c.newGame(ann, players = 3)
        c.join(bob, g.code)
        c.join(cat, g.code)
        val ok = SettingsRequest(GameMode.DRAW, 3, 60, Pace.LIVE)

        assertEquals(HttpStatusCode.Forbidden, c.settings(bob, g.id, ok).status)
        assertEquals("3 people are seated", HttpStatusCode.Conflict, c.settings(ann, g.id, ok.copy(players = 2)).status)
        assertEquals(HttpStatusCode.BadRequest, c.settings(ann, g.id, ok.copy(players = 5)).status)
        assertEquals(HttpStatusCode.BadRequest, c.settings(ann, g.id, ok.copy(target = 5)).status)

        c.post("/games/${g.id}/start") { bearerAuth(ann.token); contentType(ContentType.Application.Json); setBody(StartRequest()) }
        assertEquals(HttpStatusCode.Conflict, c.settings(ann, g.id, ok).status)
    }

    @Test
    fun openGamesListShowsTheNewSettings() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, players = 2, visibility = Visibility.PUBLIC)
        c.settings(ann, g.id, SettingsRequest(GameMode.BLOCK, 3, 60, Pace.TURN_BASED))
        val listed = c.get("/games/open") { bearerAuth(bob.token) }.body<List<OpenGame>>().single()
        assertEquals(GameMode.BLOCK, listed.mode)
        assertEquals(3, listed.seats)
        assertEquals(Pace.TURN_BASED, listed.pace)
        assertNull(listed.takeIf { it.id != g.id })
    }

    @Test
    fun serverSaysItHasLobbySettings() = testApplication {
        val c = setUp()
        assertTrue(ServerFeatures.LOBBY_SETTINGS in c.get("/version").body<ServerInfo>().features)
    }
}
