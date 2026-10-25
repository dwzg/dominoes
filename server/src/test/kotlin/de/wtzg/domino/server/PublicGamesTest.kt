package de.wtzg.domino.server

import de.wtzg.domino.engine.Difficulty
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
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.Visibility
import de.wtzg.domino.protocol.VisibilityRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager
import kotlin.random.Random

class PublicGamesTest {
    private fun ApplicationTestBuilder.setUp(service: GameService = GameService(InMemoryStore(), random = Random(1))): HttpClient {
        application { dominoServer(service, LiveHub()) }
        return createClient { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    private suspend fun HttpClient.register(name: String): Registration =
        post("/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest(name)) }.body()

    private suspend fun HttpClient.newGame(host: Registration, visibility: Visibility, players: Int = 3): GameView =
        post("/games") {
            bearerAuth(host.token); contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(GameMode.ALL_FIVES, players, 100, Pace.LIVE, visibility))
        }.body()

    private suspend fun HttpClient.open(r: Registration): List<OpenGame> = get("/games/open") { bearerAuth(r.token) }.body()

    private suspend fun HttpClient.join(r: Registration, code: String): HttpResponse =
        post("/games/join") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(JoinRequest(code)) }

    private suspend fun HttpClient.visibility(r: Registration, id: String, v: Visibility): HttpResponse =
        post("/games/$id/visibility") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(VisibilityRequest(v)) }

    @Test
    fun publicLobbiesAreListedUntilTheyAreFull() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val cat = c.register("Cat")
        val public = c.newGame(ann, Visibility.PUBLIC)
        val private = c.newGame(ann, Visibility.PRIVATE)
        assertEquals(Visibility.PUBLIC, public.visibility)
        assertEquals(Visibility.PRIVATE, private.visibility)

        // Others see the public game only; the host doesn't see their own.
        val seen = c.open(bob).single()
        assertEquals(public.id, seen.id)
        assertEquals(public.code, seen.code)
        assertEquals("Ann", seen.host)
        assertEquals(3, seen.seats)
        assertEquals(1, seen.taken)
        assertTrue(c.open(ann).isEmpty())

        // Joining works with the listed code; you no longer see a game you're in.
        assertEquals(HttpStatusCode.OK, c.join(bob, seen.code).status)
        assertTrue(c.open(bob).isEmpty())
        assertEquals(2, c.open(cat).single().taken)

        // Full: off the list.
        assertEquals(HttpStatusCode.OK, c.join(cat, seen.code).status)
        assertTrue(c.open(c.register("Dan")).isEmpty())

        // The fixed /games/open path doesn't swallow game ids.
        assertEquals(HttpStatusCode.OK, c.get("/games/${public.id}") { bearerAuth(ann.token) }.status)
    }

    @Test
    fun hostCanListOrHideTheLobbyBeforeItStarts() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.newGame(ann, Visibility.PRIVATE)
        assertTrue(c.open(bob).isEmpty())

        val listed = c.visibility(ann, g.id, Visibility.PUBLIC)
        assertEquals(HttpStatusCode.OK, listed.status)
        assertEquals(Visibility.PUBLIC, listed.body<GameView>().visibility)
        assertEquals(g.id, c.open(bob).single().id)

        assertEquals(HttpStatusCode.OK, c.join(bob, g.code).status)
        assertEquals(HttpStatusCode.Forbidden, c.visibility(bob, g.id, Visibility.PRIVATE).status)

        assertEquals(HttpStatusCode.OK, c.visibility(ann, g.id, Visibility.PRIVATE).status)
        assertTrue(c.open(c.register("Cat")).isEmpty())

        c.visibility(ann, g.id, Visibility.PUBLIC)
        c.post("/games/${g.id}/start") { bearerAuth(ann.token); contentType(ContentType.Application.Json); setBody(StartRequest(Difficulty.EASY)) }
        assertTrue("started games aren't listed", c.open(c.register("Dan")).isEmpty())
        assertEquals(HttpStatusCode.Conflict, c.visibility(ann, g.id, Visibility.PRIVATE).status)
    }

    @Test
    fun abandonedLobbiesDropOffTheList() = testApplication {
        var now = 1_000_000_000L
        val c = setUp(GameService(InMemoryStore(), clock = { now }, random = Random(3)))
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        c.newGame(ann, Visibility.PUBLIC)
        assertEquals(1, c.open(bob).size)
        now += OPEN_GAME_MAX_AGE_MS + 1
        assertTrue(c.open(bob).isEmpty())
    }

    @Test
    fun invitePageTellsTheAppWhichServerTheGameIsOn() = testApplication {
        val c = setUp()
        val page = c.get("/join/abc234").bodyAsText()
        assertTrue(page, "dominoes://join/ABC234?server=http%3A%2F%2Flocalhost%3A80" in page)
    }

    @Test
    fun serverSaysItHasPublicGames() = testApplication {
        val c = setUp()
        val info = c.get("/version").body<ServerInfo>()
        assertTrue(ServerFeatures.PUBLIC_GAMES in info.features)
    }

    @Test
    fun appsThatDontKnowVisibilityCreatePrivateGames() {
        val old = """{"mode":"ALL_FIVES","players":2,"target":100,"pace":"LIVE"}"""
        assertEquals(Visibility.PRIVATE, ProtocolJson.decodeFromString(CreateGameRequest.serializer(), old).visibility)
    }

    @Test
    fun sqliteKeepsTheOpenGamesListInStep() = runBlocking {
        val file = kotlin.io.path.createTempFile("domino", ".db").toFile()
        try {
            val service = GameService(SqliteStore(file.path), random = Random(4))
            val ann = service.authenticate(service.register("Ann").token)!!
            val bob = service.authenticate(service.register("Bob").token)!!
            val g = service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.TURN_BASED, Visibility.PUBLIC))
            service.create(ann, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.TURN_BASED, Visibility.PRIVATE))

            // Survives a restart.
            assertEquals(listOf(g.id), SqliteStore(file.path).openGames(10).map { it.id })
            assertEquals(listOf(g.id), service.openGames(bob).map { it.id })

            service.join(bob, g.code)
            assertTrue("full games leave the list", SqliteStore(file.path).openGames(10).isEmpty())

            val h = service.create(bob, CreateGameRequest(GameMode.DRAW, 2, 60, Pace.LIVE, Visibility.PUBLIC))
            SqliteStore(file.path).deleteGame(h.id)
            assertTrue("deleted games leave the list", SqliteStore(file.path).openGames(10).isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun databasesFromTheFirstReleaseAreUpgraded() {
        val file = kotlin.io.path.createTempFile("domino", ".db").toFile()
        try {
            // Exactly the first release's schema, with one of its games in it.
            val service1 = GameService(InMemoryStore(), random = Random(5))
            val ann = service1.authenticate(service1.register("Ann").token)!!
            val old = service1.create(ann, CreateGameRequest(GameMode.BLOCK, 2, 60, Pace.LIVE))
            DriverManager.getConnection("jdbc:sqlite:${file.path}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeUpdate(
                        "CREATE TABLE players (id TEXT PRIMARY KEY, name TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)",
                    )
                    st.executeUpdate("CREATE TABLE games (id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, data TEXT NOT NULL, updated_at INTEGER NOT NULL)")
                    st.executeUpdate("CREATE TABLE game_players (game_id TEXT NOT NULL, player_id TEXT NOT NULL, PRIMARY KEY (game_id, player_id))")
                    st.executeUpdate("PRAGMA user_version = 1")
                }
                conn.prepareStatement("INSERT INTO games (id, code, data, updated_at) VALUES (?, ?, ?, ?)").use {
                    // A record as the first release wrote it: no visibility field.
                    val json = ProtocolJson.encodeToJsonElement(GameRecord.serializer(), old).let { e ->
                        kotlinx.serialization.json.JsonObject(e.jsonObjectWithout("visibility"))
                    }
                    it.setString(1, old.id); it.setString(2, old.code); it.setString(3, json.toString()); it.setLong(4, old.updatedAt)
                    it.executeUpdate()
                }
            }
            val store = SqliteStore(file.path)
            assertEquals(Visibility.PRIVATE, store.game(old.id)!!.visibility)
            assertTrue(store.openGames(10).isEmpty())
            val version = DriverManager.getConnection("jdbc:sqlite:${file.path}").use { conn ->
                conn.createStatement().use { st -> st.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }
            }
            assertEquals("upgraded through every schema step", 3, version)
        } finally {
            file.delete()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectWithout(key: String) =
        (this as kotlinx.serialization.json.JsonObject).filterKeys { it != key }
}
