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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") { dominoServer(GameService(InMemoryStore()), LiveHub()) }.start()
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        url = "http://127.0.0.1:$port"
    }

    @After fun tearDown() {
        scope.cancel()
        server.stop(100, 500)
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
}
