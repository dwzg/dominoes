package de.wtzg.domino.server.compat

import de.wtzg.domino.engine.Arm
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Strategy
import de.wtzg.domino.engine.Tile
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.ActionRequest
import de.wtzg.domino.protocol.ApiError
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.ProtocolVersion
import de.wtzg.domino.protocol.PublicEvent
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.RenameRequest
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.server.GameRecord
import de.wtzg.domino.server.GameService
import de.wtzg.domino.server.InMemoryStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import kotlin.random.Random

/**
 * Every message type in the protocol, with the serializer that reads it. The JSON files
 * in src/test/resources/compat/v<N> are what protocol N wrote; they must keep loading.
 */
val COMPAT_FILES: Map<String, KSerializer<*>> = mapOf(
    "register-request.json" to RegisterRequest.serializer(),
    "rename-request.json" to RenameRequest.serializer(),
    "registration.json" to Registration.serializer(),
    "create-game-request.json" to CreateGameRequest.serializer(),
    "join-request.json" to JoinRequest.serializer(),
    "start-request.json" to StartRequest.serializer(),
    "action-play.json" to ActionRequest.serializer(),
    "action-draw.json" to ActionRequest.serializer(),
    "action-pass.json" to ActionRequest.serializer(),
    "action-leave.json" to ActionRequest.serializer(),
    "api-error.json" to ApiError.serializer(),
    "events.json" to ListSerializer(PublicEvent.serializer()),
    "game-view-lobby.json" to GameView.serializer(),
    "game-view-playing.json" to GameView.serializer(),
    "games.json" to ListSerializer(GameSummary.serializer()),
    "game-record.json" to GameRecord.serializer(),
)

/**
 * Writes the reference files for the current protocol version, once. Run with
 * WRITE_COMPAT_FIXTURES=1 after raising ProtocolVersion.CURRENT; never rewrite an
 * existing version's files.
 */
fun writeCompatFixtures(root: File) {
    val dir = File(root, "v${ProtocolVersion.CURRENT}")
    check(!dir.exists()) { "$dir already exists; reference files for a released protocol never change" }
    dir.mkdirs()
    var now = 1_761_300_000_000L
    val service = GameService(InMemoryStore(), clock = { now++ }, random = Random(7))
    val annReg = service.register("Ann")
    val ann = service.authenticate(annReg.token)!!
    val bob = service.register("Bob").let { service.authenticate(it.token)!! }

    val lobby = service.create(ann, CreateGameRequest(GameMode.ALL_FIVES, 3, 100, Pace.TURN_BASED), 1)
    val lobbyView = service.view(runBlocking { service.join(bob, lobby.code, 1) }, ann.id, setOf(ann.id), 1)
    var g = runBlocking { service.start(ann, lobby.id, Difficulty.HARD) }
    // Play until a hand has finished and the next one has a few moves.
    var guard = 0
    while (g.lastHand == null || (g.match?.round?.events?.size ?: 0) < 3) {
        val r = g.match!!.round
        val who = if (r.turn == 0) ann else bob
        val action = when {
            r.canDraw -> Action.Draw
            r.mustPass -> Action.Pass
            else -> Action.Play(Strategy.chooseMove(r, Difficulty.NORMAL, Random(guard))!!)
        }
        g = runBlocking { service.act(who, g.id, action, g.version)!! }
        check(++guard < 2_000)
    }

    fun <T> write(name: String, serializer: KSerializer<T>, value: T) =
        File(dir, name).writeText(ProtocolJson.encodeToString(serializer, value) + "\n")

    write("register-request.json", RegisterRequest.serializer(), RegisterRequest("Ann"))
    write("rename-request.json", RenameRequest.serializer(), RenameRequest("Annie"))
    write("registration.json", Registration.serializer(), annReg)
    write("create-game-request.json", CreateGameRequest.serializer(), CreateGameRequest(GameMode.ALL_FIVES, 3, 100, Pace.TURN_BASED))
    write("join-request.json", JoinRequest.serializer(), JoinRequest(lobby.code))
    write("start-request.json", StartRequest.serializer(), StartRequest(Difficulty.HARD))
    write("action-play.json", ActionRequest.serializer(), ActionRequest(Action.Play(Move(Tile(5, 3), Arm.WEST)), 7))
    write("action-draw.json", ActionRequest.serializer(), ActionRequest(Action.Draw, 8))
    write("action-pass.json", ActionRequest.serializer(), ActionRequest(Action.Pass, 9))
    write("action-leave.json", ActionRequest.serializer(), ActionRequest(Action.Leave, 10))
    write("api-error.json", ApiError.serializer(), ApiError("It's not your turn"))
    write(
        "events.json", ListSerializer(PublicEvent.serializer()),
        listOf(
            PublicEvent.Played(0, Move(Tile(5, 5), Arm.EAST), 10),
            PublicEvent.Drew(1, listOf(5, 3)),
            PublicEvent.Passed(2, listOf(5, 3)),
        ),
    )
    write("game-view-lobby.json", GameView.serializer(), lobbyView)
    write("game-view-playing.json", GameView.serializer(), service.view(g, ann.id, setOf(ann.id, bob.id), 1))
    write("games.json", ListSerializer(GameSummary.serializer()), service.gamesOf(ann, 1))
    write("game-record.json", GameRecord.serializer(), g)
}
