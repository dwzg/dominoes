package de.wtzg.domino.feature.online

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.data.online.onlineHttpClient
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.Visibility
import de.wtzg.domino.protocol.canDraw
import de.wtzg.domino.protocol.legalMoves
import de.wtzg.domino.protocol.mustPass
import de.wtzg.domino.server.GameService
import de.wtzg.domino.server.InMemoryStore
import de.wtzg.domino.server.LiveHub
import de.wtzg.domino.server.dominoServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Two "phones" talk to a real server over HTTP and WebSockets. */
class OnlineEndToEndTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var url: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") { dominoServer(GameService(InMemoryStore()), LiveHub()) }.start()
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        url = "http://127.0.0.1:$port"
    }

    @After fun tearDown() {
        scope.cancel()
        // Some tests stop the server themselves.
        runCatching { server.stop(100, 500) }
        Dispatchers.resetMain()
    }

    private fun phone(name: String): OnlineRepository {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("$name.preferences_pb") }
        return OnlineRepository(store, SettingsRepository(store), onlineHttpClient(), defaultServer = url)
    }

    @Test
    fun strangersFindPublicGamesAndJoin() = runBlocking {
        val ann = phone("ann-public")
        val bob = phone("bob-public")
        assertTrue(de.wtzg.domino.protocol.ServerFeatures.PUBLIC_GAMES in ann.api().serverInfo().features)
        ann.register("Ann")
        bob.register("Bob")

        val open = ann.api().create(
            CreateGameRequest(GameMode.ALL_FIVES, players = 2, target = 100, pace = Pace.LIVE, visibility = Visibility.PUBLIC),
        )
        val hidden = ann.api().create(CreateGameRequest(GameMode.ALL_FIVES, players = 2, target = 100, pace = Pace.LIVE))
        assertEquals(Visibility.PUBLIC, open.visibility)
        assertEquals(listOf(open.id), bob.api().openGames().map { it.id })

        // Bob joins from the list, which fills the table and takes it off.
        val listed = bob.api().openGames().single()
        assertEquals("Ann", listed.host)
        assertEquals(open.id, bob.api().join(listed.code).id)
        assertTrue(bob.api().openGames().isEmpty())

        // The host can list a private lobby later, and hide it again.
        ann.api().setVisibility(hidden.id, Visibility.PUBLIC)
        assertEquals(listOf(hidden.id), bob.api().openGames().map { it.id })
        assertEquals(Visibility.PRIVATE, ann.api().setVisibility(hidden.id, Visibility.PRIVATE).visibility)
        assertTrue(bob.api().openGames().isEmpty())
    }

    @Test
    fun hostChangesTheGameInTheLobby() = runBlocking {
        val ann = phone("ann-settings")
        val bob = phone("bob-settings")
        assertTrue(de.wtzg.domino.protocol.ServerFeatures.LOBBY_SETTINGS in ann.api().serverInfo().features)
        ann.register("Ann")
        bob.register("Bob")
        val lobby = ann.api().create(CreateGameRequest(GameMode.ALL_FIVES, players = 2, target = 100, pace = Pace.LIVE))
        bob.api().join(lobby.code)

        ann.api().updateSettings(lobby.id, de.wtzg.domino.protocol.SettingsRequest(GameMode.BLOCK, 3, 60, Pace.TURN_BASED))
        val seen = bob.api().game(lobby.id)
        assertEquals(GameMode.BLOCK, seen.rules.mode)
        assertEquals(3, seen.seats.size)
        assertEquals(Pace.TURN_BASED, seen.pace)

        // Only the host may change it.
        val notHost = runCatching { bob.api().updateSettings(lobby.id, de.wtzg.domino.protocol.SettingsRequest(GameMode.DRAW, 3, 60, Pace.LIVE)) }
        assertEquals(403, (notHost.exceptionOrNull() as OnlineException).status)
    }

    @Test
    fun bothPlayersLandInTheSameRematch() = runBlocking {
        val ann = phone("ann-rematch")
        val bob = phone("bob-rematch")
        ann.register("Ann")
        bob.register("Bob")
        // A custom target, and one move each so the game ends quickly once someone leaves.
        val lobby = ann.api().create(CreateGameRequest(GameMode.DRAW, players = 2, target = 13, pace = Pace.LIVE))
        assertEquals(13, lobby.rules.target)
        bob.api().join(lobby.code)
        ann.api().start(lobby.id, Difficulty.EASY)
        // Play until it's over: whoever is to move plays the first legal move, draws or passes.
        var v = ann.api().game(lobby.id)
        var steps = 0
        while (v.status != GameStatus.FINISHED) {
            val mover = if (v.turn == v.yourSeat) ann else bob
            val mine = mover.api().game(lobby.id)
            val action = when {
                mine.legalMoves.isNotEmpty() -> Action.Play(mine.legalMoves.first())
                mine.canDraw -> Action.Draw
                else -> Action.Pass
            }
            mover.api().act(lobby.id, action, mine.version)
            v = ann.api().game(lobby.id)
            assertTrue("match never ends", ++steps < 2_000)
        }
        val first = bob.api().rematch(lobby.id)
        assertEquals(first.id, ann.api().game(lobby.id).rematchId)
        val second = ann.api().rematch(lobby.id)
        assertEquals(first.id, second.id)
        assertEquals(listOf("Bob", "Ann"), second.seats.map { it.name })
        assertEquals(13, second.rules.target)
    }

    @Test
    fun liveConnectionSaysWhenItDrops() = runBlocking {
        val ann = phone("ann-live")
        ann.register("Ann")
        val lobby = ann.api().create(CreateGameRequest(GameMode.DRAW, players = 2, target = 60, pace = Pace.LIVE))
        val states = kotlinx.coroutines.channels.Channel<Boolean>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val api = ann.api()
        val watching = scope.launch { api.live(lobby.id) { states.trySend(it) }.collect {} }
        try {
            withTimeout(10_000) { while (!states.receive()) Unit }
            server.stop(0, 100)
            withTimeout(10_000) { while (states.receive()) Unit }
        } finally {
            watching.cancel()
        }
    }

    @Test
    fun friendsCreateJoinAndPlay() = runBlocking {
        val ann = phone("ann")
        val bob = phone("bob")
        val info = ann.api().serverInfo()
        assertEquals(de.wtzg.domino.protocol.ProtocolVersion.CURRENT, info.protocol)
        assertEquals(null, compatibilityProblem(info))
        ann.register("Ann")
        bob.register("Bob")
        assertEquals("Ann", ann.account.first().registration?.name)

        val lobby = ann.api().create(CreateGameRequest(GameMode.DRAW, players = 3, target = 60, pace = Pace.TURN_BASED))
        bob.api().join(lobby.code)

        // Bob watches live while Ann starts the game.
        val bobLive = scope.async {
            withTimeout(10_000) { bob.api().live(lobby.id).first { it.status == GameStatus.PLAYING } }
        }
        Thread.sleep(300) // let the socket connect
        val started = ann.api().start(lobby.id, Difficulty.EASY)
        assertEquals(GameStatus.PLAYING, started.status)
        val bobSees = bobLive.await()
        assertEquals("Bob", bobSees.seats[bobSees.yourSeat].name)
        assertTrue(bobSees.seats.last().isAi)

        // Play a few turns as whoever is to move; the computer moves on its own.
        val phones = mapOf("Ann" to ann, "Bob" to bob)
        var v: GameView = started
        repeat(30) {
            v = ann.api().game(lobby.id)
            if (v.status != GameStatus.PLAYING) return@repeat
            val who = phones.getValue(v.seats[v.turn].name)
            val mine = who.api().game(lobby.id)
            val action = when {
                mine.legalMoves.isNotEmpty() -> Action.Play(mine.legalMoves.first())
                mine.canDraw -> Action.Draw
                mine.mustPass -> Action.Pass
                else -> fail("Nothing to do") as Nothing
            }
            assertNotNull(who.api().act(lobby.id, action, mine.version))
        }

        // What the game screen would show Bob.
        val bobView = bob.api().game(lobby.id)
        val table = OnlineGameState(view = bobView, ackedHand = bobView.lastHand?.handNumber ?: 0).toTable()!!
        assertEquals(2, table.opponents.size)
        assertEquals(bobView.hand, table.hand)
        assertEquals(bobView.seats[(bobView.yourSeat + 1) % 3].name, table.opponents.first().name)

        val list = bob.api().games()
        assertEquals(lobby.id, list.single().id)

        // Stale moves are refused with a clear message.
        val e = runCatching { bob.api().act(lobby.id, Action.Draw, version = 0) }.exceptionOrNull()
        assertTrue(e is OnlineException && e.status == 409)
    }

    @Test
    fun unreachableServerGivesAFriendlyError() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("x.preferences_pb") }
        val repo = OnlineRepository(store, SettingsRepository(store), onlineHttpClient(), defaultServer = "http://127.0.0.1:1")
        val e = runCatching { repo.register("Ann") }.exceptionOrNull()
        assertTrue(e is OnlineException)
        assertTrue(e!!.message!!.startsWith("Can't reach the server"))
    }

    @Test
    fun friendsChallengeEachOtherThroughTheApp() = runBlocking {
        val ann = phone("ann-friends")
        val bob = phone("bob-friends")
        assertTrue(de.wtzg.domino.protocol.ServerFeatures.FRIENDS in ann.api().serverInfo().features)
        ann.register("Ann")
        bob.register("Bob")

        // Bob adds Ann with her code as she'd share it; Ann accepts.
        val code = formatFriendCode(ann.api().friends().friendCode)
        bob.api().addFriend(friendCodeFrom(code)!!)
        val request = ann.api().friends().requests.single()
        assertEquals("Bob", request.name)
        assertEquals(listOf("Bob"), ann.api().acceptFriend(request.playerId).friends.map { it.name })

        // Ann challenges Bob: a lobby with an invite in it.
        val bobId = ann.api().friends().friends.single().playerId
        val lobby = ann.api().create(CreateGameRequest(GameMode.ALL_THREES, players = 2, target = 90, pace = Pace.TURN_BASED))
        assertEquals(listOf("Bob"), ann.api().invite(lobby.id, listOf(bobId)).invited.map { it.name })

        val invite = bob.api().friends().invites.single()
        assertEquals("Ann", invite.from)
        assertEquals(GameMode.ALL_THREES, invite.mode)
        assertEquals(lobby.id, bob.api().join(invite.code).id)
        assertTrue(bob.api().friends().invites.isEmpty())
        assertTrue(ann.api().game(lobby.id).invited.isEmpty())

        // A second invite, declined this time.
        val next = ann.api().create(CreateGameRequest(GameMode.DRAW, players = 2, target = 60, pace = Pace.LIVE))
        ann.api().invite(next.id, listOf(bobId))
        assertTrue(bob.api().declineInvite(next.id).invites.isEmpty())
        assertTrue(ann.api().game(next.id).invited.isEmpty())
    }

    private suspend fun <T> eventually(read: () -> T?): T = withTimeout(10_000) {
        var value = read()
        while (value == null) {
            delay(20)
            value = read()
        }
        value
    }

    @Test
    fun aGuestNoticesWhenTheHostCancels() = runBlocking {
        val ann = phone("ann-cancel")
        val bob = phone("bob-cancel")
        ann.register("Ann")
        bob.register("Bob")
        val lobby = ann.api().create(CreateGameRequest(GameMode.DRAW, players = 2, target = 60, pace = Pace.LIVE))
        bob.api().join(lobby.code)

        val bobVm = OnlineGameViewModel(lobby.id, bob)
        val live = scope.launch { bobVm.stayConnected() }
        eventually { bobVm.state.value.view }
        ann.api().act(lobby.id, Action.Leave, 0)
        eventually { bobVm.state.value.gone.takeIf { it } }
        live.cancel()
    }

    @Test
    fun aChallengeInvitesTheFriendFromTheLobby() = runBlocking {
        val ann = phone("ann-challenge")
        val bob = phone("bob-challenge")
        ann.register("Ann")
        bob.register("Bob")
        bob.api().addFriend(ann.api().friends().friendCode)
        val bobId = ann.api().friends().requests.single().playerId
        ann.api().acceptFriend(bobId)

        val lobby = ann.api().create(CreateGameRequest(GameMode.DRAW, players = 2, target = 60, pace = Pace.LIVE))
        val annVm = OnlineGameViewModel(lobby.id, ann, invite = bobId)
        val live = scope.launch { annVm.stayConnected() }
        eventually { annVm.state.value.view?.invited?.takeIf { it.isNotEmpty() } }
        assertEquals(lobby.id, bob.api().friends().invites.single().gameId)
        live.cancel()
    }
}
