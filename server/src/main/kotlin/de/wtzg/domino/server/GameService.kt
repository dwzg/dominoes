package de.wtzg.domino.server

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.Event
import de.wtzg.domino.engine.Match
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.engine.Strategy
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.HandSummary
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.PublicEvent
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.SeatView
import de.wtzg.domino.protocol.Visibility
import de.wtzg.domino.protocol.sinceProtocol
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** An error the client caused, with the HTTP status to answer with. */
class ApiException(val status: Int, message: String) : RuntimeException(message)

private fun badRequest(msg: String): Nothing = throw ApiException(400, msg)
private fun forbidden(msg: String): Nothing = throw ApiException(403, msg)
private fun notFound(msg: String): Nothing = throw ApiException(404, msg)
private fun conflict(msg: String): Nothing = throw ApiException(409, msg)
private fun updateApp(msg: String): Nothing = throw ApiException(426, msg)

/** Refuses games an app speaking [protocol] can't show (e.g. a newer game mode). */
private fun GameRecord.requireProtocol(protocol: Int) {
    if (rules.mode.sinceProtocol > protocol) updateApp("This game needs a newer version of the app. Please update it.")
}

private val AI_NAMES = listOf("Rosa", "Hank", "Mabel", "Otis")

/** Public lobbies untouched for this long are left out of the open games. */
const val OPEN_GAME_MAX_AGE_MS = 24L * 60 * 60 * 1000
private const val OPEN_GAMES_SHOWN = 50
private const val OPEN_GAMES_SCAN = 200
private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O or 1/I mix-ups

fun hashToken(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * The rules of online play. All game changes go through here, one at a time per game,
 * and every change bumps the game's version so clients can tell what's new.
 */
class GameService(
    private val store: Store,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    private val secure = SecureRandom()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private suspend fun <T> withGame(id: String, block: (GameRecord) -> T): T =
        locks.computeIfAbsent(id) { Mutex() }.withLock { block(store.game(id) ?: notFound("No such game")) }

    // --- Players -----------------------------------------------------------------------------

    fun register(name: String): Registration {
        val clean = cleanName(name)
        val token = ByteArray(32).also(secure::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val p = PlayerRecord(UUID.randomUUID().toString(), clean, hashToken(token), clock())
        store.addPlayer(p)
        return Registration(p.id, token, p.name)
    }

    fun authenticate(token: String?): PlayerRecord? = token?.takeIf { it.isNotBlank() }?.let { store.playerByTokenHash(hashToken(it)) }

    fun rename(player: PlayerRecord, name: String): Registration {
        val clean = cleanName(name)
        store.renamePlayer(player.id, clean)
        return Registration(player.id, "", clean)
    }

    private fun cleanName(name: String): String {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty() || n.length > Limits.NAME_MAX) badRequest("Names are 1 to ${Limits.NAME_MAX} characters")
        return n
    }

    // --- Lobby -------------------------------------------------------------------------------

    fun create(host: PlayerRecord, req: CreateGameRequest): GameRecord {
        if (req.players !in 2..4) badRequest("2 to 4 players")
        if (req.target !in 10..1000) badRequest("Target must be between 10 and 1000")
        val now = clock()
        val g = GameRecord(
            id = UUID.randomUUID().toString(),
            code = newCode(),
            pace = req.pace,
            rules = Rules(req.mode, req.players, req.target),
            seats = List(req.players) { i -> if (i == 0) SeatRecord(host.id, host.name) else SeatRecord() },
            status = GameStatus.LOBBY,
            createdAt = now,
            updatedAt = now,
            visibility = req.visibility,
        )
        store.saveGame(g)
        return g
    }

    /** The host lists the lobby in the open games, or takes it off. Only before the game starts. */
    suspend fun setVisibility(player: PlayerRecord, id: String, visibility: Visibility): GameRecord = withGame(id) { g ->
        if (g.seatOf(player.id) != g.hostSeat) forbidden("Only the host can change who can join")
        if (g.status != GameStatus.LOBBY) conflict("The game has already started")
        if (g.visibility == visibility) g else save(g.copy(visibility = visibility))
    }

    /**
     * Public lobbies [player] could join: not their own, with a free seat, in a mode their app
     * knows, and touched in the last [OPEN_GAME_MAX_AGE_MS] so abandoned lobbies drop off.
     */
    fun openGames(player: PlayerRecord, protocol: Int = 1): List<OpenGame> {
        val since = clock() - OPEN_GAME_MAX_AGE_MS
        return store.openGames(OPEN_GAMES_SCAN)
            .filter { it.updatedAt >= since && it.seatOf(player.id) < 0 && it.rules.mode.sinceProtocol <= protocol }
            .take(OPEN_GAMES_SHOWN)
            .map { g ->
                OpenGame(
                    id = g.id,
                    code = g.code,
                    mode = g.rules.mode,
                    target = g.rules.target,
                    pace = g.pace,
                    host = g.seats[g.hostSeat].name,
                    seats = g.seats.size,
                    taken = g.seats.count { it.playerId != null },
                    updatedAt = g.updatedAt,
                )
            }
    }

    private fun newCode(): String {
        repeat(20) {
            val code = (1..Limits.CODE_LENGTH).map { CODE_ALPHABET[secure.nextInt(CODE_ALPHABET.length)] }.joinToString("")
            if (store.gameByCode(code) == null) return code
        }
        error("Couldn't find a free game code")
    }

    suspend fun join(player: PlayerRecord, code: String, protocol: Int = 1): GameRecord {
        val g0 = store.gameByCode(code.trim().uppercase()) ?: notFound("No game with that code")
        g0.requireProtocol(protocol)
        return withGame(g0.id) { g ->
            if (g.seatOf(player.id) >= 0) return@withGame g
            if (g.status != GameStatus.LOBBY) conflict("That game has already started")
            val free = g.seats.indexOfFirst { it.isFree }.takeIf { it >= 0 } ?: conflict("That game is full")
            save(g.copy(seats = g.seats.toMutableList().also { it[free] = SeatRecord(player.id, player.name) }))
        }
    }

    suspend fun start(player: PlayerRecord, id: String, difficulty: Difficulty): GameRecord = withGame(id) { g ->
        if (g.seatOf(player.id) != g.hostSeat) forbidden("Only the host can start the game")
        if (g.status != GameStatus.LOBBY) conflict("Already started")
        val taken = g.seats.map { it.name }.toMutableSet()
        val seats = g.seats.map { s ->
            if (!s.isFree) s else {
                val name = AI_NAMES.first { it !in taken }.also { taken += it }
                SeatRecord(null, name, difficulty)
            }
        }
        save(runComputers(g.copy(seats = seats, status = GameStatus.PLAYING, match = Match.start(g.rules, random))))
    }

    // --- Play --------------------------------------------------------------------------------

    suspend fun act(player: PlayerRecord, id: String, action: Action, version: Int): GameRecord? = withGame(id) { g ->
        val seat = g.seatOf(player.id).takeIf { it >= 0 } ?: forbidden("You're not at this table")
        if (action is Action.Leave) return@withGame leave(g, seat)
        if (version != g.version) conflict("The game has moved on; refresh and try again")
        if (g.status != GameStatus.PLAYING) conflict("The game isn't running")
        val m = g.match ?: conflict("The game isn't running")
        if (m.round.turn != seat) forbidden("It's not your turn")
        val r = m.round
        val next = when (action) {
            is Action.Play -> if (action.move in r.legalMoves()) m.play(action.move) else badRequest("That move isn't allowed")
            Action.Draw -> if (r.canDraw) m.draw() else badRequest("You can't draw now")
            Action.Pass -> if (r.mustPass) m.pass() else badRequest("You can't pass while you have a move")
            Action.Leave -> error("handled above")
        }
        save(runComputers(g.copy(match = next)))
    }

    private fun leave(g: GameRecord, seat: Int): GameRecord? = when (g.status) {
        GameStatus.LOBBY ->
            if (seat == g.hostSeat) {
                store.deleteGame(g.id)
                null
            } else {
                save(g.copy(seats = g.seats.toMutableList().also { it[seat] = SeatRecord() }))
            }
        GameStatus.PLAYING -> {
            val s = g.seats[seat]
            val ai = SeatRecord(null, "${s.name} (AI)", Difficulty.NORMAL)
            save(runComputers(g.copy(seats = g.seats.toMutableList().also { it[seat] = ai })))
        }
        GameStatus.FINISHED -> {
            // Take it off your list; the record stays for the others.
            save(g.copy(seats = g.seats.toMutableList().also { it[seat] = it[seat].copy(playerId = null) }))
        }
    }

    /**
     * Plays for computer seats until a person is to move, dealing new hands as they end,
     * and marks the game finished when someone reaches the target.
     */
    private fun runComputers(g0: GameRecord): GameRecord {
        var g = g0
        repeat(10_000) {
            val m = g.match ?: return g
            if (m.isOver) return g.copy(status = GameStatus.FINISHED, lastHand = summaryIfOver(m) ?: g.lastHand)
            if (m.round.isOver) {
                g = g.copy(lastHand = summaryIfOver(m), match = m.nextHand(random))
                return@repeat
            }
            val turn = m.round.turn
            val ai = g.seats[turn].ai ?: return g
            val r = m.round
            g = g.copy(
                match = when {
                    r.canDraw -> m.draw()
                    r.mustPass -> m.pass()
                    else -> m.play(Strategy.chooseMove(r, ai, random)!!)
                },
            )
        }
        error("Computer players never finished")
    }

    private fun summaryIfOver(m: Match): HandSummary? {
        val res = m.round.result ?: return null
        return HandSummary(m.handNumber, res, m.round.hands, m.round.board, m.scores)
    }

    private fun save(g: GameRecord): GameRecord {
        val saved = g.copy(version = g.version + 1, updatedAt = clock())
        store.saveGame(saved)
        return saved
    }

    // --- Views -------------------------------------------------------------------------------

    fun game(player: PlayerRecord, id: String, protocol: Int = 1): GameRecord {
        val g = store.game(id) ?: notFound("No such game")
        if (g.seatOf(player.id) < 0) forbidden("You're not at this table")
        g.requireProtocol(protocol)
        return g
    }

    /** Your games, leaving out any an app speaking [protocol] couldn't show. */
    fun gamesOf(player: PlayerRecord, protocol: Int = 1): List<GameSummary> = store.gamesOf(player.id)
        .filter { it.rules.mode.sinceProtocol <= protocol }
        .map { g ->
        GameSummary(
            id = g.id,
            code = g.code,
            status = g.status,
            pace = g.pace,
            mode = g.rules.mode,
            seats = g.seats.map { it.name.ifEmpty { "Open seat" } },
            scores = g.match?.scores ?: List(g.seats.size) { 0 },
            yourSeat = g.seatOf(player.id),
            turn = g.match?.round?.turn ?: -1,
            winner = g.match?.winner,
            version = g.version,
            updatedAt = g.updatedAt,
        )
    }.sortedWith(compareByDescending<GameSummary> { it.isYourTurn }.thenByDescending { it.updatedAt })

    /** The game as [playerId] may see it, in a form an app speaking [protocol] understands. */
    fun view(g: GameRecord, playerId: String, online: Set<String> = emptySet(), protocol: Int = 1): GameView {
        val you = g.seatOf(playerId)
        val m = g.match
        val r = m?.round
        val scores = m?.scores
        return GameView(
            id = g.id,
            code = g.code,
            version = g.version,
            status = g.status,
            pace = g.pace,
            rules = g.rules,
            hostSeat = g.hostSeat,
            yourSeat = you,
            seats = g.seats.mapIndexed { i, s ->
                SeatView(
                    name = s.name,
                    playerId = s.playerId,
                    isAi = s.ai != null,
                    tiles = r?.hands?.get(i)?.size ?: 0,
                    score = scores?.get(i) ?: 0,
                    online = s.playerId != null && s.playerId in online,
                )
            },
            handNumber = m?.handNumber ?: 0,
            board = r?.board ?: de.wtzg.domino.engine.Board(spinnerAllowed = g.rules.mode.spinner),
            boneyard = r?.boneyard?.size ?: 0,
            turn = r?.turn ?: -1,
            hand = if (r != null && you >= 0) r.hands[you] else emptyList(),
            opening = r?.opening?.takeIf { you >= 0 && r.turn == you },
            lacks = r?.lacks ?: List(g.seats.size) { emptySet() },
            events = r?.events?.map { it.toPublic() }?.filter { it.sinceProtocol <= protocol } ?: emptyList(),
            lastHand = g.lastHand,
            winner = m?.winner,
            updatedAt = g.updatedAt,
            visibility = g.visibility,
        )
    }

    private fun Event.toPublic(): PublicEvent = when (this) {
        is Event.Played -> PublicEvent.Played(player, move, points)
        is Event.Drew -> PublicEvent.Drew(player, ends)
        is Event.Passed -> PublicEvent.Passed(player, ends)
    }
}
