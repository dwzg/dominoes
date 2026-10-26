package de.wtzg.domino.server

import de.wtzg.domino.protocol.AddFriendRequest
import de.wtzg.domino.protocol.Friend
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameInvite
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.protocol.sinceProtocol
import java.security.SecureRandom

/** People you played with who are offered as friends. */
private const val RECENT_SHOWN = 10

/**
 * Friend codes, friend requests, and the invites friends send each other. Changes here are
 * made one at a time; inviting to a game goes through [GameService.invite], under that game's lock.
 */
class FriendService(
    private val store: Store,
    private val clock: () -> Long,
    private val secure: SecureRandom = SecureRandom(),
) {
    private val lock = Any()

    /** Your friend code, made the first time you ask for it. */
    fun code(player: PlayerRecord): String = synchronized(lock) {
        store.friendCode(player.id)
            ?: secure.freeCode(Limits.FRIEND_CODE_LENGTH) { store.playerByFriendCode(it) != null }
                .also { store.setFriendCode(player.id, it) }
    }

    private fun List<String>.asFriends(): List<Friend> = mapNotNull { id -> store.player(id)?.let { Friend(it.id, it.name) } }

    /** Your friends page: friends, requests both ways, people you played with, and game invites. */
    fun page(player: PlayerRecord, protocol: Int): FriendsView {
        val me = player.id
        val friends = store.friendsOf(me)
        val requests = store.requestsTo(me)
        val sent = store.requestsFrom(me)
        val known = (friends + requests + sent + me).toSet()
        val recent = store.gamesOf(me)
            .flatMap { g -> g.playerIds }
            .filter { it !in known }
            .distinct()
            .take(RECENT_SHOWN)
        return FriendsView(
            friendCode = code(player),
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
    fun add(player: PlayerRecord, req: AddFriendRequest, protocol: Int): FriendsView {
        val code = req.code
        val id = req.playerId
        val other = when {
            code != null -> store.playerByFriendCode(code.uppercase().filter { it.isLetterOrDigit() })
                ?: notFound("No player has that friend code")
            id != null -> store.player(id)?.takeIf { store.haveShared(player.id, it.id) }
                ?: notFound("You can only add people you've played with")
            else -> badRequest("Say who to add")
        }
        if (other.id == player.id) badRequest("That's your own friend code")
        synchronized(lock) {
            when {
                other.id in store.friendsOf(player.id) -> Unit
                player.id in store.requestsFrom(other.id) -> befriend(other.id, player.id)
                else -> {
                    checkRoom(player.id, "You have")
                    // Keeps one player from flooding strangers with requests.
                    if (store.requestsFrom(player.id).size >= Limits.FRIEND_REQUESTS_MAX) {
                        conflict("You have ${Limits.FRIEND_REQUESTS_MAX} requests waiting. Cancel some first.")
                    }
                    if (store.requestsTo(other.id).size >= Limits.FRIEND_REQUESTS_MAX) conflict("They have too many requests waiting")
                    store.addRequest(player.id, other.id, clock())
                }
            }
        }
        return page(player, protocol)
    }

    fun accept(player: PlayerRecord, otherId: String, protocol: Int): FriendsView {
        synchronized(lock) {
            if (otherId !in store.requestsTo(player.id)) notFound("No friend request from them")
            befriend(otherId, player.id)
        }
        return page(player, protocol)
    }

    /** Answers [fromId]'s request to [toId]. Call under [lock]. */
    private fun befriend(fromId: String, toId: String) {
        checkRoom(toId, "You have")
        checkRoom(fromId, "They have")
        store.acceptRequest(fromId, toId, clock())
    }

    private fun checkRoom(playerId: String, who: String) {
        if (store.friendsOf(playerId).size >= Limits.FRIENDS_MAX) conflict("$who ${Limits.FRIENDS_MAX} friends already")
    }

    /** Unfriends, declines their request, or takes back yours: whichever there is. */
    fun remove(player: PlayerRecord, otherId: String, protocol: Int): FriendsView {
        synchronized(lock) { store.unfriend(player.id, otherId) }
        return page(player, protocol)
    }

    /** Says no to an invite. Returns the game it was for, if it still exists. */
    fun decline(player: PlayerRecord, gameId: String): GameRecord? {
        store.removeInvite(gameId, player.id)
        return store.game(gameId)
    }
}
