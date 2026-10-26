package de.wtzg.domino.server

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.AddFriendRequest
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.InviteRequest
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.ServerFeatures
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.StartRequest
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FriendsTest {
    private fun ApplicationTestBuilder.setUp(): HttpClient {
        application { dominoServer(GameService(InMemoryStore(), random = Random(11)), LiveHub()) }
        return createClient { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    private suspend fun HttpClient.register(name: String): Registration =
        post("/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest(name)) }.body()

    private suspend fun HttpClient.friends(r: Registration): FriendsView = get("/friends") { bearerAuth(r.token) }.body()

    private suspend fun HttpClient.add(r: Registration, req: AddFriendRequest): HttpResponse =
        post("/friends") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(req) }

    private suspend fun HttpClient.befriend(a: Registration, b: Registration) {
        add(a, AddFriendRequest(code = friends(b).friendCode))
        post("/friends/${a.playerId}/accept") { bearerAuth(b.token) }
    }

    private suspend fun HttpClient.lobby(host: Registration, players: Int = 3): GameView =
        post("/games") {
            bearerAuth(host.token); contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(GameMode.DRAW, players, 60, Pace.TURN_BASED))
        }.body()

    private suspend fun HttpClient.invite(r: Registration, gameId: String, vararg ids: String): HttpResponse =
        post("/games/$gameId/invites") { bearerAuth(r.token); contentType(ContentType.Application.Json); setBody(InviteRequest(ids.toList())) }

    @Test
    fun friendsAddEachOtherByCode() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val code = c.friends(ann).friendCode
        assertEquals(Limits.FRIEND_CODE_LENGTH, code.length)
        assertEquals("the code stays the same", code, c.friends(ann).friendCode)
        assertNotEquals(code, c.friends(bob).friendCode)

        // Typed loosely: lower case and a dash are fine.
        val dashed = code.lowercase().chunked(4).joinToString("-")
        val asked = c.add(bob, AddFriendRequest(code = dashed)).body<FriendsView>()
        assertEquals(listOf("Ann"), asked.sent.map { it.name })
        assertTrue(asked.friends.isEmpty())
        assertEquals(listOf("Bob"), c.friends(ann).requests.map { it.name })

        val accepted = c.post("/friends/${bob.playerId}/accept") { bearerAuth(ann.token) }.body<FriendsView>()
        assertEquals(listOf("Bob"), accepted.friends.map { it.name })
        assertTrue(accepted.requests.isEmpty())
        val bobs = c.friends(bob)
        assertEquals(listOf(ann.playerId), bobs.friends.map { it.playerId })
        assertTrue(bobs.sent.isEmpty())

        // Unfriending works from either side and clears both lists.
        c.post("/friends/${ann.playerId}/remove") { bearerAuth(bob.token) }
        assertTrue(c.friends(ann).friends.isEmpty())
        assertTrue(c.friends(bob).friends.isEmpty())
    }

    @Test
    fun askingSomeoneWhoAskedYouMakesYouFriends() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        c.add(ann, AddFriendRequest(code = c.friends(bob).friendCode))
        val both = c.add(bob, AddFriendRequest(code = c.friends(ann).friendCode)).body<FriendsView>()
        assertEquals(listOf("Ann"), both.friends.map { it.name })
        assertTrue(both.requests.isEmpty() && both.sent.isEmpty())
        assertEquals(listOf("Bob"), c.friends(ann).friends.map { it.name })
    }

    @Test
    fun badRequestsAreTurnedAway() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        assertEquals(HttpStatusCode.BadRequest, c.add(ann, AddFriendRequest(code = c.friends(ann).friendCode)).status)
        assertEquals(HttpStatusCode.NotFound, c.add(ann, AddFriendRequest(code = "ZZZZZZZZ")).status)
        assertEquals(HttpStatusCode.NotFound, c.post("/friends/${bob.playerId}/accept") { bearerAuth(ann.token) }.status)
        // Strangers can't be added by id, only people you've played with.
        assertEquals(HttpStatusCode.NotFound, c.add(ann, AddFriendRequest(playerId = bob.playerId)).status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/friends").status)
    }

    @Test
    fun peopleYouPlayedWithAreSuggested() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val g = c.lobby(ann)
        c.post("/games/join") { bearerAuth(bob.token); contentType(ContentType.Application.Json); setBody(JoinRequest(g.code)) }
        assertEquals(listOf("Bob"), c.friends(ann).recent.map { it.name })

        val asked = c.add(ann, AddFriendRequest(playerId = bob.playerId)).body<FriendsView>()
        assertTrue("no longer suggested once asked", asked.recent.isEmpty())
        assertEquals(listOf("Bob"), asked.sent.map { it.name })
    }

    @Test
    fun friendsAreInvitedToALobbyAndJoinIt() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        val cat = c.register("Cat")
        c.befriend(ann, bob)
        val g = c.lobby(ann)

        assertEquals(HttpStatusCode.Forbidden, c.invite(ann, g.id, cat.playerId).status)
        assertEquals(HttpStatusCode.Forbidden, c.invite(cat, g.id, bob.playerId).status)

        val sent = c.invite(ann, g.id, bob.playerId)
        assertEquals(HttpStatusCode.OK, sent.status)
        assertEquals(listOf("Bob"), sent.body<GameView>().invited.map { it.name })

        val invite = c.friends(bob).invites.single()
        assertEquals(g.id, invite.gameId)
        assertEquals("Ann", invite.from)
        assertEquals(GameMode.DRAW, invite.mode)
        assertEquals(1, invite.taken)

        // Accepting is joining with the code; the invite goes away.
        c.post("/games/join") { bearerAuth(bob.token); contentType(ContentType.Application.Json); setBody(JoinRequest(invite.code)) }
        assertTrue(c.friends(bob).invites.isEmpty())
        val lobby = c.get("/games/${g.id}") { bearerAuth(ann.token) }.body<GameView>()
        assertTrue(lobby.invited.isEmpty())
        assertEquals(bob.playerId, lobby.seats[1].playerId)
    }

    @Test
    fun invitesCanBeDeclinedAndEndWithTheLobby() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        c.befriend(ann, bob)

        val first = c.lobby(ann)
        c.invite(ann, first.id, bob.playerId)
        val declined = c.post("/invites/${first.id}/decline") { bearerAuth(bob.token) }.body<FriendsView>()
        assertTrue(declined.invites.isEmpty())
        assertTrue(c.get("/games/${first.id}") { bearerAuth(ann.token) }.body<GameView>().invited.isEmpty())

        // Once the game starts, or the host cancels it, the invite is gone.
        val second = c.lobby(ann)
        c.invite(ann, second.id, bob.playerId)
        assertEquals(1, c.friends(bob).invites.size)
        c.post("/games/${second.id}/start") { bearerAuth(ann.token); contentType(ContentType.Application.Json); setBody(StartRequest(Difficulty.EASY)) }
        assertTrue(c.friends(bob).invites.isEmpty())
        assertEquals(HttpStatusCode.Conflict, c.invite(ann, second.id, bob.playerId).status)
    }

    @Test
    fun friendRequestsAreCapped() = runBlocking {
        val service = GameService(InMemoryStore(), random = Random(13))
        val spammer = service.authenticate(service.register("Spam").token)!!
        repeat(Limits.FRIEND_REQUESTS_MAX) { i ->
            val p = service.authenticate(service.register("P$i").token)!!
            service.friends.add(spammer, AddFriendRequest(code = service.friends.code(p)), 1)
        }
        val one = service.authenticate(service.register("One").token)!!
        val tooMany = runCatching { service.friends.add(spammer, AddFriendRequest(code = service.friends.code(one)), 1) }
        assertEquals(409, (tooMany.exceptionOrNull() as ApiException).status)
    }

    @Test
    fun unfriendingDropsInvitesBetweenThem() = testApplication {
        val c = setUp()
        val ann = c.register("Ann")
        val bob = c.register("Bob")
        c.befriend(ann, bob)
        val g = c.lobby(ann)
        c.invite(ann, g.id, bob.playerId)
        c.post("/friends/${bob.playerId}/remove") { bearerAuth(ann.token) }
        assertTrue(c.friends(bob).invites.isEmpty())
    }

    @Test
    fun aFriendCodeIsNeverHandedToTwoPlayers() {
        for (store in listOf(InMemoryStore(), SqliteStore(kotlin.io.path.createTempFile("domino", ".db").toFile().also { it.deleteOnExit() }.path))) {
            store.setFriendCode("a", "SAMECODE")
            assertTrue(runCatching { store.setFriendCode("b", "SAMECODE") }.isFailure)
            assertEquals("SAMECODE", store.friendCode("a"))
        }
    }

    @Test
    fun serverSaysItHasFriendsAndServesFriendLinks() = testApplication {
        val c = setUp()
        assertTrue(ServerFeatures.FRIENDS in c.get("/version").body<ServerInfo>().features)
        val page = c.get("/friend/abcd2345").bodyAsText()
        assertTrue(page, "dominoes://friends?code=ABCD2345&amp;server=http%3A%2F%2Flocalhost%3A80" in page)
    }

    @Test
    fun sqliteKeepsFriendsAndInvites() = runBlocking {
        val file = kotlin.io.path.createTempFile("domino", ".db").toFile()
        try {
            val service = GameService(SqliteStore(file.path), random = Random(12))
            val ann = service.authenticate(service.register("Ann").token)!!
            val bob = service.authenticate(service.register("Bob").token)!!
            val cat = service.authenticate(service.register("Cat").token)!!
            val code = service.friends.code(ann)
            service.friends.add(bob, AddFriendRequest(code = code), 1)
            service.friends.add(cat, AddFriendRequest(code = code), 1)
            service.friends.accept(ann, bob.id, 1)
            val g = service.create(ann, CreateGameRequest(GameMode.ALL_FIVES, 2, 100, Pace.LIVE), 1)
            service.invite(ann, g.id, listOf(bob.id))

            // A restart keeps everything.
            val again = GameService(SqliteStore(file.path))
            assertEquals(code, again.friends.code(ann))
            val anns = again.friends.page(ann, 1)
            assertEquals(listOf("Bob"), anns.friends.map { it.name })
            assertEquals(listOf("Cat"), anns.requests.map { it.name })
            assertEquals(listOf(g.id), again.friends.page(bob, 1).invites.map { it.gameId })
            assertEquals(listOf("Ann"), again.friends.page(cat, 1).sent.map { it.name })

            again.friends.remove(ann, cat.id, 1)
            assertTrue(again.friends.page(cat, 1).sent.isEmpty())
            SqliteStore(file.path).deleteGame(g.id)
            assertTrue(again.friends.page(bob, 1).invites.isEmpty())
        } finally {
            file.delete()
        }
    }
}
