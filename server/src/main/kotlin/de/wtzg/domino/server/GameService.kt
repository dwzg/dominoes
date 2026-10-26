package de.wtzg.domino.server

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.Event
import de.wtzg.domino.engine.Match
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.engine.Strategy
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.AddFriendRequest
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.Friend
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameInvite
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.HandSummary
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.PublicEvent
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.SeatView
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.TurnLimits
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
/** People you played with who are offered as friends. */
private const val RECENT_SHOWN = 10
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

    private fun checkTable(players: Int, target: Int, turnLimitSeconds: Int) {
        if (players !in 2..4) badRequest("2 to 4 players")
        if (target !in Limits.TARGET_MIN..Limits.TARGET_MAX) {
            badRequest("Target must be between ${Limits.TARGET_MIN} and ${Limits.TARGET_MAX}")
        }
        if (!TurnLimits.isValid(turnLimitSeconds)) badRequest("Turn limits are 15 seconds to 30 days, or none")
    }

    /** Keeps one player from filling the open games list. */
    private fun checkOpenGamesOf(host: PlayerRecord) {
        val listed = store.gamesOf(host.id).count { it.isOpen && it.seatOf(host.id) == it.hostSeat }
        if (listed >= Limits.OPEN_GAMES_PER_HOST) {
            conflict("You already have ${Limits.OPEN_GAMES_PER_HOST} open games listed. Start or close one first.")
        }
    }

    fun create(host: PlayerRecord, req: CreateGameRequest): GameRecord {
        checkTable(req.players, req.target, req.turnLimitSeconds)
        if (req.visibility == Visibility.PUBLIC) checkOpenGamesOf(host)
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
            turnLimitSeconds = req.turnLimitSeconds,
        )
        store.saveGame(g)
        return g
    }

    /**
     * The host changes the game before it starts. Everyone already seated keeps a seat:
     * the table can't shrink below them, and when it shrinks they move up to fill the gaps.
     */
    suspend fun updateSettings(player: PlayerRecord, id: String, req: SettingsRequest, protocol: Int = 1): GameRecord = withGame(id) { g ->
        if (g.seatOf(player.id) != g.hostSeat) forbidden("Only the host can change the game")
        if (g.status != GameStatus.LOBBY) conflict("The game has already started")
        checkTable(req.players, req.target, req.turnLimitSeconds)
        if (req.mode.sinceProtocol > protocol) updateApp("This game needs a newer version of the app. Please update it.")
        val people = g.seats.filter { !it.isFree }
        if (req.players < people.size) conflict("${people.size} people are already at the table")
        // The host stays in seat 0; everyone else keeps their order, then the free seats.
        val seats = if (req.players >= g.seats.size) {
            g.seats + List(req.players - g.seats.size) { SeatRecord() }
        } else {
            people + List(req.players - people.size) { SeatRecord() }
        }
        save(g.copy(rules = Rules(req.mode, req.players, req.target), pace = req.pace, seats = seats, turnLimitSeconds = req.turnLimitSeconds))
    }

    /** The host lists the lobby in the open games, or takes it off. Only before the game starts. */
    suspend fun setVisibility(player: PlayerRecord, id: String, visibility: Visibility): GameRecord = withGame(id) { g ->
        if (g.seatOf(player.id) != g.hostSeat) forbidden("Only the host can change who can join")
        if (g.status != GameStatus.LOBBY) conflict("The game has already started")
        if (g.visibility == visibility) return@withGame g
        if (visibility == Visibility.PUBLIC) checkOpenGamesOf(player)
        save(g.copy(visibility = visibility))
    }

    // --- Rematch -----------------------------------------------------------------------------

    /**
     * A new lobby with the same settings after a finished game. The first player to ask sets
     * it up and hosts it; everyone after joins that same lobby.
     */
    suspend fun rematch(player: PlayerRecord, id: String, protocol: Int = 1): GameRecord {
        val (lobbyId, code) = withGame(id) { g ->
            if (g.seatOf(player.id) < 0) forbidden("You're not at this table")
            if (g.status != GameStatus.FINISHED) conflict("The game isn't over yet")
            val existing = g.rematchId?.let(store::game)
            when {
                existing != null && existing.status == GameStatus.LOBBY -> existing.id to existing.code
                existing != null -> conflict("The rematch has already started")
                else -> {
                    // None yet, or its host cancelled it: set up a new one.
                    val now = clock()
                    val next = GameRecord(
                        id = UUID.randomUUID().toString(),
                        code = newCode(),
                        pace = g.pace,
                        rules = g.rules,
                        seats = List(g.rules.players) { i -> if (i == 0) SeatRecord(player.id, player.name) else SeatRecord() },
                        status = GameStatus.LOBBY,
                        createdAt = now,
                        updatedAt = now,
                        turnLimitSeconds = g.turnLimitSeconds,
                    )
                    store.saveGame(next)
                    save(g.copy(rematchId = next.id))
                    next.id to null
                }
            }
        }
        return if (code == null) store.game(lobbyId) ?: notFound("No such game") else join(player, code, protocol)
    }

    // --- Turn limits -------------------------------------------------------------------------

    /**
     * Ends every game whose player to move has run out of time, and returns their ids so open
     * connections can be told. Nobody wins a game that ends this way.
     */
    suspend fun expireOverdueTurns(): List<String> = store.overdueGames(clock()).mapNotNull { id ->
        try {
            withGame(id) { g ->
                val deadline = g.turnDeadline
                // Checked again under the lock: a move may have come in meanwhile.
                if (deadline == null || deadline > clock()) return@withGame null
                save(g.copy(status = GameStatus.FINISHED, timedOut = g.match!!.round.turn))
                id
            }
        } catch (_: ApiException) {
            null // deleted meanwhile
        }
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
                    turnLimitSeconds = g.turnLimitSeconds,
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
            store.removeInvite(g.id, player.id)
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
        store.invitesFor(g.id).forEach { store.removeInvite(g.id, it.toId) }
        save(runComputers(g.copy(seats = seats, status = GameStatus.PLAYING, match = Match.start(g.rules, random))))
    }

    // --- Friends -----------------------------------------------------------------------------

    private val friendLock = Any()

    /** Your friend code, made the first time you ask for it. */
    fun friendCode(player: PlayerRecord): String = synchronized(friendLock) {
        store.friendCode(player.id) ?: newFriendCode().also { store.setFriendCode(player.id, it) }
    }

    private fun newFriendCode(): String {
        repeat(20) {
            val code = (1..Limits.FRIEND_CODE_LENGTH).map { CODE_ALPHABET[secure.nextInt(CODE_ALPHABET.length)] }.joinToString("")
            if (store.playerByFriendCode(code) == null) return code
        }
        error("Couldn't find a free friend code")
    }

    private fun List<String>.asFriends(): List<Friend> = mapNotNull { id -> store.player(id)?.let { Friend(it.id, it.name) } }

    /** Your friends page: friends, requests both ways, people you played with, and game invites. */
    fun friends(player: PlayerRecord, protocol: Int = 1): FriendsView {
        val me = player.id
        val friends = store.friendsOf(me)
        val requests = store.requestsTo(me)
        val sent = store.requestsFrom(me)
        val known = (friends + requests + sent + me).toSet()
        val recent = store.gamesOf(me).sortedByDescending { it.updatedAt }
            .flatMap { g -> g.playerIds }
            .filter { it !in known }
            .distinct()
            .take(RECENT_SHOWN)
        return FriendsView(
            friendCode = friendCode(player),
            friends = friends.asFriends(),
            requests = requests.asFriends(),
            sent = sent.asFriends(),
            invites = invitesTo(player, protocol),
            recent = recent.asFriends(),
        )
    }

    /** Invites to lobbies you could still join. Ones that can't be joined any more are cleared. */
    private fun invitesTo(player: PlayerRecord, protocol: Int): List<GameInvite> = store.invitesTo(player.id).mapNotNull { inv ->
        val g = store.game(inv.gameId)
        if (g == null || g.status != GameStatus.LOBBY || g.seatOf(player.id) >= 0) {
            store.removeInvite(inv.gameId, player.id)
            return@mapNotNull null
        }
        // A full table may still free up, so the invite stays, just hidden.
        if (g.seats.none { it.isFree } || g.rules.mode.sinceProtocol > protocol) return@mapNotNull null
        GameInvite(
            gameId = g.id,
            code = g.code,
            from = store.player(inv.fromId)?.name ?: g.seats[g.hostSeat].name,
            mode = g.rules.mode,
            target = g.rules.target,
            pace = g.pace,
            seats = g.seats.size,
            taken = g.seats.count { it.playerId != null },
            turnLimitSeconds = g.turnLimitSeconds,
            sentAt = inv.sentAt,
        )
    }

    /**
     * Asks someone to be friends, by their friend code or, if you've played together, their
     * player id. If they already asked you, you're friends straight away.
     */
    fun addFriend(player: PlayerRecord, req: AddFriendRequest, protocol: Int = 1): FriendsView {
        val code = req.code
        val id = req.playerId
        val other = when {
            code != null -> store.playerByFriendCode(code.uppercase().filter { it.isLetterOrDigit() })
                ?: notFound("No player has that friend code")
            id != null -> store.player(id)
                ?.takeIf { p -> store.gamesOf(player.id).any { p.id in it.playerIds } }
                ?: notFound("You can only add people you've played with")
            else -> badRequest("Say who to add")
        }
        if (other.id == player.id) badRequest("That's your own friend code")
        synchronized(friendLock) {
            val friends = store.friendsOf(player.id)
            when {
                other.id in friends -> Unit
                player.id in store.requestsFrom(other.id) -> {
                    checkFriendsRoom(player.id, other.id)
                    store.removeRequest(other.id, player.id)
                    store.addFriendship(player.id, other.id, clock())
                }
                else -> {
                    if (friends.size >= Limits.FRIENDS_MAX) conflict("You have ${Limits.FRIENDS_MAX} friends already")
                    store.addRequest(player.id, other.id, clock())
                }
            }
        }
        return friends(player, protocol)
    }

    private fun checkFriendsRoom(a: String, b: String) {
        if (store.friendsOf(a).size >= Limits.FRIENDS_MAX) conflict("You have ${Limits.FRIENDS_MAX} friends already")
        if (store.friendsOf(b).size >= Limits.FRIENDS_MAX) conflict("They have too many friends already")
    }

    fun acceptFriend(player: PlayerRecord, otherId: String, protocol: Int = 1): FriendsView {
        synchronized(friendLock) {
            if (otherId !in store.requestsTo(player.id)) notFound("No friend request from them")
            checkFriendsRoom(player.id, otherId)
            store.removeRequest(otherId, player.id)
            store.addFriendship(player.id, otherId, clock())
        }
        return friends(player, protocol)
    }

    /** Unfriends, declines their request, or takes back yours: whichever there is. */
    fun removeFriend(player: PlayerRecord, otherId: String, protocol: Int = 1): FriendsView {
        synchronized(friendLock) {
            store.removeFriendship(player.id, otherId)
            store.removeRequest(otherId, player.id)
            store.removeRequest(player.id, otherId)
        }
        return friends(player, protocol)
    }

    /** Invites friends to a lobby you're in. People already at the table are skipped. */
    suspend fun invite(player: PlayerRecord, id: String, playerIds: List<String>): GameRecord = withGame(id) { g ->
        if (g.seatOf(player.id) < 0) forbidden("You're not at this table")
        if (g.status != GameStatus.LOBBY) conflict("The game has already started")
        if (g.seats.none { it.isFree }) conflict("The table is full")
        val ids = playerIds.distinct()
        if (ids.isEmpty() || ids.size > g.seats.size) badRequest("Invite 1 to ${g.seats.size} friends")
        val friends = store.friendsOf(player.id).toSet()
        if (ids.any { it !in friends }) forbidden("You can only invite friends")
        ids.filter { g.seatOf(it) < 0 }.forEach { store.addInvite(InviteRecord(g.id, it, player.id, clock())) }
        g
    }

    /** Says no to an invite. Returns the game it was for, if it still exists. */
    fun declineInvite(player: PlayerRecord, gameId: String): GameRecord? {
        store.removeInvite(gameId, player.id)
        return store.game(gameId)
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
        GameStatus.PLAYING ->
            // The game ends for everyone, nobody winning. The leaver's name stays at the table,
            // but the game drops off their list.
            save(
                g.copy(
                    status = GameStatus.FINISHED,
                    leftSeat = seat,
                    seats = g.seats.toMutableList().also { it[seat] = it[seat].copy(playerId = null) },
                ),
            )
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
            timedOut = g.timedOut,
            leftSeat = g.leftSeat,
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
            turnLimitSeconds = g.turnLimitSeconds,
            turnEndsInMs = g.turnDeadline?.let { (it - clock()).coerceAtLeast(0) },
            timedOut = g.timedOut,
            leftSeat = g.leftSeat,
            rematchId = g.rematchId,
            invited = if (g.status != GameStatus.LOBBY) emptyList() else {
                store.invitesFor(g.id).map { it.toId }.filter { g.seatOf(it) < 0 }.asFriends()
            },
        )
    }

    private fun Event.toPublic(): PublicEvent = when (this) {
        is Event.Played -> PublicEvent.Played(player, move, points)
        is Event.Drew -> PublicEvent.Drew(player, ends)
        is Event.Passed -> PublicEvent.Passed(player, ends)
    }
}
